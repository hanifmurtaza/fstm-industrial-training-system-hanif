package com.example.itsystem.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.example.itsystem.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.example.itsystem.service.FileStorageService;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.element.Text;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.model.CompanyInfoStatus;
import com.example.itsystem.model.Company;
import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;

import com.example.itsystem.service.LogbookEntryService;
import com.example.itsystem.service.VisitScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.itsystem.model.VisitEvaluation;

@Controller
public class StudentController {

    @Autowired private UserRepository userRepository;
    @Autowired private VisitScheduleService visitScheduleService;

    @Autowired private LogbookEntryRepository logbookEntryRepository;
    @Autowired private LogbookEntryService logbookEntryService;

    @Autowired private PlacementRepository placementRepository;
    @Autowired private CompanyRepository companyRepository;

    @Autowired private StudentAssessmentRepository studentAssessmentRepository;

    @Autowired private CompanyInfoRepository companyInfoRepository;
    @Autowired private VisitEvaluationRepository visitEvaluationRepository;


    @Autowired(required = false)
    private FileStorageService fileStorageService;

    private String currentSession(User user) {
        if (user != null && user.getSession() != null && !user.getSession().isBlank()) {
            return user.getSession();
        }
        return "2024/2025-2";
    }

    // ==========================
    //         Dashboard
    // ==========================
    @GetMapping("/student-dashboard")
    public String studentDashboard(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        model.addAttribute("name", user.getName());

        VisitSchedule visit = visitScheduleService.findUpcomingForStudent(user.getId());
        model.addAttribute("visitSchedule", visit);

        // ====== 还是保留你原来 StudentAssessment 的读取逻辑（Industry Supervisor / total / grade 继续用） ======
        String byUser = (user.getSession() != null && !user.getSession().isBlank()) ? user.getSession() : null;
        String fallbackDefault = "2024/2025-2";

        ArrayList<String> candidates = new ArrayList<>();
        if (byUser != null && !byUser.isBlank()) candidates.add(byUser.trim());
        if (fallbackDefault != null) candidates.add(fallbackDefault.trim());
        candidates = new ArrayList<>(new LinkedHashSet<>(candidates));

        StudentAssessment sa = null;
        for (String s : candidates) {
            sa = studentAssessmentRepository.findByStudentUserIdAndSession(user.getId(), s).orElse(null);
            if (sa != null) break;
        }
        if (sa == null) {
            sa = studentAssessmentRepository.findTopByStudentUserIdOrderByIdDesc(user.getId()).orElse(null);
        }

        // ====== Industry Supervisor (40) OFFICIAL rubric: Section B (30) + Section C (10) ======
        int isAttr30 = sa != null && sa.getIsAttributes30() != null ? sa.getIsAttributes30().intValue() : 0;
        int isOverall10 = sa != null && sa.getIsOverall10() != null ? sa.getIsOverall10().intValue() : 0;

        // Fallback to legacy buckets if official fields are empty (older data)
        if (isAttr30 == 0 && isOverall10 == 0 && sa != null) {
            int legacy = 0;
            if (sa.getIsSkills20() != null) legacy += sa.getIsSkills20().intValue();
            if (sa.getIsCommunication10() != null) legacy += sa.getIsCommunication10().intValue();
            if (sa.getIsTeamwork10() != null) legacy += sa.getIsTeamwork10().intValue();
            isAttr30 = Math.min(30, legacy); // best-effort display
            isOverall10 = Math.max(0, legacy - isAttr30);
        }

        int isSum = isAttr30 + isOverall10;
        boolean hasIS = sa != null && (isSum > 0);

        model.addAttribute("isAttr30", isAttr30);
        model.addAttribute("isOverall10", isOverall10);
        model.addAttribute("isSum", isSum);
        model.addAttribute("hasIS", hasIS);

        Integer total100 = sa != null && sa.getTotal100() != null ? sa.getTotal100().intValue() : null;
        String grade = sa != null ? sa.getGrade() : null;
        model.addAttribute("grade", grade);

        // ====== ✅ Visiting Lecturer (40) 新逻辑：从 visit_evaluation 拿（新结构） ======
        // 你 repo 里需要有这个方法：
        // Optional<VisitEvaluation> findFirstByStudentIdOrderByCreatedAtDesc(Long studentId);
        VisitEvaluation ve = visitEvaluationRepository
                .findFirstByStudentIdOrderByCreatedAtDesc(user.getId())
                .orElse(null);

        boolean hasVL = (ve != null);

        int vlRef10 = 0, vlEng10 = 0, vlSuit5 = 0, vlLog5 = 0, vlOverall10 = 0;
        int vlSum40 = 0;

        if (hasVL) {
            vlRef10 = safeLikert(ve.getReflectionLikert()) * 2;
            vlEng10 = safeLikert(ve.getEngagementLikert()) * 2;
            vlSuit5 = safeLikert(ve.getPlacementSuitabilityLikert()); // ✅ Suitability
            vlLog5 = safeLikert(ve.getLogbookLikert());
            vlOverall10 = safeLikert(ve.getLecturerOverallLikert()) * 2;

            vlSum40 = (ve.getTotalScore40() != null)
                    ? ve.getTotalScore40()
                    : (vlRef10 + vlEng10 + vlSuit5 + vlLog5 + vlOverall10);
        }

        model.addAttribute("hasVL", hasVL);
        model.addAttribute("vlRef10", vlRef10);
        model.addAttribute("vlEng10", vlEng10);
        model.addAttribute("vlSuit5", vlSuit5);
        model.addAttribute("vlLog5", vlLog5);
        model.addAttribute("vlOverall10", vlOverall10);
        model.addAttribute("vlSum", vlSum40);


        // ====== Evaluation Status cards ======
        // FinalReport / Logbook 的旧字段你现在是拿 VL 的旧结构分数（vlFinalReport40 / vlLogbook5）
        // 为了不破坏你现有逻辑：我们保持从 StudentAssessment 拿（你之后再统一改也行）
        boolean hasLogbook = sa != null && sa.getVlLogbook5() != null;
        boolean hasFinal   = sa != null && sa.getVlFinalReport40() != null;

        Integer logShown = hasLogbook ? (sa.getVlLogbook5() != null ? sa.getVlLogbook5().intValue() : 0) : null;
        Integer frShown  = hasFinal ? (sa.getVlFinalReport40() != null ? sa.getVlFinalReport40().intValue() : 0) : null;

        Integer isShown  = hasIS ? isSum : null;
        Integer vlShown  = hasVL ? vlSum40 : null;

        Map<String, Integer> evaluationStatus = new LinkedHashMap<>();
        evaluationStatus.put("FinalReport", frShown);
        evaluationStatus.put("IndustrySupervisor", isShown);
        evaluationStatus.put("Logbook", logShown);
        evaluationStatus.put("VisitingLecturer", vlShown);
        model.addAttribute("evaluationStatus", evaluationStatus);

        Map<String, Integer> maxMap = new HashMap<>();
        maxMap.put("FinalReport", 40);
        maxMap.put("IndustrySupervisor", 40);
        maxMap.put("Logbook", 5);
        maxMap.put("VisitingLecturer", 40); // ✅ 改成 40
        model.addAttribute("maxMap", maxMap);

        model.addAttribute("hasLogbook", hasLogbook);
        model.addAttribute("hasFinal", hasFinal);

        // ====== Total ======
        // 你现在 total100 可能是数据库算好的，也可能 null。
        // 我们保持你的做法：如果 null，就用 (vlSum40 + isSum) 先顶着
        model.addAttribute("total100", total100 != null ? total100 : (vlSum40 + isSum));

        return "student-dashboard";
    }

