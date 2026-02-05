package com.example.itsystem.controller;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.LogbookEntryRepository;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.service.LogbookEntryService;
import com.example.itsystem.service.UserService;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;

@Controller
@RequestMapping("/lecturer/logbook")
public class LecturerLogbookController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LogbookEntryRepository logbookEntryRepository;

    @Autowired
    private LogbookEntryService logbookEntryService;

    @Autowired
    private UserService userService;

    @GetMapping({"/list", "/logbook-list"})
    public String viewLogbookEntries(@RequestParam(name = "session", required = false) String sessionFilter,
                                     HttpSession session,
                                     Model model) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        model.addAttribute("lecturer", lecturer.getName());
        model.addAttribute("activePage", "logbook");

        List<String> sessions = userRepository.findDistinctSessionsByLecturer(lecturer);
        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);

        List<User> students = (sessionFilter != null && !sessionFilter.isBlank())
                ? userRepository.findByLecturerAndSession(lecturer, sessionFilter)
                : userRepository.findByLecturer(lecturer);

        Map<LogbookEntry, User> logbookWithNames = new LinkedHashMap<>();
        for (User stu : students) {
            List<LogbookEntry> entries = logbookEntryRepository.findByStudentId(stu.getId());
            for (LogbookEntry e : entries) {
                logbookWithNames.put(e, stu);
            }
        }

        model.addAttribute("logbookWithNames", logbookWithNames);
        return "lecturer/logbook-list";
    }

    @PostMapping("/endorse")
    public String toggleEndorsement(@RequestParam("entryId") Long entryId,
                                    @RequestParam(name = "session", required = false) String sessionFilter,
                                    HttpServletRequest request,
                                    HttpSession httpSession,
                                    RedirectAttributes ra) {

        User lecturer = (User) httpSession.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        logbookEntryRepository.findById(entryId).ifPresent(e -> {
            // ✅ Toggle lecturer endorsement (this is the field your UI must read)
            boolean newValue = !e.isEndorsedByLecturer();
            e.setEndorsedByLecturer(newValue);

            if (newValue) {
                String who = (lecturer.getName() != null && !lecturer.getName().isBlank())
                        ? lecturer.getName()
                        : lecturer.getUsername();
                e.setLecturerReviewedBy(who);
                e.setLecturerReviewedAt(java.time.LocalDateTime.now());
            } else {
                e.setLecturerReviewedBy(null);
                e.setLecturerReviewedAt(null);
            }

            logbookEntryRepository.save(e);
        });

        // back to where user came from
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            // basic safety check
            String base = request.getScheme() + "://" + request.getServerName();
            if (referer.startsWith(base)) {
                return "redirect:" + referer;
            }
        }

        if (sessionFilter != null && !sessionFilter.isBlank()) {
            return "redirect:/lecturer/logbook/list?session=" + sessionFilter;
        }
        return "redirect:/lecturer/logbook/list";
    }

    @GetMapping("/export/{id}")
    public void exportLogbookAsPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        LogbookEntry entry = logbookEntryService.getById(id);
        User student = userService.getById(entry.getStudentId());

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=logbook_" + student.getName() + "_" + entry.getWeekStartDate() + ".pdf");

        PdfWriter writer = new PdfWriter(response.getOutputStream());
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        document.add(new Paragraph("Logbook Entry").setFont(bold).setFontSize(16).setBold().setMarginBottom(15));
        document.add(new Paragraph().add(new Text("Student: ").setFont(bold)).add(new Text(student.getName()).setFont(normal)));
        document.add(new Paragraph().add(new Text("Matric ID: ").setFont(bold)).add(new Text(student.getStudentId()).setFont(normal)));
        document.add(new Paragraph().add(new Text("Week: ").setFont(bold)).add(new Text(entry.getWeekStartDate().toString()).setFont(normal)));
        document.add(new Paragraph("\n"));

        document.add(new Paragraph().add(new Text("Main Task: ").setFont(bold)).add(new Text(nz(entry.getMainTask())).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Skills: ").setFont(bold)).add(new Text(nz(entry.getSkills())).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Challenges: ").setFont(bold)).add(new Text(nz(entry.getChallenges())).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Result: ").setFont(bold)).add(new Text(nz(entry.getResult())).setFont(normal)).setMarginBottom(8));

        // ✅ FIX: use endorsedByLecturer for lecturer endorsement
        document.add(new Paragraph().add(new Text("Endorsed (Lecturer): ").setFont(bold))
                .add(new Text(entry.isEndorsedByLecturer() ? "Yes" : "No").setFont(normal)));

        document.close();
    }

    @GetMapping(value = "/text/{id}", produces = "text/plain; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> getLogbookText(@PathVariable Long id, HttpSession session) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Optional<LogbookEntry> opt = logbookEntryRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body("Not found");
        }

        LogbookEntry e = opt.get();

        Long studentId = e.getStudentId();
        if (studentId == null) {
            return ResponseEntity.status(409).body("Invalid data: studentId is null.");
        }

        User student = userService.getById(studentId);
        if (student == null) {
            return ResponseEntity.status(404).body("Student not found");
        }

        if (student.getLecturer() == null || !Objects.equals(student.getLecturer().getId(), lecturer.getId())) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        StringBuilder sb = new StringBuilder();
        appendIfHas(sb, "Main Task", nz(e.getMainTask()));
        appendIfHas(sb, "Skills", nz(e.getSkills()));
        appendIfHas(sb, "Challenges", nz(e.getChallenges()));
        appendIfHas(sb, "Result", nz(e.getResult()));

        String out = sb.toString().trim();
        return ResponseEntity.ok(out.isEmpty() ? "No content." : out);
    }

    private static String nz(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static void appendIfHas(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n\n");
        }
    }
}