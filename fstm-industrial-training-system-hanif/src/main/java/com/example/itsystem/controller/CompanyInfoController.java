package com.example.itsystem.controller;

import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.service.CompanyInfoService;
import com.example.itsystem.service.UserService;
import com.example.itsystem.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import com.example.itsystem.model.CompanyInfoStatus;


@Controller
@RequestMapping("/student/company")
public class CompanyInfoController {

    @Autowired private CompanyInfoService companyInfoService;
    @Autowired private UserService userService;

    @GetMapping
    public String showForm(Model model, HttpSession session) {
        User student = (User) session.getAttribute("user"); // 从 session 拿 user 对象

        if (student == null) {
            return "redirect:/login";
        }

        CompanyInfo info = companyInfoService.findByStudentId(student.getId())
                .orElse(new CompanyInfo());
        info.setStudentId(student.getId());
        model.addAttribute("info", info);
        return "student/company-form";
    }


    @PostMapping
    public String submitForm(@ModelAttribute("info") CompanyInfo info, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) {
            return "redirect:/login";
        }

        info.setStudentId(student.getId()); // 防止被篡改
        info.setStatus(CompanyInfoStatus.PENDING); // ✅ use enum, not String
        companyInfoService.saveOrUpdate(info);
        return "redirect:/student/company?success";
    }
}