    // 放在 StudentController 类里任意位置即可（private helper）
    private int safeLikert(Integer v) {
        if (v == null) return 0;
        if (v < 1) return 0;
        return Math.min(v, 5);
    }


    // ==========================
    //      Student Company Info
    // ==========================
    @GetMapping("/student/company-info")
    public String companyInfoPage(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        boolean canSubmit = (latest == null || latest.getStatus() == CompanyInfoStatus.REJECTED);

        model.addAttribute("student", student);
        model.addAttribute("latestInfo", latest);
        model.addAttribute("canSubmit", canSubmit);

        return "student/company-form";
    }

    /**
     * ✅ 提交公司信息 + 实习起止日期校验（>=24周）
     */
    @PostMapping("/student/company-info")
    public String submitCompanyInfo(@RequestParam String companyName,
                                    @RequestParam(required = false) String companyAddress,
                                    @RequestParam String supervisorName,
                                    @RequestParam String supervisorEmail,
                                    @RequestParam(required = false) String supervisorPhone,
                                    @RequestParam("internshipStartDate") LocalDate internshipStartDate,
                                    @RequestParam("internshipEndDate") LocalDate internshipEndDate,
                                    HttpSession session,
                                    RedirectAttributes ra) {

        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        // 1) 不能重复提交（除非 REJECTED）
        CompanyInfo latest = companyInfoRepository
                .findFirstByStudentIdOrderByIdDesc(student.getId())
                .orElse(null);

        if (latest != null && latest.getStatus() != CompanyInfoStatus.REJECTED) {
            ra.addFlashAttribute("error",
                    "You already submitted your company information. Current status: " + latest.getStatus()
                            + ". Please wait for admin to process it.");
            return "redirect:/student/company-info";
        }

        // 2) 日期基础校验
        if (internshipStartDate == null || internshipEndDate == null) {
            ra.addFlashAttribute("error", "Please select internship start and end date.");
            return "redirect:/student/company-info";
        }
        if (internshipEndDate.isBefore(internshipStartDate)) {
            ra.addFlashAttribute("error", "Internship end date must be after start date.");
            return "redirect:/student/company-info";
        }

        // 3) ✅ >=24周校验（按天数>=168天）
        long days = ChronoUnit.DAYS.between(internshipStartDate, internshipEndDate) + 1; // 包含当天
        if (days < 168) {
            ra.addFlashAttribute("error", "Internship duration must be greater than 24 weeks.");
            return "redirect:/student/company-info";
        }

        // 4) 保存
        CompanyInfo info = new CompanyInfo();
        info.setStudentId(student.getId());
        info.setCompanyName(companyName.trim());
        info.setAddress(companyAddress);
        info.setSupervisorName(supervisorName.trim());
        info.setSupervisorEmail(supervisorEmail.trim());
        info.setSupervisorPhone(supervisorPhone);

        info.setInternshipStartDate(internshipStartDate);
        info.setInternshipEndDate(internshipEndDate);

        info.setSession(currentSession(student));
        info.setStatus(CompanyInfoStatus.PENDING);

        companyInfoRepository.save(info);

        ra.addFlashAttribute("success", "Your company information has been submitted and is now pending approval.");
        return "redirect:/student/company-info";
    }

