package com.example.itsystem.controller;

import com.example.itsystem.model.FinalReport;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.FinalReportRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

//@Controller
//@RequestMapping("/student/final-report")
public class FinalReportController {

    @Autowired
    private FinalReportRepository finalReportRepository;

    @GetMapping
    public String showUploadForm(Model model) {
        model.addAttribute("report", new FinalReport());
        return "student/final-report-form";
    }

    @PostMapping("/submit")
    public String submitFinalReport(@RequestParam("reportFile") MultipartFile reportFile,
                                    @RequestParam("videoLink") String videoLink,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {

        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        // ✅ basic validation
        if (videoLink == null || videoLink.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Video link is required.");
            return "redirect:/student/final-report";
        }

        FinalReport report = new FinalReport();
        report.setStudentId(student.getId());

        // 🧪 模拟保存，仅存文件名 + 视频链接
        report.setReportFilename(reportFile.getOriginalFilename());
        report.setVideoLink(videoLink.trim());

        finalReportRepository.save(report);

        redirectAttributes.addFlashAttribute("successMessage", "Final Report submitted successfully!");
        return "redirect:/student/final-report?success";
    }
}