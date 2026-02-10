package com.example.itsystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Legacy route kept for backward compatibility.
 *
 * The authoritative student company submission flow is handled by StudentController under
 * /student/company-info. This controller redirects to avoid creating duplicate/incomplete
 * CompanyInfo records (which can lead to data mismatch).
 */
@Controller
@RequestMapping("/student/company")
public class CompanyInfoController {

    @GetMapping
    public String showForm() {
        return "redirect:/student/company-info";
    }

    @PostMapping
    public String submitForm() {
        return "redirect:/student/company-info";
    }
}