    // ==========================
    //          Profile
    // ==========================
    @GetMapping("/student/profile")
    public String viewProfile(Model model, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        User fresh = userRepository.findById(student.getId()).orElse(student);
        session.setAttribute("user", fresh);

        String companyName = "-";
        Placement approved = placementRepository
                .findTopByStudentIdAndStatusOrderByIdDesc(fresh.getId(), PlacementStatus.APPROVED)
                .orElse(null);

        if (approved != null && approved.getCompanyId() != null) {
            companyName = companyRepository.findById(approved.getCompanyId())
                    .map(Company::getName)
                    .orElse("-");
        }

        List<User> lecturers = userRepository.findByRole("teacher");

        model.addAttribute("user", fresh);
        model.addAttribute("lecturers", lecturers);
        model.addAttribute("companyName", companyName);
        return "student/student-profile";
    }

    @PostMapping("/student/profile")
    public String updateLecturer(@RequestParam Long lecturerId, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        User lecturer = userRepository.findById(lecturerId).orElse(null);
        if (lecturer != null) {
            student.setLecturer(lecturer);
            userRepository.save(student);
            User updated = userRepository.findById(student.getId()).orElse(student);
            session.setAttribute("user", updated);
        }
        return "redirect:/student/profile";
    }

    @PostMapping("/student/profile/update")
    public String updateProfile(@RequestParam String session,
                                HttpSession httpSession) {
        User user = (User) httpSession.getAttribute("user");
        if (user != null) {
            user.setSession(session);
            userRepository.save(user);
            User updated = userRepository.findById(user.getId()).orElse(user);
            httpSession.setAttribute("user", updated);
        }
        return "redirect:/student/profile?success=true";
    }

    // ==========================
    //          Visit (你现有逻辑保留)
    // ==========================
    @PostMapping("/student/visit/confirm")
    public String confirmVisit(@RequestParam Long visitId, HttpSession session) {
        VisitSchedule visit = visitScheduleService.findById(visitId);
        visit.setStatus("Confirmed");
        visitScheduleService.saveSchedule(visit);
        return "redirect:/student-dashboard";
    }

