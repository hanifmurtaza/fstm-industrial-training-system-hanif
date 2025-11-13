package com.example.itsystem.controller;

import com.example.itsystem.model.OpportunityPromo;
import com.example.itsystem.model.PromoStatus;
import com.example.itsystem.repository.OpportunityPromoRepository;
import com.example.itsystem.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/industry/opportunities")
public class OpportunityPromoController {

  private final OpportunityPromoRepository promoRepo;
  private final FileStorageService storage;

  public OpportunityPromoController(OpportunityPromoRepository promoRepo,
                                    FileStorageService storage) {
    this.promoRepo = promoRepo;
    this.storage = storage;
  }

  // ---- helpers ----
  private boolean notIndustry(HttpSession session) {
    Object auth = session.getAttribute("auth");
    if (auth instanceof Map<?,?> m) {
      Object role = m.get("role");
      return role == null || !"industry".equalsIgnoreCase(String.valueOf(role));
    }
    return true;
  }
  private Long currentUserId(HttpSession session) {
    Object auth = session.getAttribute("auth");
    if (auth instanceof Map<?,?> m && m.get("id") != null) {
      try { return Long.valueOf(String.valueOf(m.get("id"))); } catch (Exception ignored) {}
    }
    return null;
  }
  private List<String> sectorOptions() {
    return List.of("Bakery","Food Manufacturing","Frozen Food","Catering","Others");
  }

  // ---- List (industry-owned) ----
  @GetMapping
  public String list(@RequestParam(required = false) PromoStatus status,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "10") int size,
                     HttpSession session, Model model) {
    if (notIndustry(session)) return "redirect:/login";
    Long me = currentUserId(session); if (me == null) return "redirect:/login";

    Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt", "id"));
    Page<OpportunityPromo> items = (status == null)
            ? promoRepo.findBySupervisorUserId(me, p)
            : promoRepo.findBySupervisorUserIdAndStatus(me, status, p);

    model.addAttribute("items", items);
    model.addAttribute("status", status);
    return "industry-promo-list";
  }

  // ---- New ----
  @GetMapping("/new")
  public String createForm(HttpSession session, Model model) {
    if (notIndustry(session)) return "redirect:/login";
    OpportunityPromo promo = new OpportunityPromo();
    promo.setSupervisorUserId(currentUserId(session));
    model.addAttribute("promo", promo);
    model.addAttribute("sectorOptions", sectorOptions());
    return "industry-promo-form";
  }

  // ---- Edit ----
  @GetMapping("/{id}")
  public String edit(@PathVariable Long id, HttpSession session, Model model) {
    if (notIndustry(session)) return "redirect:/login";
    Long me = currentUserId(session); if (me == null) return "redirect:/login";

    OpportunityPromo promo = promoRepo.findById(id).orElse(null);
    if (promo == null || !me.equals(promo.getSupervisorUserId()))
      return "redirect:/industry/opportunities";

    model.addAttribute("promo", promo);
    model.addAttribute("sectorOptions", sectorOptions());
    return "industry-promo-form";
  }

  // ---- Save (create or update) ----
  // NOTE: BindingResult MUST come immediately after the @Valid model
  @PostMapping
  public String save(@ModelAttribute("promo") @Valid OpportunityPromo promo,
                     org.springframework.validation.BindingResult result,
                     @RequestParam(name="image", required=false) MultipartFile image,
                     HttpSession session,
                     Model model,
                     RedirectAttributes ra) {
    if (notIndustry(session)) return "redirect:/login";
    Long me = currentUserId(session); if (me == null) return "redirect:/login";

    // if validation fails, redisplay the form with errors
    if (result.hasErrors()) {
      model.addAttribute("sectorOptions", sectorOptions());
      return "industry-promo-form";
    }

    // preserve existing image if editing and no new file uploaded
    if ((image == null || image.isEmpty()) && promo.getId() != null) {
      promoRepo.findById(promo.getId()).ifPresent(existing -> {
        if (promo.getImageUrl() == null || promo.getImageUrl().isBlank()) {
          promo.setImageUrl(existing.getImageUrl());
        }
      });
    }

    // handle new image upload
    if (image != null && !image.isEmpty()) {
      String url = storage.storeImage(image);
      promo.setImageUrl(url);
    }

    promo.setSupervisorUserId(me);
    if (promo.getStatus() == null) promo.setStatus(PromoStatus.DRAFT);

    promo.setExternalLink(normalizeUrl(promo.getExternalLink()));

    try {
      promoRepo.save(promo);
      ra.addFlashAttribute("successMessage", "Opportunity saved.");
      return "redirect:/industry/opportunities";
    } catch (DataIntegrityViolationException ex) {
      // e.g., DB length constraints; surface a friendly message
      model.addAttribute("sectorOptions", sectorOptions());
      model.addAttribute("formError", "Please check required fields and length limits.");
      return "industry-promo-form";
    }
  }

  // ---- Publish / Archive ----
  @PostMapping("/{id}/publish")
  public String publish(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
    if (notIndustry(session)) return "redirect:/login";
    Long me = currentUserId(session); if (me == null) return "redirect:/login";

    promoRepo.findById(id).ifPresent(p -> {
      if (me.equals(p.getSupervisorUserId())) {
        p.setStatus(PromoStatus.PUBLISHED);
        promoRepo.save(p);
      }
    });
    ra.addFlashAttribute("successMessage", "Published.");
    return "redirect:/industry/opportunities";
  }

  private String normalizeUrl(String url) {
    if (url == null) return null;
    String u = url.trim();
    if (u.isEmpty()) return null;
    if (u.startsWith("http://") || u.startsWith("https://")) return u;
    return "https://" + u; // default to https
  }


  @PostMapping("/{id}/archive")
  public String archive(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
    if (notIndustry(session)) return "redirect:/login";
    Long me = currentUserId(session); if (me == null) return "redirect:/login";

    promoRepo.findById(id).ifPresent(p -> {
      if (me.equals(p.getSupervisorUserId())) {
        p.setStatus(PromoStatus.ARCHIVED);
        promoRepo.save(p);
      }
    });
    ra.addFlashAttribute("successMessage", "Archived.");
    return "redirect:/industry/opportunities";
  }
}
