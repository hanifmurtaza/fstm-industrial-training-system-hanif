package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import com.example.itsystem.model.Company;
import com.example.itsystem.model.CompanySector;
import com.example.itsystem.model.PlacementStatus;
import com.example.itsystem.repository.CompanyRepository;
import com.example.itsystem.repository.CompanyStudentCountRow;
import com.example.itsystem.repository.DocumentRepository;
import com.example.itsystem.model.Document;
import org.springframework.ui.Model;

import java.util.*;
import org.springframework.data.domain.Sort;



import java.util.Map;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          CompanyRepository companyRepository,
                          DocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.companyRepository = companyRepository;
        this.documentRepository = documentRepository;
    }

    // Root -> login
    @GetMapping("/")
    public String root() { return "redirect:/login"; }

    // ---- Login view ----
    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {

        Object auth = session.getAttribute("auth");
        if (auth instanceof Map<?, ?> m) {
            String role = String.valueOf(m.get("role")).toLowerCase();
            return switch (role) {
                case "student"  -> "redirect:/student-dashboard";
                case "teacher"  -> "redirect:/lecturer/home";
                case "admin"    -> "redirect:/admin/dashboard";
                case "industry" -> "redirect:/industry/dashboard";
                default         -> "login";
            };
        }

        // ==============================
        // ✅ PUBLIC LOGIN PAGE STATISTICS
        // ==============================

        // 1) Chart: number of companies per sector (enum)
        List<Company> companies = companyRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));

        Map<CompanySector, Long> sectorCounts = new EnumMap<>(CompanySector.class);
        for (CompanySector s : CompanySector.values()) sectorCounts.put(s, 0L);

        for (Company c : companies) {
            CompanySector s = null;
            try {
                if (c.getSector() != null && !c.getSector().isBlank()) {
                    s = CompanySector.valueOf(c.getSector().trim());
                }
            } catch (Exception ignore) { /* legacy free text */ }

            if (s == null) s = CompanySector.OTHERS;
            sectorCounts.put(s, sectorCounts.get(s) + 1);
        }

        // Keep only non-zero sectors to avoid clutter
        List<String> sectorLabels = new ArrayList<>();
        List<Long> sectorData = new ArrayList<>();
        for (var e : sectorCounts.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                sectorLabels.add(e.getKey().name().replace("_", " "));
                sectorData.add(e.getValue());
            }
        }

        model.addAttribute("sectorLabels", sectorLabels);
        model.addAttribute("sectorData", sectorData);

        // 2) Table: all companies + total students with APPROVED placement in that company
        List<CompanyStudentCountRow> companyCounts =
                companyRepository.findCompanyStudentCounts(PlacementStatus.APPROVED);

        model.addAttribute("companyCounts", companyCounts);

        // ==============================
        // ✅ PUBLIC ANNOUNCEMENTS (shown on login page)
        // ==============================
        model.addAttribute("publicAnnouncements",
                documentRepository.findByAnnouncementTrueAndAudienceOrderByUploadedAtDesc(Document.Audience.ALL));

        return "login";
    }

    // ---- Password hashing utility (for admin use) ----
    private static boolean isBcrypt(String s) {
        return s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$");
    }
}