    @PostMapping("/student/visit/reschedule")
    public String rescheduleVisit(@RequestParam Long visitId, HttpSession session) {
        VisitSchedule visit = visitScheduleService.findById(visitId);
        visit.setStatus("RescheduleRequested");
        visitScheduleService.saveSchedule(visit);
        return "redirect:/student-dashboard";
    }

    // ==========================
    //          Logbook
    // ==========================
    @GetMapping("/student/logbook/new")
    public String showLogbookForm(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        model.addAttribute("logbookEntry", new LogbookEntry());
        return "student/logbook-form";
    }

    @PostMapping("/student/logbook/save")
    public String saveLogbookEntry(@ModelAttribute LogbookEntry logbookEntry,
                                   @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                   HttpSession session) {

        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        logbookEntry.setStudentId(student.getId());
        logbookEntry.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        LogbookEntry saved = logbookEntryRepository.save(logbookEntry);

        if (photoFile != null && !photoFile.isEmpty() && fileStorageService != null) {
            String url = fileStorageService.storeLogbookFile(photoFile, student.getId(), saved.getId());
            saved.setPhotoPath(url);
            logbookEntryRepository.save(saved);
        }

        return "redirect:/student/logbook/list";
    }

    @GetMapping("/student/logbook/list")
    public String viewLogbookList(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        List<LogbookEntry> entries = logbookEntryService.getByStudentId(student.getId());
        model.addAttribute("logbookEntries", entries);
        return "student/logbook-list";
    }

    @GetMapping("/student/logbook/download/{id}")
    public void downloadSingleLogbookPdf(@PathVariable Long id,
                                         HttpServletResponse response,
                                         HttpSession session) throws IOException {
        User student = (User) session.getAttribute("user");
        if (student == null) {
            response.sendRedirect("/login");
            return;
        }

        LogbookEntry entry = logbookEntryService.getById(id);
        if (entry == null || !entry.getStudentId().equals(student.getId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=logbook_" + entry.getWeekStartDate() + ".pdf");

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

    @GetMapping("/student/logbook/delete/{id}")
    public String deleteLogbookEntry(@PathVariable Long id, HttpSession session) {
        User student = (User) session.getAttribute("user");
        if (student == null) return "redirect:/login";

        LogbookEntry entry = logbookEntryService.getById(id);
        if (entry != null && entry.getStudentId().equals(student.getId())) {
            logbookEntryService.deleteById(id);
        }
        return "redirect:/student/logbook/list";
    }

    // ==========================
    //        Final Report
    // ==========================
    @GetMapping("/student/final-report")
    public String finalReportPage(HttpSession session, Model model) {
        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        User fresh = userRepository.findById(student.getId()).orElse(student);
        session.setAttribute("user", fresh);

        model.addAttribute("user", fresh);
        return "student/final-report-form";
    }

    @PostMapping("/student/final-report/submit")
    public String submitFinalReport(@RequestParam(value = "reportFile", required = false) MultipartFile reportFile,
                                    @RequestParam(value = "videoLink", required = false) String videoLink,
                                    HttpSession session,
                                    RedirectAttributes ra) {

        User student = (User) session.getAttribute("user");
        if (student == null || !"student".equals(student.getRole())) return "redirect:/login";

        User dbStudent = userRepository.findById(student.getId()).orElse(student);

        // 1) 保存 PDF（保持原逻辑）
        if (fileStorageService != null) {
            if (reportFile != null && !reportFile.isEmpty()) {
                String pdfUrl = fileStorageService.storeFinalReportPdf(reportFile, dbStudent.getId());
                dbStudent.setFinalReportPdfPath(pdfUrl);
            }
        }

        // 2) 保存 Video Link（不再上传视频）
        if (videoLink == null || videoLink.trim().isEmpty()) {
            // 你也可以选择不强制 required，这里我按“必须提交链接”处理
            ra.addFlashAttribute("errorMessage", "Please paste your video presentation link.");
            return "redirect:/student/final-report";
        }
        dbStudent.setFinalReportVideoPath(videoLink.trim()); // ✅ 复用原字段存链接

        userRepository.save(dbStudent);
        session.setAttribute("user", dbStudent);

        return "redirect:/student/final-report?success=true";
    }


    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}