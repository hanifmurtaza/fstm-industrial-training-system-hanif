package com.example.itsystem.controller;

import com.example.itsystem.model.OpportunityPromo;
import com.example.itsystem.model.PromoStatus;
import com.example.itsystem.repository.OpportunityPromoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicOpportunitiesController {

  private final OpportunityPromoRepository promoRepo;

  public PublicOpportunitiesController(OpportunityPromoRepository promoRepo) {
    this.promoRepo = promoRepo;
  }

  // Public SSR page: /opportunities
  @GetMapping("/opportunities")
  public String list(@RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "12") int size,
                     Model model) {
    Page<OpportunityPromo> items = promoRepo.findByStatus(
            PromoStatus.PUBLISHED,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
    );
    model.addAttribute("items", items);
    return "public-opportunities";
  }
}
