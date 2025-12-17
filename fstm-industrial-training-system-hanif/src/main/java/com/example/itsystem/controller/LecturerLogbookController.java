package com.example.itsystem.controller;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.LogbookEntryRepository;
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

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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

    // 仍在 @RequestMapping("/lecturer/logbook") 控制器里
    @GetMapping({"/list", "/logbook-list"})
    public String viewLogbookEntries(
            @RequestParam(name = "session", required = false) String sessionFilter,
            HttpSession session,
            Model model) {

        // 登录 & 角色校验
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        // 顶部欢迎名 & 左侧导航高亮（如果你在 layout 用得到）
        model.addAttribute("lecturer", lecturer.getName());
        model.addAttribute("activePage", "logbook");

        // 下拉数据：该讲师名下所有 Session（去重）
        List<String> sessions = userRepository.findDistinctSessionsByLecturer(lecturer);
        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);

        // 学生集合（按 session 可选过滤）
        List<User> students = (sessionFilter != null && !sessionFilter.isBlank())
                ? userRepository.findByLecturerAndSession(lecturer, sessionFilter)
                : userRepository.findByLecturer(lecturer);

        // 仍旧用 “Map<LogbookEntry, User>” 喂给你原页面，保证 Preview / Endorse / Export 都不受影响
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
            boolean newValue = !e.isEndorsedByLecturer();
            e.setEndorsedByLecturer(newValue);

            if (newValue) {
                e.setLecturerReviewedBy("teacher"); // or lecturer.getUsername()
                e.setLecturerReviewedAt(java.time.LocalDateTime.now());
            } else {
                e.setLecturerReviewedBy(null);
                e.setLecturerReviewedAt(null);
            }

            logbookEntryRepository.save(e);
        });

        // ① go back to where we came from (keep ?session=... etc.)
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            if (referer.startsWith(request.getScheme() + "://" + request.getServerName())) {
                return "redirect:" + referer;
            }
        }

        // ② fallback: redirect to list with same filter
        if (sessionFilter != null && !sessionFilter.isBlank()) {
            return "redirect:/lecturer/logbook/list?session=" + sessionFilter;
        }
        return "redirect:/lecturer/logbook/list";
    }


    @GetMapping("/export/{id}")
    public void exportLogbookAsPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        LogbookEntry entry = logbookEntryService.getById(id);
        User student = userService.getById(entry.getStudentId());// ✅ 确保这个方法使用的是 id 类型一致

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

        document.add(new Paragraph().add(new Text("Main Task: ").setFont(bold)).add(new Text(entry.getMainTask()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Skills: ").setFont(bold)).add(new Text(entry.getSkills()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Challenges: ").setFont(bold)).add(new Text(entry.getChallenges()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Result: ").setFont(bold)).add(new Text(entry.getResult()).setFont(normal)).setMarginBottom(8));
        document.add(new Paragraph().add(new Text("Endorsed: ").setFont(bold)).add(new Text(entry.isEndorsed() ? "Yes" : "No").setFont(normal)));

        document.close();
    }

    // === 在线预览：返回纯文本（只使用现有字段）===
    @GetMapping(value = "/text/{id}", produces = "text/plain; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> getLogbookText(@PathVariable Long id, HttpSession session) {
        // 1) 登录 & 角色校验
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // 2) 查询日志
        Optional<LogbookEntry> opt = logbookEntryRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body("Not found");
        }
        LogbookEntry e = opt.get();

        // 3) 所属校验
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

        // 4) 只拼接实体中确实存在的字段（与数据库列一致）
        StringBuilder sb = new StringBuilder();
        appendIfHas(sb, "Main Task",  nz(e.getMainTask()));
        appendIfHas(sb, "Skills",     nz(e.getSkills()));
        appendIfHas(sb, "Challenges", nz(e.getChallenges()));
        appendIfHas(sb, "Result",     nz(e.getResult()));

        String out = sb.toString().trim();
        return ResponseEntity.ok(out.isEmpty() ? "No content." : out);
    }

    /** 空串/Null 归一化 */
    private static String nz(String s) { return (s == null) ? "" : s.trim(); }

    /** 只有有值才追加到 StringBuilder */
    private static void appendIfHas(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n\n");
        }
    }



}
