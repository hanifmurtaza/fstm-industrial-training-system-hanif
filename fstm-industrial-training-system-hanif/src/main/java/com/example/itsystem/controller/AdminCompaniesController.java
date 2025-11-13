package com.example.itsystem.controller;

import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.repository.CompanyInfoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/companies")
public class AdminCompaniesController {

    private final CompanyInfoRepository companyInfoRepository;

    public AdminCompaniesController(CompanyInfoRepository companyInfoRepository) {
        this.companyInfoRepository = companyInfoRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<CompanyInfo> companies = companyInfoRepository.findAll();
        model.addAttribute("companies", companies);
        return "admin-companies";  // matches your existing template name
    }
}
