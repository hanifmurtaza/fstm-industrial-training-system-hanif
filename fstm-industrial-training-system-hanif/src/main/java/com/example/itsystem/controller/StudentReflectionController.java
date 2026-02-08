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
import java.time.LocalDateTime;
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

@Controller
@RequestMapping("/student/reflection")
public class StudentReflectionController {

    @Autowired
    private SelfReflectionRepository repository;

    /**
     * Submit Reflection page:
     * - If not submitted: show empty form + show reminder modal
     * - If already submitted: load existing reflection into form (editable), no reminder
     */
    @GetMapping
    public String showForm(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        Long studentId = student.getId();

        SelfReflection existing = repository.findFirstByStudentIdOrderBySubmittedAtDesc(studentId).orElse(null);
        boolean alreadySubmitted = (existing != null);

        model.addAttribute("alreadySubmitted", alreadySubmitted);

        if (alreadySubmitted) {
            model.addAttribute("reflection", existing); // ✅ allow edit/update
        } else {
            model.addAttribute("reflection", new SelfReflection());
            model.addAttribute("showOneTimeReminder", true); // ✅ show reminder only before first submit
        }

        return "student/reflection-form";
    }

    /**
     * One-time submit rule:
     * - If no record yet: create (submit once)
     * - If already exists: UPDATE the same record (edit), not create a new one
     */
    @PostMapping("/submit")
    public String submitOrUpdateReflection(@ModelAttribute SelfReflection reflection,
                                           HttpSession session,
                                           RedirectAttributes redirectAttributes) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        Long studentId = student.getId();

        SelfReflection existing = repository.findFirstByStudentIdOrderBySubmittedAtDesc(studentId).orElse(null);

        if (existing == null) {
            // ✅ First time submit: create 1 record
            reflection.setStudentId(studentId);
            reflection.setSubmittedAt(LocalDateTime.now());
            repository.save(reflection);

            redirectAttributes.addFlashAttribute("successMessage", "Reflection submitted successfully! You may edit it later if needed.");
            return "redirect:/student/reflection";
        }

        // ✅ Already submitted: update the SAME record (no new insert)
        existing.setLearningExperience(reflection.getLearningExperience());
        existing.setSoftSkills(reflection.getSoftSkills());
        existing.setFeedback(reflection.getFeedback());
        existing.setSubmittedAt(existing.getSubmittedAt()); // keep original submit time (optional)
        repository.save(existing);

        redirectAttributes.addFlashAttribute("successMessage", "Reflection updated successfully!");
        return "redirect:/student/reflection";
    }

    // History page (will show 1 record usually)
    @GetMapping("/history")
    public String viewHistory(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        List<SelfReflection> reflections = repository.findByStudentIdOrderBySubmittedAtDesc(student.getId());
        if (reflections == null) reflections = new ArrayList<>();

        model.addAttribute("reflections", reflections);
        return "student/reflection-history";
    }

    // ❌ Delete is NOT allowed
    @GetMapping("/delete/{id}")
    public String deleteReflection(@PathVariable Long id,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        redirectAttributes.addFlashAttribute("errorMessage",
                "Deletion is not allowed for Self-Reflection.");
        return "redirect:/student/reflection";
    }

    // Edit route is optional now; we keep it for compatibility and redirect to main page
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";
        return "redirect:/student/reflection";
    }

    // Export PDF (unchanged)
    @GetMapping("/export/{id}")
    public void exportReflectionAsPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SelfReflection reflection = repository.findById(id).orElse(null);
        if (reflection == null) return;

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=reflection_" + reflection.getSubmittedAt().toLocalDate() + ".pdf");

        PdfWriter writer = new PdfWriter(response.getOutputStream());
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        document.add(new Paragraph("Self-Reflection Record")
                .setFont(bold).setFontSize(18).setBold().setMarginBottom(10));

        document.add(new Paragraph("Submitted on: " + reflection.getSubmittedAt())
                .setFont(bold).setFontSize(12).setMarginBottom(20));

        document.add(new Paragraph()
                .add(new Text("Key Learning Experiences:\n").setFont(bold))
                .add(new Text(reflection.getLearningExperience()).setFont(normal))
                .setMarginBottom(10));

        document.add(new Paragraph()
                .add(new Text("Soft Skills Developed:\n").setFont(bold))
                .add(new Text(reflection.getSoftSkills()).setFont(normal))
                .setMarginBottom(10));

        document.add(new Paragraph()
                .add(new Text("Feedback or Suggestions:\n").setFont(bold))
                .add(new Text(reflection.getFeedback()).setFont(normal)));

        document.close();
    }
}