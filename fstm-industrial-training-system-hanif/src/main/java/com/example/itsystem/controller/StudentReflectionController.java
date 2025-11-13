package com.example.itsystem.controller;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;


import com.example.itsystem.model.SelfReflection;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.SelfReflectionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student/reflection")
public class StudentReflectionController {

    @Autowired
    private SelfReflectionRepository repository;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("reflection", new SelfReflection());
        return "student/reflection-form";
    }

    @PostMapping("/submit")
    public String submitReflection(@ModelAttribute SelfReflection reflection,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User student = (User) session.getAttribute("user");
        if (student != null) {
            reflection.setStudentId(student.getId());
            repository.save(reflection);
            redirectAttributes.addFlashAttribute("successMessage", "Reflection submitted successfully!");
        }
        return "redirect:/student/reflection"; // 回到原表单页面
    }

    // 查看所有反思（按时间倒序）
    @GetMapping("/history")
    public String viewHistory(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        List<SelfReflection> reflections = repository.findByStudentIdOrderBySubmittedAtDesc(student.getId());
        if (reflections == null) reflections = new ArrayList<>(); // 安全处理

        model.addAttribute("reflections", reflections);
        return "student/reflection-history";
    }


    // 删除反思
    @GetMapping("/delete/{id}")
    public String deleteReflection(@PathVariable Long id, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        SelfReflection r = repository.findById(id).orElse(null);
        if (r != null && r.getStudentId().equals(student.getId())) {
            repository.deleteById(id);
        }

        return "redirect:/student/reflection/history";
    }

    // 编辑反思（显示表单）
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        SelfReflection r = repository.findById(id).orElse(null);
        if (r != null && r.getStudentId().equals(student.getId())) {
            model.addAttribute("reflection", r);
            return "student/reflection-form";
        }

        return "redirect:/student/reflection/history";
    }
    @GetMapping("/export/{id}")
    public void exportReflectionAsPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SelfReflection reflection = repository.findById(id).orElse(null);
        if (reflection == null) return;

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=reflection_" + reflection.getSubmittedAt().toLocalDate() + ".pdf");

        PdfWriter writer = new PdfWriter(response.getOutputStream());
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // 标题
        document.add(new Paragraph("Self-Reflection Record")
                .setFont(bold)
                .setFontSize(18)
                .setBold()
                .setMarginBottom(10));

// 副标题
        document.add(new Paragraph("Submitted on: " + reflection.getSubmittedAt().toString())
                .setFont(bold)
                .setFontSize(12)
                .setMarginBottom(20));

        // 内容段落
        document.add(new Paragraph().add(new Text("Key Learning Experiences:\n").setFont(bold)).add(new Text(reflection.getLearningExperience()).setFont(normal)).setMarginBottom(10));
        document.add(new Paragraph().add(new Text("Soft Skills Developed:\n").setFont(bold)).add(new Text(reflection.getSoftSkills()).setFont(normal)).setMarginBottom(10));
        document.add(new Paragraph().add(new Text("Feedback or Suggestions:\n").setFont(bold)).add(new Text(reflection.getFeedback()).setFont(normal)));

        document.close();
    }


}
