package com.example.itsystem.controller;

import com.example.itsystem.model.*;
import com.example.itsystem.repository.*;
import com.example.itsystem.service.AdminMetricsService;
import com.example.itsystem.service.GradingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Set;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.repository.StudentAssessmentRepository;
import com.example.itsystem.util.UpmGradeUtil;
import com.example.itsystem.model.VisitSchedule;
import jakarta.servlet.http.HttpSession;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.example.itsystem.model.Department;
import java.util.stream.Collectors;
import java.util.Objects;


@Controller
@RequestMapping("/admin")
public class AdminController {

    // ----- Repositories -----
    @Autowired private UserRepository userRepository;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private CompanyInfoRepository companyInfoRepository;
    @Autowired(required = false) private PlacementRepository placementRepository;
    @Autowired(required = false) private CompanyRepository companyRepository;
    @Autowired(required = false) private LogbookEntryRepository logbookEntryRepository;
    @Autowired private com.example.itsystem.service.BulkStudentImportService bulkImportService;
    @Autowired private StudentAssessmentRepository studentAssessmentRepository;
    @Autowired(required = false) private VisitScheduleRepository visitScheduleRepository;

    @Autowired(required = false) private VisitEvaluationRepository visitEvaluationRepository;
    @Autowired private SupervisorEvaluationRepository supervisorEvaluationRepository;





    // ----- Services -----
    @Autowired(required = false) private AdminMetricsService adminMetricsService;
    @Autowired(required = false) private GradingService gradingService;



    // Password encoder
    @Autowired(required = false) private PasswordEncoder passwordEncoder;

    // ============================
    // Dashboard
    // ============================
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long studentCount   = userRepository.countByRole("student");
        long companyCount   = companyRepository.count();
        long evalCount      = evaluationRepository.count();
        long documentCount  = documentRepository.count();

        model.addAttribute("studentCount", studentCount);
        model.addAttribute("companyCount", companyCount);
        model.addAttribute("evalCount", evalCount);
        model.addAttribute("documentCount", documentCount);

        long pendingCompanyInfo = companyInfoRepository.countByStatus(CompanyInfoStatus.PENDING);
        model.addAttribute("pendingCompanyInfo", pendingCompanyInfo);

        if (placementRepository != null) {
            long awaitingApproval = placementRepository.countByStatus(PlacementStatus.AWAITING_ADMIN);
            model.addAttribute("awaitingApproval", awaitingApproval);
        } else {
            model.addAttribute("awaitingApproval", 0L);
        }

        if (logbookEntryRepository != null) {
            long awaitingSup = logbookEntryRepository.countByStatusAndEndorsedFalse(ReviewStatus.PENDING);
            long awaitingLec = logbookEntryRepository.countByEndorsedTrueAndEndorsedByLecturerFalse();
            model.addAttribute("awaitingSupEndorse", awaitingSup);
            model.addAttribute("awaitingLecEndorse", awaitingLec);
        }

        if (adminMetricsService != null) {
            model.addAttribute("metrics", adminMetricsService.snapshot());
        }
        return "admin-dashboard";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("placementsAwaiting",
                placementRepository == null ? Page.empty()
                        : placementRepository.findByStatus(PlacementStatus.AWAITING_ADMIN,
                        PageRequest.of(0, 10)));

        if (logbookEntryRepository != null) {
            model.addAttribute("logbooksAwaitingSup",
                    logbookEntryRepository.findByStatusAndEndorsedFalse(
                            ReviewStatus.PENDING, PageRequest.of(0, 10)));
            model.addAttribute("logbooksAwaitingLec",
                    logbookEntryRepository.findByEndorsedTrueAndEndorsedByLecturerFalse(
                            PageRequest.of(0, 10)));
        } else {
            model.addAttribute("logbooksAwaitingSup", Page.empty());
            model.addAttribute("logbooksAwaitingLec", Page.empty());
        }
        return "admin-notifications";
    }

    // ============================
    // Students
    // ============================
    @GetMapping("/students")
    public String students(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "session", required = false) String session,
            @RequestParam(value = "department", required = false) Department department,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            Model model
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<User> students = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                search != null ? search.trim() : null,
                session != null && !session.isBlank() ? session : null,
                department,
                pageable
        );

        // ✅ Company shown in list comes from APPROVED placement (Company Master)
        Map<Long, String> studentCompanyMap = new HashMap<>();

// 1) latest approved placement per student on current page
        Map<Long, Placement> latestApprovedPlacement = new HashMap<>();
        for (User s : students.getContent()) {
            placementRepository
                    .findTopByStudentIdAndStatusOrderByIdDesc(s.getId(), PlacementStatus.APPROVED)
                    .ifPresent(p -> latestApprovedPlacement.put(s.getId(), p));
        }

// 2) collect companyIds from placements
        Set<Long> companyIds = latestApprovedPlacement.values().stream()
                .map(Placement::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

// 3) batch fetch company names
        Map<Long, String> companyNameById = new HashMap<>();
        if (!companyIds.isEmpty()) {
            companyRepository.findAllById(companyIds).forEach(c ->
                    companyNameById.put(c.getId(), c.getName())
            );
        }

// 4) final map studentId -> companyName
        for (User s : students.getContent()) {
            Placement p = latestApprovedPlacement.get(s.getId());
            String companyName = "-";
            if (p != null && p.getCompanyId() != null) {
                companyName = companyNameById.getOrDefault(p.getCompanyId(), "-");
            }
            studentCompanyMap.put(s.getId(), companyName);
        }

        model.addAttribute("studentCompanyMap", studentCompanyMap);
        model.addAttribute("students", students);
        model.addAttribute("search", search);
        model.addAttribute("department", department);
        model.addAttribute("selectedSession", session);
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", students.getTotalPages());
        model.addAttribute("size", size);

        return "admin-students";
    }


    @GetMapping("/students/add")
    public String addStudentForm(Model model) {
        model.addAttribute("student", new User());
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("lecturers", userRepository.findByRole("teacher"));

        return "admin-student-form";
    }

    @PostMapping("/students/add")
    public String saveNewStudent(@RequestParam String name,
                                 @RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam String studentId,
                                 @RequestParam String session,
                                 @RequestParam String company,
                                 @RequestParam(required = false) Department department) {

        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setStudentId(studentId);
        user.setSession(session);
        user.setCompany(company);
        user.setDepartment(department);
        user.setRole("student");

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank.");
        }
        user.setPassword(encode(password));

        if (user.getAccessStart() == null) user.setAccessStart(LocalDate.now());
        if (user.getAccessEnd() == null)   user.setAccessEnd(LocalDate.now().plusMonths(6));
        user.setEnabled(true);

        userRepository.save(user);
        logAction("ADD_STUDENT", "Added student: " + name);
        return "redirect:/admin/students";
    }

    @GetMapping("/students/edit/{id}")
    public String showEditStudentForm(@PathVariable Long id, Model model) {

        User student = userRepository.findById(id).orElse(null);
        model.addAttribute("student", student);

// ✅ show company from APPROVED placement (read-only)
        String currentPlacementCompany = "-";
        if (student != null && placementRepository != null && companyRepository != null) {
            Placement p = placementRepository
                    .findTopByStudentIdAndStatusOrderByIdDesc(student.getId(), PlacementStatus.APPROVED)
                    .orElse(null);
            if (p != null && p.getCompanyId() != null) {
                currentPlacementCompany = companyRepository.findById(p.getCompanyId())
                        .map(Company::getName)
                        .orElse("-");
            }
        }
        model.addAttribute("currentPlacementCompany", currentPlacementCompany);
        model.addAttribute("student", userRepository.findById(id).orElse(null));
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("lecturers", userRepository.findByRole("teacher"));
        return "admin-student-form";
    }

    @PostMapping("/students/save")
    public String saveStudent(@ModelAttribute("student") User incoming) {
        incoming.setRole("student");
        if (incoming.getId() != null) {
            User current = userRepository.findById(incoming.getId()).orElse(null);
            if (current != null) {
                if (incoming.getPassword() == null || incoming.getPassword().isBlank()) {
                    incoming.setPassword(current.getPassword());
                    incoming.setCompany(current.getCompany());
                } else {
                    incoming.setPassword(encode(incoming.getPassword()));
                }
                if (incoming.getEnabled() == null) incoming.setEnabled(current.getEnabled());
                if (incoming.getAccessStart() == null) incoming.setAccessStart(current.getAccessStart());
                if (incoming.getAccessEnd() == null) incoming.setAccessEnd(current.getAccessEnd());
                if (incoming.getRemarks() == null) incoming.setRemarks(current.getRemarks());
            }
        } else {
            if (incoming.getPassword() == null || incoming.getPassword().isBlank()) {
                throw new IllegalArgumentException("Password cannot be blank.");
            }
            incoming.setPassword(encode(incoming.getPassword()));
            if (incoming.getEnabled() == null) incoming.setEnabled(true);
            if (incoming.getAccessStart() == null) incoming.setAccessStart(LocalDate.now());
            if (incoming.getAccessEnd() == null) incoming.setAccessEnd(LocalDate.now().plusMonths(6));
        }

        // Fix transient lecturer object from form binding
        if (incoming.getLecturer() != null) {
            Long lecId = incoming.getLecturer().getId();

            if (lecId == null) {
                // If user selected 'none' / empty option
                incoming.setLecturer(null);
            } else {
                // Replace transient User with a managed entity from DB
                User managedLecturer = userRepository.findById(lecId).orElse(null);
                incoming.setLecturer(managedLecturer);
            }
        }

        userRepository.save(incoming);
        logAction("UPDATE_STUDENT", "Updated student: " + incoming.getName());
        return "redirect:/admin/students";
    }

    @PostMapping("/students/delete/{id}")
    public String deleteStudent(@PathVariable Long id) {
        userRepository.deleteById(id);
        logAction("DELETE_STUDENT", "Deleted student with ID: " + id);
        return "redirect:/admin/students";
    }

    // enable/disable user
    @PostMapping("/users/{id}/status")
    public String toggleUser(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             @RequestParam(required = false, defaultValue = "students") String backTo) {
        User u = userRepository.findById(id).orElseThrow();
        u.setEnabled(enabled);
        userRepository.save(u);
        logAction("USER_STATUS", "Set user " + u.getUsername() + " enabled=" + enabled);
        return "redirect:/admin/" + backTo;
    }

    // access window
    @PostMapping("/users/{id}/access-window")
    public String setAccessWindow(@PathVariable Long id,
                                  @RequestParam String start,
                                  @RequestParam String end,
                                  @RequestParam(required = false, defaultValue = "students") String backTo) {
        User u = userRepository.findById(id).orElseThrow();
        u.setAccessStart(LocalDate.parse(start));
        u.setAccessEnd(LocalDate.parse(end));
        userRepository.save(u);
        logAction("USER_ACCESS_WINDOW", "Access window set for " + u.getUsername() + " [" + start + " .. " + end + "]");
        return "redirect:/admin/" + backTo;
    }

    @PostMapping("/students/bulk")
    public String bulkStudents(
            @RequestParam(value = "ids", required = false) List<Long> ids,
            @RequestParam("mode") String mode,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,

            // ✅ new: apply to all filtered students
            @RequestParam(value = "applyAll", defaultValue = "false") boolean applyAll,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "session", required = false) String session,
            @RequestParam(value = "department", required = false) Department department,

            RedirectAttributes ra
    ) {

        // 1) Decide target users
        List<User> users;

        if (applyAll) {
            // ✅ apply to ALL students matching current filter
            users = userRepository.findAllStudentsForBulk("student", search, session, department);

            if (users.isEmpty()) {
                ra.addFlashAttribute("toast", "No students found for the current filter.");
                return "redirect:/admin/students";
            }
        } else {
            // ✅ apply only selected students (current behavior)
            if (ids == null || ids.isEmpty()) {
                ra.addFlashAttribute("toast", "No students selected for bulk action.");
                return "redirect:/admin/students";
            }
            users = userRepository.findAllById(ids);
            if (users.isEmpty()) {
                ra.addFlashAttribute("toast", "Selected students not found.");
                return "redirect:/admin/students";
            }
        }

        int affected = users.size();

        // 2) Apply operation
        if ("status".equals(mode) && enabled != null) {
            for (User u : users) {
                u.setEnabled(enabled);
            }
            userRepository.saveAll(users);

            logAction("BULK_USER_STATUS",
                    "Set enabled=" + enabled + " for " + affected + " students"
                            + (applyAll ? " (applyAllFiltered=true)" : ""));

            ra.addFlashAttribute("toast",
                    (enabled ? "Enabled " : "Disabled ") + affected + " students.");

        } else if ("window".equals(mode)) {

            // Allow clearing the window if both empty (optional but useful)
            boolean startEmpty = (start == null || start.isBlank());
            boolean endEmpty = (end == null || end.isBlank());

            if (!startEmpty && !endEmpty) {
                LocalDate s = LocalDate.parse(start);
                LocalDate e = LocalDate.parse(end);

                if (e.isBefore(s)) {
                    ra.addFlashAttribute("toast", "End date cannot be before start date.");
                    return "redirect:/admin/students";
                }

                for (User u : users) {
                    u.setAccessStart(s);
                    u.setAccessEnd(e);
                }
                userRepository.saveAll(users);

                logAction("BULK_USER_ACCESS_WINDOW",
                        "Set access window [" + s + " to " + e + "] for " + affected + " students"
                                + (applyAll ? " (applyAllFiltered=true)" : ""));

                ra.addFlashAttribute("toast",
                        "Updated access window for " + affected + " students.");

            } else if (startEmpty && endEmpty) {
                // ✅ optional: clear access window in bulk
                for (User u : users) {
                    u.setAccessStart(null);
                    u.setAccessEnd(null);
                }
                userRepository.saveAll(users);

                logAction("BULK_USER_ACCESS_WINDOW",
                        "Cleared access window for " + affected + " students"
                                + (applyAll ? " (applyAllFiltered=true)" : ""));

                ra.addFlashAttribute("toast",
                        "Cleared access window for " + affected + " students.");

            } else {
                ra.addFlashAttribute("toast", "Please fill BOTH start and end date (or leave both empty to clear).");
            }

        } else {
            ra.addFlashAttribute("toast", "Invalid bulk operation.");
        }

        // 3) Redirect back, preserving current filters (nice UX)
        String redirect = "redirect:/admin/students";
        // You can keep it simple:
        // return redirect;

        // Better: keep filters so admin stays on same filtered session view
        String qs = buildStudentsQueryString(search, session, department);
        return redirect + qs;
    }

    // helper to keep filter after redirect (optional but good UX)
    private String buildStudentsQueryString(String search, String session, Department department) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        if (search != null && !search.isBlank()) {
            sb.append(first ? "?" : "&").append("search=").append(urlEncode(search));
            first = false;
        }
        if (session != null && !session.isBlank()) {
            sb.append(first ? "?" : "&").append("session=").append(urlEncode(session));
            first = false;
        }
        if (department != null) {
            sb.append(first ? "?" : "&").append("department=").append(urlEncode(department.name()));
        }
        return sb.toString();
    }

    // minimal url encode helper
    private String urlEncode(String v) {
        try {
            return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return v;
        }
    }


    @GetMapping("/students/import")
    public String importForm(Model model) {
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("departmentOptions", Department.values());
        return "admin-students-import";
    }

    @PostMapping("/students/import/preview")
    public String importPreview(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                @RequestParam("defaultSession") String defaultSession,
                                @RequestParam("defaultDepartment") Department defaultDepartment,
                                Model model,
                                jakarta.servlet.http.HttpSession session) throws IOException {

        var preview = bulkImportService.preview(file, defaultSession);

        session.setAttribute("bulkImportPreview", preview);
        session.setAttribute("bulkImportDepartment", defaultDepartment);

        model.addAttribute("preview", preview);
        model.addAttribute("defaultSession", defaultSession);
        model.addAttribute("defaultDepartment", defaultDepartment);

        return "admin-students-import-preview";
    }


    @PostMapping("/students/import/commit")
    public String importCommit(jakarta.servlet.http.HttpSession session,
                               RedirectAttributes ra) {

        com.example.itsystem.dto.BulkPreviewResult preview =
                (com.example.itsystem.dto.BulkPreviewResult) session.getAttribute("bulkImportPreview");

        if (preview == null) {
            ra.addFlashAttribute("toast", "No bulk import preview found. Please upload the file again.");
            return "redirect:/admin/students";
        }

        Department dept = (Department) session.getAttribute("bulkImportDepartment");
        int created = bulkImportService.commit(preview.validRows(), this::ensureUser, dept);
        session.removeAttribute("bulkImportDepartment");


        logAction("BULK_IMPORT_STUDENTS", "Imported " + created + " students");
        ra.addFlashAttribute("toast", "Imported " + created + " students.");
        return "redirect:/admin/students";
    }

    private void ensureUser(String matric, String name) {
        userRepository.findByUsername(matric).orElseGet(() -> {
            User u = new User();
            u.setUsername(matric);
            u.setStudentId(matric);
            u.setName(name);
            u.setRole("student");
            u.setPassword(encode("changeme"));
            u.setEnabled(false);
            if (u.getAccessStart() == null) u.setAccessStart(LocalDate.now());
            if (u.getAccessEnd() == null)   u.setAccessEnd(LocalDate.now().plusMonths(6));
            return userRepository.save(u);
        });
    }

    // ============================
// Evaluations (Admin overview: VL + Industry scores)
// ============================
    @GetMapping("/evaluations")
    public String manageEvaluations(@RequestParam(value = "search", required = false) String search,
                                    @RequestParam(value = "session", required = false) String session,
                                    @RequestParam(value = "department", required = false) String department,
                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "25") int size,
                                    Model model) {


        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        // ✅ normalize empty to null (critical)
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String sessionTerm = (session != null && !session.isBlank()) ? session.trim() : null;

        Department deptEnum = null;
        if (department != null && !department.isBlank()) {
            try {
                deptEnum = Department.valueOf(department.trim());
            } catch (Exception ignored) {
                deptEnum = null;
            }
        }

        // ✅ Use ONE query path (no if/else), so behavior is consistent
        Page<User> students = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                searchTerm,
                sessionTerm,
                deptEnum,
                pageable
        );


        // Latest assessment per student (same data student dashboard uses)
        Map<Long, StudentAssessment> assessmentByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            studentAssessmentRepository.findTopByStudentUserIdOrderByIdDesc(s.getId())
                    .ifPresent(sa -> {
                        // keep existing grade logic untouched for DB rows if you want,
                        // but list page will use the new gradeByStudentId anyway.
                        if (sa.getGrade() == null || sa.getGrade().isBlank()) {
                            sa.setGrade(computeGrade(sa));
                            studentAssessmentRepository.save(sa);
                        }
                        assessmentByStudentId.put(s.getId(), sa);
                    });
        }

        // Visiting Lecturer score (40) derived from latest VisitEvaluation (official guideline)
        Map<Long, BigDecimal> vlScoreByStudentId = new HashMap<>();
        if (visitEvaluationRepository != null) {
            for (User s : students.getContent()) {
                VisitEvaluation ve = visitEvaluationRepository
                        .findFirstByStudentIdOrderByCreatedAtDesc(s.getId())
                        .orElse(null);
                BigDecimal vl40 = computeVl40FromVisitEvaluation(ve);
                if (vl40 != null) vlScoreByStudentId.put(s.getId(), vl40);
            }
        }

        // Final Report (10) keyed-in by Admin (Written 5 + Video 5). Stored in StudentAssessment.
        Map<Long, BigDecimal> reportScoreByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            StudentAssessment sa = assessmentByStudentId.get(s.getId());
            if (sa != null) {
                BigDecimal rep10 = nz(sa.getAdminReportWritten5()).add(nz(sa.getAdminReportVideo5()));
                reportScoreByStudentId.put(s.getId(), rep10);
            }
        }

        // Logbook (10) will be wired later after faculty confirms marking scheme.
        // For now we only display a PENDING badge (no numeric backend yet).
        Map<Long, BigDecimal> logbookScoreByStudentId = new HashMap<>();

        // Total + Grade (temporary: logbook treated as 0 until marking is confirmed)
        Map<Long, BigDecimal> totalByStudentId = new HashMap<>();
        Map<Long, String> gradeByStudentId = new HashMap<>();

        for (User s : students.getContent()) {
            Long sid = s.getId();
            StudentAssessment sa = assessmentByStudentId.get(sid);

            boolean hasVl = vlScoreByStudentId.containsKey(sid);
            boolean hasReport = reportScoreByStudentId.containsKey(sid);
            boolean hasLogbook = logbookScoreByStudentId.containsKey(sid);

            // If absolutely no data exists yet, show '-' like before.
            if (!hasVl && sa == null && !hasReport && !hasLogbook) {
                continue;
            }

            BigDecimal vl = hasVl ? vlScoreByStudentId.get(sid) : BigDecimal.ZERO;

            BigDecimal ind = BigDecimal.ZERO;
            if (sa != null) {
                // Industry supervisor is already 40 in your StudentAssessment buckets:
                ind = nz(sa.getIsAttributes30()).add(nz(sa.getIsOverall10()));
            }

            BigDecimal rep = hasReport ? reportScoreByStudentId.get(sid) : BigDecimal.ZERO;
            BigDecimal log = hasLogbook ? logbookScoreByStudentId.get(sid) : BigDecimal.ZERO;

            BigDecimal total = vl.add(ind).add(rep).add(log); // /100 (logbook currently 0)
            totalByStudentId.put(sid, total);
            gradeByStudentId.put(sid, UpmGradeUtil.gradeFromTotal(total.doubleValue()));
        }

        model.addAttribute("students", students);
        model.addAttribute("assessmentByStudentId", assessmentByStudentId);

        // NEW columns
        model.addAttribute("vlScoreByStudentId", vlScoreByStudentId);
        model.addAttribute("reportScoreByStudentId", reportScoreByStudentId);
        model.addAttribute("logbookScoreByStudentId", logbookScoreByStudentId);
        model.addAttribute("totalByStudentId", totalByStudentId);
        model.addAttribute("gradeByStudentId", gradeByStudentId);

        model.addAttribute("search", searchTerm);
        model.addAttribute("selectedSession", sessionTerm);
        model.addAttribute("currentPage", page);
        model.addAttribute("size", size);
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("selectedDepartment", deptEnum != null ? deptEnum.name() : "");


        List<String> sessions = userRepository.findDistinctStudentSessions();
        model.addAttribute("sessions", sessions);

        return "admin-evaluations";
    }


    @GetMapping("/evaluations/{studentId}")
    public String viewStudentEvaluation(@PathVariable Long studentId, Model model) {

        User student = userRepository.findById(studentId).orElse(null);
        if (student == null) return "redirect:/admin/evaluations";

        StudentAssessment sa = studentAssessmentRepository
                .findTopByStudentUserIdOrderByIdDesc(studentId)
                .orElse(null);

        // Industry supervisor + company
        Placement placement = placementRepository
                .findTopByStudentIdOrderByIdDesc(studentId)
                .orElse(null);

        User industrySupervisor = null;
        Company company = null;

        if (placement != null) {
            if (placement.getSupervisorUserId() != null) {
                industrySupervisor = userRepository
                        .findById(placement.getSupervisorUserId())
                        .orElse(null);
            }
            if (placement.getCompanyId() != null) {
                company = companyRepository
                        .findById(placement.getCompanyId())
                        .orElse(null);
            }
        }

        // Visiting Lecturer
        User visitingLecturer = null;

        // 1) Try from StudentAssessment first
        if (sa != null && sa.getVisitingLecturerId() != null) {
            visitingLecturer = userRepository.findById(sa.getVisitingLecturerId()).orElse(null);
        }

        // 2) Fallback: try from VisitSchedule (if repo available)
        if (visitingLecturer == null && visitScheduleRepository != null) {
            VisitSchedule vs = visitScheduleRepository
                    .findTopByStudentIdOrderByIdDesc(studentId)
                    .orElse(null);

            if (vs != null && vs.getLecturerId() != null) {
                visitingLecturer = userRepository.findById(vs.getLecturerId()).orElse(null);

                // Optional backfill
                if (sa != null && sa.getVisitingLecturerId() == null) {
                    sa.setVisitingLecturerId(vs.getLecturerId());
                    studentAssessmentRepository.save(sa);
                }
            }
        }

        // ✅ NEW: Latest Visiting Lecturer evaluation (40%)
        VisitEvaluation visitEval = visitEvaluationRepository
                .findFirstByStudentIdOrderByCreatedAtDesc(studentId)
                .orElse(null);

        if (visitEval != null && visitEval.getTotalScore40() == null) {
            visitEval.recalcTotalScore40();
        }

        // ✅ NEW: Latest Supervisor evaluation (comments live here)
        SupervisorEvaluation supEval = null;
        if (placement != null) {
            supEval = supervisorEvaluationRepository.findByPlacementId(placement.getId()).orElse(null);
        }

        // ✅ NEW: Component scores
        BigDecimal vl40 = (visitEval != null && visitEval.getTotalScore40() != null)
                ? BigDecimal.valueOf(visitEval.getTotalScore40())
                : BigDecimal.ZERO;

        BigDecimal industry40 = BigDecimal.ZERO;
        if (sa != null) {
            BigDecimal a30 = sa.getIsAttributes30() != null ? sa.getIsAttributes30() : BigDecimal.ZERO;
            BigDecimal o10 = sa.getIsOverall10() != null ? sa.getIsOverall10() : BigDecimal.ZERO;
            industry40 = a30.add(o10);
        }

        // Final Report (10) keyed by admin: written 5 + video 5
        BigDecimal report10 = BigDecimal.ZERO;
        if (sa != null) {
            BigDecimal w5 = sa.getAdminReportWritten5() != null ? sa.getAdminReportWritten5() : BigDecimal.ZERO;
            BigDecimal v5 = sa.getAdminReportVideo5() != null ? sa.getAdminReportVideo5() : BigDecimal.ZERO;
            report10 = w5.add(v5);
        }

        // Logbook (10) -> pending for now
        BigDecimal logbook10 = null; // null means show "PENDING"

        // Total out of 100 (logbook treated as 0 until confirmed)
        BigDecimal total100 = vl40.add(industry40).add(report10);
        String grade = UpmGradeUtil.gradeFromTotal(total100.doubleValue());

        model.addAttribute("student", student);
        model.addAttribute("sa", sa);
        model.addAttribute("industrySupervisor", industrySupervisor);
        model.addAttribute("company", company);
        model.addAttribute("visitingLecturer", visitingLecturer);

        // ✅ NEW: pass eval objects + computed scores
        model.addAttribute("visitEval", visitEval);
        model.addAttribute("supEval", supEval);

        model.addAttribute("vl40", vl40);
        model.addAttribute("industry40", industry40);
        model.addAttribute("report10", report10);
        model.addAttribute("logbook10", logbook10); // null => pending
        model.addAttribute("total100New", total100);
        model.addAttribute("gradeNew", grade);

        return "admin-evaluation-detail";
    }




    // ====================== LECTURER MANAGEMENT ======================

    @GetMapping("/lecturers")
    public String listLecturers(Model model) {
        List<User> lecturers = userRepository.findByRole("teacher");
        model.addAttribute("lecturers", lecturers);
        return "admin-lecturers";
    }

    @GetMapping("/lecturers/add")
    public String showAddLecturerForm(Model model) {
        User lecturer = new User();
        lecturer.setRole("teacher"); // pre-set role
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("formTitle", "Add Lecturer");
        return "admin-lecturer-form";
    }

    @GetMapping("/lecturers/edit/{id}")
    public String showEditLecturerForm(@PathVariable Long id, Model model) {
        User lecturer = userRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Invalid lecturer Id:" + id));
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("formTitle", "Edit Lecturer");
        return "admin-lecturer-form";
    }

    @PostMapping("/lecturers/save")
    public String saveLecturer(@ModelAttribute("lecturer") User lecturer,
                               @RequestParam(required = false) String rawPassword) {

        // make sure role is always teacher
        lecturer.setRole("teacher");
        lecturer.setEnabled(true);

        if (lecturer.getId() == null) {
            // NEW lecturer – password is required
            if (rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalArgumentException("Password is required for new lecturer");
            }
            lecturer.setPassword(passwordEncoder.encode(rawPassword));
        } else {
            // EXISTING lecturer – only update password if something was entered
            User existing = userRepository.findById(lecturer.getId()).orElseThrow();
            if (rawPassword == null || rawPassword.isBlank()) {
                lecturer.setPassword(existing.getPassword());
            } else {
                lecturer.setPassword(passwordEncoder.encode(rawPassword));
            }
        }

        userRepository.save(lecturer);
        return "redirect:/admin/lecturers";
    }

    @GetMapping("/lecturers/delete/{id}")
    public String deleteLecturer(@PathVariable Long id) {
        userRepository.deleteById(id);
        return "redirect:/admin/lecturers";
    }

    @GetMapping("/lecturers/{lecturerId}/assign-students")
    public String assignStudentsToLecturerPage(
            @PathVariable Long lecturerId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "session", required = false) String session,
            @RequestParam(value = "department", required = false) Department department,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            Model model
    ) {
        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new IllegalArgumentException("Lecturer not found: " + lecturerId));

        // Optional safety (recommended)
        if (!"teacher".equalsIgnoreCase(lecturer.getRole())) {
            throw new IllegalArgumentException("Selected user is not a lecturer.");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        Page<User> students = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                search != null ? search.trim() : null,
                session != null && !session.isBlank() ? session : null,
                department,
                pageable
        );

        model.addAttribute("lecturer", lecturer);
        model.addAttribute("students", students);

        model.addAttribute("search", search);
        model.addAttribute("selectedSession", session);
        model.addAttribute("department", department);

        // reuse dropdown helpers you already use in students page
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("departmentOptions", Department.values());

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", students.getTotalPages());
        model.addAttribute("size", size);

        return "admin-lecturer-assign-students";
    }

    @PostMapping("/lecturers/{lecturerId}/assign-students")
    public String assignStudentsToLecturerSubmit(
            @PathVariable Long lecturerId,
            @RequestParam(value = "ids", required = false) List<Long> ids,

            @RequestParam("mode") String mode, // selected | allFiltered
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,

            // keep filters (for applyAll)
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "session", required = false) String session,
            @RequestParam(value = "department", required = false) Department department,

            RedirectAttributes ra
    ) {
        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new IllegalArgumentException("Lecturer not found: " + lecturerId));

        if (!"teacher".equalsIgnoreCase(lecturer.getRole())) {
            throw new IllegalArgumentException("Selected user is not a lecturer.");
        }

        List<User> targets;

        boolean applyAll = "allFiltered".equalsIgnoreCase(mode);

        if (applyAll) {
            targets = userRepository.findAllStudentsForBulk("student", search, session, department);

            if (targets.isEmpty()) {
                ra.addFlashAttribute("toast", "No students found for the current filter.");
                return "redirect:/admin/lecturers/" + lecturerId + "/assign-students"
                        + buildStudentsQueryString(search, session, department);
            }

        } else {
            if (ids == null || ids.isEmpty()) {
                ra.addFlashAttribute("toast", "No students selected.");
                return "redirect:/admin/lecturers/" + lecturerId + "/assign-students"
                        + buildStudentsQueryString(search, session, department);
            }

            targets = userRepository.findAllById(ids);
            if (targets.isEmpty()) {
                ra.addFlashAttribute("toast", "Selected students not found.");
                return "redirect:/admin/lecturers/" + lecturerId + "/assign-students"
                        + buildStudentsQueryString(search, session, department);
            }
        }

        int assigned = 0;
        int skipped = 0;

        for (User stu : targets) {
            if (!overwrite && stu.getLecturer() != null) {
                skipped++;
                continue;
            }
            stu.setLecturer(lecturer);
            assigned++;
        }

        userRepository.saveAll(targets);

        logAction("ASSIGN_STUDENTS_TO_LECTURER",
                "lecturerId=" + lecturerId + ", assigned=" + assigned + ", skipped=" + skipped
                        + ", applyAllFiltered=" + applyAll);

        ra.addFlashAttribute("toast",
                "Assigned " + assigned + " student(s) to " + lecturer.getName()
                        + (skipped > 0 ? (" (skipped " + skipped + ")") : ""));

        return "redirect:/admin/lecturers";
    }



    // ============================
    // Placements
    // ============================
    @GetMapping("/placements")
    public String listPlacements(@RequestParam(value = "status", required = false) PlacementStatus status,
                                 @RequestParam(value = "q", required = false) String q,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 Model model) {
        requireBean(placementRepository, "PlacementRepository");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Placement> placements = (status == null)
                ? placementRepository.findAll(pageable)
                : placementRepository.findByStatus(status, pageable);

        java.util.Set<Long> studentIds    = new java.util.HashSet<>();
        java.util.Set<Long> supervisorIds = new java.util.HashSet<>();
        java.util.Set<Long> companyIds    = new java.util.HashSet<>();

        for (Placement p : placements.getContent()) {
            if (p.getStudentId() != null)        studentIds.add(p.getStudentId());
            if (p.getSupervisorUserId() != null) supervisorIds.add(p.getSupervisorUserId());
            if (p.getCompanyId() != null)        companyIds.add(p.getCompanyId());
        }

        // Map of userId -> User (for both student + supervisor)
        java.util.Map<Long, User> userById = new java.util.HashMap<>();
        if (!studentIds.isEmpty() || !supervisorIds.isEmpty()) {
            java.util.Set<Long> allUserIds = new java.util.HashSet<>(studentIds);
            allUserIds.addAll(supervisorIds);
            for (User u : userRepository.findAllById(allUserIds)) {
                userById.put(u.getId(), u);
            }
        }

        // Map of companyId -> Company
        java.util.Map<Long, Company> companyById = new java.util.HashMap<>();
        if (!companyIds.isEmpty() && companyRepository != null) {
            for (Company c : companyRepository.findAllById(companyIds)) {
                companyById.put(c.getId(), c);
            }
        }

        model.addAttribute("placements", placements);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("userById", userById);
        model.addAttribute("companyById", companyById);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", placements.getTotalPages());
        return "admin-placements";
    }


    @PostMapping("/placements/{id}/approve")
    public String approvePlacement(@PathVariable Long id) {
        requireBean(placementRepository, "PlacementRepository");

        Placement plc = placementRepository.findById(id).orElseThrow();
        if (plc.getStatus() != PlacementStatus.AWAITING_ADMIN) {
            return "redirect:/admin/placements?status=" + plc.getStatus();
        }
        plc.setStatus(PlacementStatus.APPROVED);
        placementRepository.save(plc);

        logAction("APPROVE_PLACEMENT", "Approved placement id=" + id);
        return "redirect:/admin/placements";
    }

    @GetMapping("/placements/new")
    public String newPlacement(Model model) {
        requireBean(companyRepository, "CompanyRepository");
        model.addAttribute("students", userRepository.findByRole("student"));
        model.addAttribute("supervisors", userRepository.findByRole("industry"));
        model.addAttribute("companies", companyRepository.findAll());
        return "admin-placement-form";
    }

    @PostMapping("/placements")
    public String createPlacement(@RequestParam Long studentId,
                                  @RequestParam Long supervisorUserId,
                                  @RequestParam Long companyId,
                                  @RequestParam(required = false)
                                  @org.springframework.format.annotation.DateTimeFormat(
                                          iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                  LocalDate startDate,
                                  @RequestParam(required = false)
                                  @org.springframework.format.annotation.DateTimeFormat(
                                          iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                  LocalDate endDate) {
        requireBean(placementRepository, "PlacementRepository");
        Placement p = new Placement();
        p.setStudentId(studentId);
        p.setSupervisorUserId(supervisorUserId);
        p.setCompanyId(companyId);
        p.setStartDate(startDate);
        p.setEndDate(endDate);
        p.setStatus(PlacementStatus.AWAITING_ADMIN);
        placementRepository.save(p);
        logAction("CREATE_PLACEMENT", "Created placement for studentId=" + studentId);
        return "redirect:/admin/placements?status=AWAITING_ADMIN";
    }

    // ============================
    // Logbooks
    // ============================
    @GetMapping("/logbooks")
    public String logbooks(@RequestParam(value="status", required=false) ReviewStatus status,
                           @RequestParam(value="studentId", required=false) Long studentId,
                           @RequestParam(value="page", defaultValue="0") int page,
                           @RequestParam(value="size", defaultValue="10") int size,
                           Model model) {
        requireBean(logbookEntryRepository, "LogbookEntryRepository");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<LogbookEntry> data;
        if (studentId != null) {
            data = logbookEntryRepository.findByStudentId(studentId, pageable);
        } else if (status != null) {
            data = logbookEntryRepository.findByStatus(status, pageable);
        } else {
            data = logbookEntryRepository.findAll(pageable);
        }

        // 🔹 build studentById for Admin table
        java.util.Set<Long> studentIds = new java.util.HashSet<>();
        for (LogbookEntry e : data.getContent()) {
            if (e.getStudentId() != null) {
                studentIds.add(e.getStudentId());
            }
        }
        java.util.Map<Long, User> studentById = new java.util.HashMap<>();
        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds)
                    .forEach(u -> studentById.put(u.getId(), u));
        }

        model.addAttribute("logbooks", data);
        model.addAttribute("studentById", studentById);
        model.addAttribute("status", status);
        model.addAttribute("studentId", studentId);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        return "redirect:/admin/logbooks/students";
    }


    @GetMapping("/logbooks/export")
    public void exportLogbooksCsv(@RequestParam(value="status", required=false) ReviewStatus status,
                                  @RequestParam(value="studentId", required=false) Long studentId,
                                  HttpServletResponse resp) throws IOException {
        requireBean(logbookEntryRepository, "LogbookEntryRepository");

        List<LogbookEntry> rows;
        if (studentId != null) {
            rows = logbookEntryRepository.findByStudentId(
                    studentId, PageRequest.of(0, 10000)).getContent();
        } else if (status != null) {
            rows = logbookEntryRepository.findByStatus(
                    status, PageRequest.of(0, 10000)).getContent();
        } else {
            rows = logbookEntryRepository.findAll();
        }

        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"logbooks.csv\"");
        var out = resp.getWriter();
        out.println("ID,StudentId,WeekStart,LecturerId,CreatedAt,Status,Endorsed,EndorsedByLecturer,ReviewedBy,ReviewedAt,Hours,MainTask,Skills,Challenges,Result,PhotoPath,InternalNote");
        for (var e : rows) {
            out.printf("%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    e.getId(),
                    e.getStudentId(),
                    e.getWeekStartDate(),
                    e.getLecturerId(),
                    e.getCreatedAt(),
                    e.getStatus(),
                    e.isEndorsed(),
                    e.isEndorsedByLecturer(),
                    e.getReviewedBy() == null ? "" : e.getReviewedBy(),
                    e.getReviewedAt(),
                    safe(e.getMainTask()),
                    safe(e.getSkills()),
                    safe(e.getChallenges()),
                    safe(e.getResult()),
                    e.getPhotoPath() == null ? "" : e.getPhotoPath(),
                    safe(e.getInternalNote())
            );
        }
        out.flush();
        logAction("EXPORT_LOGBOOKS", "Export logbooks status="+status+", studentId="+studentId);
    }

    @GetMapping("/logbooks/{id}")
    public String viewLogbookAdmin(@PathVariable Long id, Model model) {
        requireBean(logbookEntryRepository, "LogbookEntryRepository");

        LogbookEntry entry = logbookEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Logbook not found: " + id));

        User student = null;
        if (entry.getStudentId() != null) {
            student = userRepository.findById(entry.getStudentId()).orElse(null);
        }

        model.addAttribute("entry", entry);
        model.addAttribute("student", student);  // for name + matric
        return "admin-logbook-view";
    }


    private static String safe(String s) {
        return s == null ? "" : s.replace("\n"," ").replace("\r"," ").replace(",", " ");
    }

    @GetMapping("/logbooks/students")
    public String logbookStudents(@RequestParam(value="session", required=false) String session,
                                  @RequestParam(value="search", required=false) String search,
                                  @RequestParam(value="page", defaultValue="0") int page,
                                  @RequestParam(value="size", defaultValue="10") int size,
                                  Model model) {
        requireBean(userRepository, "UserRepository");
        requireBean(logbookEntryRepository, "LogbookEntryRepository");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        Page<User> studentsPage;

        String q = (search == null) ? "" : search.trim();

        if (session != null && !session.isBlank()) {
            if (!q.isBlank()) {
                studentsPage = userRepository.searchStudentsBySession("student", session, q, pageable);
            } else {
                studentsPage = userRepository.findAllByRoleAndSession("student", session, pageable);
            }
        } else {
            if (!q.isBlank()) {
                studentsPage = userRepository.searchStudents("student", q, pageable);
            } else {
                studentsPage = userRepository.findAllByRole("student", pageable);
            }
        }

        // build summary map for just the current page
        List<Long> studentIds = studentsPage.getContent().stream()
                .map(User::getId)
                .toList();

        java.util.Map<Long, StudentLogbookSummary> summaryByStudentId = new java.util.HashMap<>();
        if (!studentIds.isEmpty()) {
            logbookEntryRepository.summarizeByStudentIds(studentIds)
                    .forEach(s -> summaryByStudentId.put(s.getStudentId(), s));
        }

        model.addAttribute("students", studentsPage);
        model.addAttribute("summaryByStudentId", summaryByStudentId);

        // reuse your session dropdown helper (already exists in your AdminController)
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("selectedSession", session);
        model.addAttribute("search", q);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", studentsPage.getTotalPages());
        model.addAttribute("size", size);

        return "admin-logbooks-students";
    }

    @GetMapping("/logbooks/student/{studentId}")
    public String logbooksByStudent(@PathVariable Long studentId,
                                    @RequestParam(value="status", required=false) ReviewStatus status,
                                    @RequestParam(value="page", defaultValue="0") int page,
                                    @RequestParam(value="size", defaultValue="10") int size,
                                    Model model) {
        requireBean(logbookEntryRepository, "LogbookEntryRepository");
        requireBean(userRepository, "UserRepository");

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "weekStartDate"));

        Page<LogbookEntry> data = (status == null)
                ? logbookEntryRepository.findByStudentId(studentId, pageable)
                : logbookEntryRepository.findByStudentIdAndStatus(studentId, status, pageable);

        model.addAttribute("student", student);
        model.addAttribute("logbooks", data);
        model.addAttribute("status", status);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        model.addAttribute("size", size);

        return "admin-logbooks-student";
    }



    // ============================
    // Company Info (student submissions)
    // ============================
    @GetMapping("/company-info")
    public String listCompanyInfo(@RequestParam(value="status", required=false) String statusValue,
                                  @RequestParam(value="page", defaultValue="0") int page,
                                  @RequestParam(value="size", defaultValue="10") int size,
                                  Model model) {
        CompanyInfoStatus status = null;
        if (statusValue != null && !statusValue.isBlank()) {
            try {
                status = CompanyInfoStatus.valueOf(statusValue);
            } catch (IllegalArgumentException ignored) {
                // invalid status → treat as "all"
            }
        }

        Pageable p = PageRequest.of(page, size, Sort.by("id").descending());
        Page<CompanyInfo> data = (status == null)
                ? companyInfoRepository.findAll(p)
                : companyInfoRepository.findByStatus(status, p);

        var ids = data.stream()
                .map(CompanyInfo::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        var users = userRepository.findAllById(ids);

        // ✅ Map <User.id, User> so Thymeleaf can access name + matric
        java.util.Map<Long, User> userById = new java.util.HashMap<>();
        for (User u : users) {
            userById.put(u.getId(), u);
        }

        model.addAttribute("infos", data);
        model.addAttribute("userById", userById);
        model.addAttribute("status", status);
        model.addAttribute("statusValue", statusValue);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        return "admin-company-info";
    }



    // shared helper for the process form
    private String loadCompanyInfoProcessForm(Long id, Model model) {
        CompanyInfo info = companyInfoRepository.findById(id).orElseThrow();

        List<Company> companies = (companyRepository == null)
                ? List.of()
                : companyRepository.findAll(Sort.by("name").ascending());

        List<User> supervisors = userRepository.findByRole("industry");

        Placement existingPlacement = null;
        if (placementRepository != null) {
            existingPlacement = placementRepository.findFirstByCompanyInfoId(info.getId()).orElse(null);
        }

        // 🔹 NEW: resolve student by info.studentId (this is User.id)
        User student = null;
        if (info.getStudentId() != null) {
            student = userRepository.findById(info.getStudentId()).orElse(null);
        }

        model.addAttribute("info", info);
        model.addAttribute("student", student);           // 🔹 add to model
        model.addAttribute("companies", companies);
        model.addAttribute("supervisors", supervisors);
        model.addAttribute("placement", existingPlacement);
        model.addAttribute("sectorOptions", CompanySector.values());
        return "admin-company-info-process";
    }


    // Path-variable version (used by POST redirect)
    @GetMapping("/company-info/{id}/process")
    public String processCompanyInfoFormPath(@PathVariable Long id, Model model) {
        return loadCompanyInfoProcessForm(id, model);
    }

    // Query-param version (used by button)
    @GetMapping("/company-info/process")
    public String processCompanyInfoFormParam(@RequestParam("id") Long id, Model model) {
        return loadCompanyInfoProcessForm(id, model);
    }

    @PostMapping("/company-info/{id}/process")
    @Transactional
    public String processCompanyInfo(@PathVariable Long id,
                                     @RequestParam String companyMode,
                                     @RequestParam(required = false) Long existingCompanyId,
                                     @RequestParam(required = false) String newCompanyName,
                                     @RequestParam(required = false) String newCompanyAddress,
                                     @RequestParam(required = false) String sector,
                                     @RequestParam(required = false) String supervisorMode,
                                     @RequestParam(required = false) Long existingSupervisorId,
                                     @RequestParam(required = false) String supervisorName,
                                     @RequestParam(required = false) String supervisorUsername,
                                     @RequestParam(required = false) String supervisorPassword,
                                     @RequestParam(required = false) String supervisorPhone,
                                     @RequestParam(required = false) String placementNotes,
                                     @RequestParam(required = false) String session,
                                     @RequestParam(required = false, defaultValue = "false") boolean publicListing,
                                     RedirectAttributes redirectAttributes) {

        CompanyInfo info = companyInfoRepository.findById(id).orElseThrow();

        //  Block re-processing if already decided
        if (info.getStatus() != CompanyInfoStatus.PENDING) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This submission is already " + info.getStatus() + " and cannot be processed again.");
            return "redirect:/admin/company-info/process?id=" + id;
        }


        Long companyId;
        if ("existing".equalsIgnoreCase(companyMode)) {
            if (existingCompanyId == null) {
                throw new IllegalArgumentException("Existing company must be selected.");
            }
            companyId = existingCompanyId;
        } else {
            requireBean(companyRepository, "CompanyRepository");
            Company company;
            if (info.getLinkedCompanyId() != null) {
                company = companyRepository.findById(info.getLinkedCompanyId())
                        .orElse(new Company());
            } else {
                company = new Company();
            }

            String name = (newCompanyName != null && !newCompanyName.isBlank())
                    ? newCompanyName.trim()
                    : info.getCompanyName();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Company name is required.");
            }

            company.setName(name);
            company.setAddress((newCompanyAddress != null && !newCompanyAddress.isBlank())
                    ? newCompanyAddress.trim()
                    : info.getAddress());
            if (sector != null && !sector.isBlank()) {
                company.setSector(sector.trim());
            }
            companyRepository.save(company);
            companyId = company.getId();
        }

        Long supervisorUserId = null;
        if ("existing".equalsIgnoreCase(supervisorMode)) {
            if (existingSupervisorId == null) {
                throw new IllegalArgumentException("Existing supervisor must be selected.");
            }
            supervisorUserId = existingSupervisorId;
        } else if ("new".equalsIgnoreCase(supervisorMode)) {
            String username = (supervisorUsername != null && !supervisorUsername.isBlank())
                    ? supervisorUsername.trim().toLowerCase()
                    : (info.getSupervisorEmail() != null ? info.getSupervisorEmail().trim().toLowerCase() : null);
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Supervisor username/email is required for new account.");
            }

            if (userRepository.findByUsername(username).isPresent()) {
                throw new IllegalStateException("Username already exists: " + username);
            }

            String rawPassword = (supervisorPassword != null && !supervisorPassword.isBlank())
                    ? supervisorPassword
                    : generateTempPassword();

            User supervisor = new User();
            supervisor.setUsername(username);
            supervisor.setPassword(encode(rawPassword));
            supervisor.setRole("industry");
            supervisor.setName((supervisorName != null && !supervisorName.isBlank())
                    ? supervisorName.trim()
                    : info.getSupervisorName());
            supervisor.setCompany(info.getCompanyName());
            supervisor.setEnabled(true);
            supervisor.setAccessStart(LocalDate.now());
            supervisor.setAccessEnd(LocalDate.now().plus(1, ChronoUnit.YEARS));
            userRepository.save(supervisor);
            supervisorUserId = supervisor.getId();

            redirectAttributes.addFlashAttribute("newSupervisorUsername", username);
            redirectAttributes.addFlashAttribute("newSupervisorPassword", rawPassword);
        } else if (supervisorMode == null || supervisorMode.isBlank()) {
            supervisorUserId = null;
        } else {
            throw new IllegalArgumentException("Unsupported supervisor mode: " + supervisorMode);
        }

        if (placementRepository == null) {
            throw new IllegalStateException("PlacementRepository is not wired yet.");
        }

        Placement placement = placementRepository.findFirstByCompanyInfoId(info.getId())
                .orElseGet(Placement::new);
        boolean isNewPlacement = placement.getId() == null;

        placement.setStudentId(info.getStudentId());
        placement.setCompanyId(companyId);
        placement.setCompanyInfoId(info.getId());
        placement.setSupervisorUserId(supervisorUserId);
        placement.setStartDate(info.getInternshipStartDate());
        placement.setEndDate(info.getInternshipEndDate());
        placement.setStatus(PlacementStatus.AWAITING_SUPERVISOR);
        if (placementNotes != null && !placementNotes.isBlank()) {
            placement.setAdminNotes(placementNotes.trim());
        }
        placementRepository.save(placement);

        info.setLinkedCompanyId(companyId);
        info.setStatus(CompanyInfoStatus.VERIFIED);
        info.setIsPublicListing(publicListing ? Boolean.TRUE : Boolean.FALSE);
        if (session != null && !session.isBlank()) {
            info.setSession(session.trim());
        }
        if (supervisorName != null && !supervisorName.isBlank()) {
            info.setSupervisorName(supervisorName.trim());
        }
        if (supervisorUsername != null && !supervisorUsername.isBlank()) {
            info.setSupervisorEmail(supervisorUsername.trim());
        }
        if (supervisorPhone != null && !supervisorPhone.isBlank()) {
            info.setSupervisorPhone(supervisorPhone.trim());
        }
        if (placementNotes != null && !placementNotes.isBlank()) {
            placement.setAdminNotes(placementNotes.trim());
        }
        if (sector != null && !sector.isBlank()) {
            try {
                info.setSector(CompanySector.valueOf(sector.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        companyInfoRepository.save(info);

        logAction("PROCESS_COMPANY_INFO",
                (isNewPlacement ? "Created" : "Updated") + " placement " + placement.getId()
                        + " for CompanyInfo#" + id + " (company=" + companyId
                        + ", supervisor=" + (supervisorUserId != null ? supervisorUserId : "none") + ")");

        redirectAttributes.addFlashAttribute("successMessage",
                "Placement " + placement.getId() + " is now awaiting supervisor verification.");

        return "redirect:/admin/company-info/" + id + "/process";
    }

    @PostMapping("/company-info/{id}/verify")
    public String verifyCompanyInfo(@PathVariable Long id) {
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();
        ci.setStatus(CompanyInfoStatus.VERIFIED);
        companyInfoRepository.save(ci);
        logAction("VERIFY_COMPANY_INFO", "Verified CompanyInfo id=" + id);
        return "redirect:/admin/company-info?status=VERIFIED";
    }

    @PostMapping("/company-info/{id}/reject")
    public String rejectCompanyInfo(@PathVariable Long id) {
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();
        ci.setStatus(CompanyInfoStatus.REJECTED);
        companyInfoRepository.save(ci);
        logAction("REJECT_COMPANY_INFO", "Rejected CompanyInfo id=" + id);
        return "redirect:/admin/company-info?status=REJECTED";
    }

    @PostMapping("/company-info/{id}/promote")
    public String promoteCompanyInfo(@PathVariable Long id) {
        requireBean(companyRepository, "CompanyRepository");
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();

        Company company = companyRepository.findByNameIgnoreCase(ci.getCompanyName())
                .orElseGet(Company::new);

        company.setName(ci.getCompanyName());
        company.setAddress(ci.getAddress());
        companyRepository.save(company);

        ci.setLinkedCompanyId(company.getId());
        if (ci.getStatus() == CompanyInfoStatus.PENDING) {
            ci.setStatus(CompanyInfoStatus.VERIFIED);
        }
        companyInfoRepository.save(ci);

        logAction("PROMOTE_COMPANY", "Promoted '" + company.getName() + "' from CompanyInfo#" + id);
        return "redirect:/admin/company-info?status=VERIFIED";
    }

    @PostMapping("/company-info/{id}/create-placement")
    public String createPlacementFromInfo(@PathVariable Long id) {
        return "redirect:/admin/company-info/" + id + "/process";
    }

    @PostMapping("/admin/placements/{id}/delete")
    public String deletePlacement(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {

        // if you want to be safe and avoid error when id not found
        if (placementRepository.existsById(id)) {
            placementRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Placement deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Placement not found.");
        }

        // keep current filters/page simple: return to main list
        return "redirect:/admin/placements";
    }

    // ============================
    // Company MASTER (separate list)
    // ============================
    // ============================
    // Company MASTER (separate list)
    // ============================
    @GetMapping("/company-master")
    public String companyMaster(@RequestParam(value = "q", required = false) String q,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                @RequestParam(value = "size", defaultValue = "10") int size,
                                Model model) {
        requireBean(companyRepository, "CompanyRepository");

        Pageable p = PageRequest.of(page, size, Sort.by("name").ascending());

        Page<Company> data = (q == null || q.isBlank())
                ? companyRepository.findAll(p)
                : companyRepository.findByNameContainingIgnoreCase(q.trim(), p);

        model.addAttribute("companies", data);
        model.addAttribute("sectorOptions", CompanySector.values());
        model.addAttribute("q", q);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        model.addAttribute("size", size);
        return "admin-company-master";
    }

    @PostMapping("/company-master")
    public String upsertCompany(@RequestParam(required = false) Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String address,
                                @RequestParam(required = false) String sector,
                                @RequestParam(required = false) String defaultJobScope,
                                @RequestParam(required = false) BigDecimal typicalAllowance,
                                @RequestParam(required = false, defaultValue = "false") boolean accommodation,
                                @RequestParam(required = false) String contactName,
                                @RequestParam(required = false) String contactEmail,
                                @RequestParam(required = false) String contactPhone,
                                @RequestParam(required = false) String website,
                                @RequestParam(required = false) String notes) {
        requireBean(companyRepository, "CompanyRepository");

        Company c = (id == null)
                ? new Company()
                : companyRepository.findById(id).orElse(new Company());

        c.setName(name);
        c.setAddress(address);
        c.setSector(sector);
        c.setDefaultJobScope(defaultJobScope);
        c.setTypicalAllowance(typicalAllowance);
        c.setAccommodation(accommodation);

        c.setContactName(contactName);
        c.setContactEmail(contactEmail);
        c.setContactPhone(contactPhone);
        c.setWebsite(website);
        c.setNotes(notes);

        companyRepository.save(c);
        logAction("UPSERT_COMPANY_MASTER", (id == null ? "Created" : "Updated") + " company: " + name);
        return "redirect:/admin/company-master";
    }

    @PostMapping("/company-master/{id}/delete")
    public String deleteCompany(@PathVariable Long id, RedirectAttributes ra) {
        requireBean(companyRepository, "CompanyRepository");
        try {
            companyRepository.deleteById(id);
            logAction("DELETE_COMPANY_MASTER", "Deleted company id=" + id);
            ra.addFlashAttribute("toast", "Company deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast",
                    "Unable to delete company. It may be referenced by placements or other data.");
        }
        return "redirect:/admin/company-master";
    }

    // Detailed view of a single company + supervisor management
    @GetMapping("/company-master/{id}")
    public String companyDetail(@PathVariable Long id, Model model) {
        requireBean(companyRepository, "CompanyRepository");
        Company company = companyRepository.findById(id).orElseThrow();

        // All industry users
        List<User> allIndustry = userRepository.findByRole("industry");

        String companyName = company.getName() != null ? company.getName().trim() : null;
        java.util.List<User> supervisors = new java.util.ArrayList<>();

        for (User u : allIndustry) {
            if (companyName != null &&
                    u.getCompany() != null &&
                    companyName.equalsIgnoreCase(u.getCompany().trim())) {
                supervisors.add(u);
            }
        }

        model.addAttribute("company", company);
        model.addAttribute("supervisors", supervisors);
        // list of all industry users – used in "link existing" dropdown
        model.addAttribute("availableSupervisors", allIndustry);
        model.addAttribute("sectorOptions", CompanySector.values());
        return "admin-company-detail";
    }

    // Link an existing industry user to this company
    @PostMapping("/company-master/{companyId}/link-supervisor")
    public String linkSupervisorToCompany(@PathVariable Long companyId,
                                          @RequestParam Long supervisorId,
                                          RedirectAttributes ra) {
        requireBean(companyRepository, "CompanyRepository");
        Company company = companyRepository.findById(companyId).orElseThrow();
        User sup = userRepository.findById(supervisorId).orElseThrow();

        sup.setRole("industry"); // make sure it is an industry user
        sup.setCompany(company.getName());
        userRepository.save(sup);

        logAction("LINK_INDUSTRY_SUPERVISOR",
                "Linked userId=" + supervisorId + " to companyId=" + companyId);
        ra.addFlashAttribute("toast", "Supervisor linked to company.");
        return "redirect:/admin/company-master/" + companyId;
    }

    // Create a brand new industry supervisor for this company
    @PostMapping("/company-master/{companyId}/create-supervisor")
    public String createSupervisorForCompany(@PathVariable Long companyId,
                                             @RequestParam String name,
                                             @RequestParam String username,
                                             @RequestParam(required = false) String password,
                                             RedirectAttributes ra) {
        requireBean(companyRepository, "CompanyRepository");
        Company company = companyRepository.findById(companyId).orElseThrow();

        String normalized = username.trim().toLowerCase();
        if (userRepository.findByUsername(normalized).isPresent()) {
            ra.addFlashAttribute("toast", "Username already exists: " + normalized);
            return "redirect:/admin/company-master/" + companyId;
        }

        String rawPassword = (password != null && !password.isBlank())
                ? password
                : generateTempPassword();

        User sup = new User();
        sup.setUsername(normalized);
        sup.setPassword(encode(rawPassword));
        sup.setRole("industry");
        sup.setName(name);
        sup.setCompany(company.getName());
        sup.setEnabled(true);
        sup.setAccessStart(LocalDate.now());
        sup.setAccessEnd(LocalDate.now().plusYears(1));
        userRepository.save(sup);

        logAction("CREATE_INDUSTRY_SUPERVISOR",
                "Created industry supervisor " + normalized + " for company " + company.getName());

        ra.addFlashAttribute("showCred", true);
        ra.addFlashAttribute("newSupervisorUsername", normalized);
        ra.addFlashAttribute("newSupervisorPassword", rawPassword);
        ra.addFlashAttribute("toast", "New industry supervisor created and linked.");
        return "redirect:/admin/company-master/" + companyId;
    }

    // Update supervisor basic details (name + username/email)
    @PostMapping("/company-master/{companyId}/supervisors/{userId}/update")
    public String updateSupervisorForCompany(@PathVariable Long companyId,
                                             @PathVariable Long userId,
                                             @RequestParam String name,
                                             @RequestParam String username,
                                             RedirectAttributes ra) {
        User sup = userRepository.findById(userId).orElseThrow();

        String newUsername = username.trim().toLowerCase();

        if (!newUsername.equalsIgnoreCase(sup.getUsername())) {
            if (userRepository.findByUsername(newUsername).isPresent()) {
                ra.addFlashAttribute("toast", "Username already in use: " + newUsername);
                return "redirect:/admin/company-master/" + companyId;
            }
            sup.setUsername(newUsername);
        }

        sup.setName(name);
        userRepository.save(sup);

        logAction("UPDATE_INDUSTRY_SUPERVISOR",
                "Updated industry supervisor id=" + userId);
        ra.addFlashAttribute("toast", "Supervisor details updated.");
        return "redirect:/admin/company-master/" + companyId;
    }

    // Unlink supervisor from this company (keeps their account)
    @PostMapping("/company-master/{companyId}/supervisors/{userId}/unlink")
    public String unlinkSupervisorFromCompany(@PathVariable Long companyId,
                                              @PathVariable Long userId,
                                              RedirectAttributes ra) {
        requireBean(companyRepository, "CompanyRepository");
        Company company = companyRepository.findById(companyId).orElseThrow();
        User sup = userRepository.findById(userId).orElseThrow();

        if (sup.getCompany() != null &&
                company.getName() != null &&
                company.getName().equalsIgnoreCase(sup.getCompany())) {
            sup.setCompany(null);
            userRepository.save(sup);

            logAction("UNLINK_INDUSTRY_SUPERVISOR",
                    "Unlinked userId=" + userId + " from companyId=" + companyId);
            ra.addFlashAttribute("toast", "Supervisor unlinked from company.");
        } else {
            ra.addFlashAttribute("toast", "Supervisor is not currently linked to this company.");
        }
        return "redirect:/admin/company-master/" + companyId;
    }

    // Completely delete supervisor account (danger – may fail if referenced)
    @PostMapping("/company-master/{companyId}/supervisors/{userId}/delete")
    public String deleteSupervisorAccount(@PathVariable Long companyId,
                                          @PathVariable Long userId,
                                          RedirectAttributes ra) {
        try {
            userRepository.deleteById(userId);
            logAction("DELETE_INDUSTRY_SUPERVISOR",
                    "Deleted industry supervisor id=" + userId);
            ra.addFlashAttribute("toast", "Supervisor user account deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast",
                    "Unable to delete supervisor – the account may be used in placements or logbooks.");
        }
        return "redirect:/admin/company-master/" + companyId;
    }

    @GetMapping("/industry-supervisors")
    public String listIndustrySupervisors(Model model) {

        // All users whose role is "INDUSTRY"
        // (If your role string is different, e.g. "INDUSTRY_SUPERVISOR",
        // change it here.)
        List<User> supervisors = userRepository.findByRole("industry");

        model.addAttribute("supervisors", supervisors);
        return "admin-industry-supervisors";
    }

    @GetMapping("/industry-supervisors/add")
    public String addIndustrySupervisorForm(Model model) {
        model.addAttribute("supervisor", new User());

        model.addAttribute("companies", companyRepository.findAll());

        return "admin-industry-supervisor-form";
    }

    @PostMapping("/industry-supervisors/save")
    public String saveIndustrySupervisor(
            @ModelAttribute User supervisor,
            @RequestParam(value = "companyId", required = false) Long companyId
    ) {

        supervisor.setRole("industry");

        if (supervisor.getPassword() != null && !supervisor.getPassword().isBlank()) {
            supervisor.setPassword(encode(supervisor.getPassword()));
        }

        if (supervisor.getEnabled() == null) supervisor.setEnabled(true);
        if (supervisor.getAccessStart() == null) supervisor.setAccessStart(LocalDate.now());
        if (supervisor.getAccessEnd() == null) supervisor.setAccessEnd(LocalDate.now().plusMonths(12));

        // ✅ Link selected company (from masterlist) into User.company (string)
        if (companyId != null && companyRepository != null) {
            Company c = companyRepository.findById(companyId).orElse(null);
            if (c != null) {
                supervisor.setCompany(c.getName());   // <-- use your existing String field
            }
        }

        userRepository.save(supervisor);
        return "redirect:/admin/industry-supervisors";
    }


    @GetMapping("/industry-supervisors/edit/{id}")
    public String editIndustrySupervisor(@PathVariable Long id, Model model) {
        model.addAttribute("supervisor", userRepository.findById(id).orElse(null));
        return "admin-industry-supervisor-form";
    }

    @PostMapping("/industry-supervisors/delete/{id}")
    public String deleteIndustrySupervisor(@PathVariable Long id) {
        userRepository.deleteById(id);
        return "redirect:/admin/industry-supervisors";
    }







    // ============================
    // Grades export
    // ============================
    @GetMapping("/evaluations/export")
    public void exportEvaluations(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "session", required = false) String session,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpSession httpSession,
            HttpServletResponse response
    ) throws Exception {

        // ✅ admin guard (same style as you wrote)
        Object auth = httpSession.getAttribute("auth");
        if (!(auth instanceof Map<?, ?> m) || m.get("role") == null
                || !"admin".equalsIgnoreCase(String.valueOf(m.get("role")))) {
            response.sendRedirect("/login");
            return;
        }

        // ✅ Use same default as overview (but for export we usually want ALL filtered)
        // If you want "export current page only", set exportAll=false and use provided page/size.
        boolean exportAll = true;

        int exportPage = (page == null ? 0 : page);
        int exportSize = (size == null ? 25 : size);

        Pageable pageable;
        if (exportAll) {
            // big size to fetch all filtered students (safe enough for typical class size)
            pageable = PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "name"));
        } else {
            pageable = PageRequest.of(exportPage, exportSize, Sort.by(Sort.Direction.ASC, "name"));
        }

        Page<User> students;

        if ((search != null && !search.isBlank()) || (session != null && !session.isBlank())) {
            students = userRepository.searchStudentsWithSession(
                    "student",
                    (search != null && !search.isBlank()) ? search.trim() : null,
                    session,
                    pageable
            );
        } else {
            students = userRepository.findAllByRole("student", pageable);
        }

        // Build latest assessment map (same logic as manageEvaluations)
        Map<Long, StudentAssessment> assessmentByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            studentAssessmentRepository.findTopByStudentUserIdOrderByIdDesc(s.getId())
                    .ifPresent(sa -> {
                        if (sa.getGrade() == null || sa.getGrade().isBlank()) {
                            sa.setGrade(computeGrade(sa));
                            studentAssessmentRepository.save(sa);
                        }
                        assessmentByStudentId.put(s.getId(), sa);
                    });
        }

        // ---- Write Excel (inline POI, no extra service needed) ----
        String fileName = "evaluations.xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Evaluations");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {"No", "Student", "Matric", "Session", "VL (60)", "Industry (40)", "Total", "Grade"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            int no = 1;

            for (User stu : students.getContent()) {
                StudentAssessment sa = assessmentByStudentId.get(stu.getId());

                String sessionText = (sa != null && sa.getSession() != null) ? sa.getSession() : (stu.getSession() != null ? stu.getSession() : "");
                String studentName = (stu.getName() != null && !stu.getName().isBlank()) ? stu.getName() : stu.getUsername();
                String matric = (stu.getStudentId() != null) ? stu.getStudentId() : "";

                // VL 60
                BigDecimal vl = BigDecimal.ZERO;
                BigDecimal ind = BigDecimal.ZERO;

                if (sa != null) {
                    vl = nz(sa.getVlEvaluation10())
                            .add(nz(sa.getVlAttendance5()))
                            .add(nz(sa.getVlLogbook5()))
                            .add(nz(sa.getVlFinalReport40()));

                    ind = nz(sa.getIsSkills20())
                            .add(nz(sa.getIsCommunication10()))
                            .add(nz(sa.getIsTeamwork10()));
                }

                boolean vlNoData = (sa == null) || vl.compareTo(BigDecimal.ZERO) == 0;
                boolean indNoData = (sa == null) || ind.compareTo(BigDecimal.ZERO) == 0;

                BigDecimal total = vl.add(ind);

                String vlText = vlNoData ? "No data" : fmt2(vl) + " / 60";
                String indText = indNoData ? "No data" : fmt2(ind) + " / 40";
                String totalText = (vlNoData && indNoData) ? "-" : fmt2(total);
                String grade = (sa != null && sa.getGrade() != null && !sa.getGrade().isBlank()) ? sa.getGrade() : "-";

                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(no++);
                r.createCell(1).setCellValue(studentName);
                r.createCell(2).setCellValue(matric);
                r.createCell(3).setCellValue(sessionText);
                r.createCell(4).setCellValue(vlText);
                r.createCell(5).setCellValue(indText);
                r.createCell(6).setCellValue(totalText);
                r.createCell(7).setCellValue(grade);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(response.getOutputStream());
            response.getOutputStream().flush();
        }

        logAction("EXPORT_EVALUATIONS",
                "Export evaluations search=" + (search == null ? "" : search)
                        + ", session=" + (session == null ? "" : session)
                        + ", exportAll=" + exportAll);
    }
    // ============================
// Final Reports (Admin)
// ============================
    @GetMapping("/final-reports")
    public String finalReports(@RequestParam(value = "search", required = false) String search,
                               @RequestParam(value = "session", required = false) String session,
                               @RequestParam(value = "department", required = false) String department,
                               @RequestParam(value = "page", defaultValue = "0") int page,
                               @RequestParam(value = "size", defaultValue = "25") int size,
                               Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        // ✅ normalize empty -> null (fix search bug)
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String sessionTerm = (session != null && !session.isBlank()) ? session.trim() : null;

        Department deptEnum = null;
        if (department != null && !department.isBlank()) {
            try {
                deptEnum = Department.valueOf(department.trim());
            } catch (Exception ignored) {}
        }

        // ✅ use same search method you used for evaluations (stable)
        Page<User> students = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                searchTerm,
                sessionTerm,
                deptEnum,
                pageable
        );

        // Pull latest assessment for report marks
        Map<Long, StudentAssessment> assessmentByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            studentAssessmentRepository.findTopByStudentUserIdOrderByIdDesc(s.getId())
                    .ifPresent(sa -> assessmentByStudentId.put(s.getId(), sa));
        }

        model.addAttribute("students", students);
        model.addAttribute("assessmentByStudentId", assessmentByStudentId);

        model.addAttribute("search", searchTerm);
        model.addAttribute("selectedSession", sessionTerm);

        model.addAttribute("currentPage", page);
        model.addAttribute("size", size);

        List<String> sessions = userRepository.findDistinctStudentSessions();
        model.addAttribute("sessions", sessions);

        // ✅ department filter UI
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("selectedDepartment", deptEnum != null ? deptEnum.name() : "");

        return "admin-final-reports";
    }

    @GetMapping("/final-reports/mark/{studentId}")
    public String adminMarkFinalReport(
            @PathVariable Long studentId,
            @RequestParam(value = "session", required = false) String sessionStr,
            HttpSession session,
            Model model
    ) {
        User admin = (User) session.getAttribute("user");
        if (admin == null) return "redirect:/login";
        if (!"admin".equalsIgnoreCase(admin.getRole())) return "redirect:/login";

        User stu = userRepository.findById(studentId).orElse(null);
        if (stu == null || !"student".equalsIgnoreCase(stu.getRole())) return "redirect:/admin/final-reports";

        String sess = (sessionStr != null && !sessionStr.isBlank())
                ? sessionStr
                : (stu.getSession() == null || stu.getSession().isBlank() ? "DEFAULT" : stu.getSession());

        StudentAssessment sa = studentAssessmentRepository
                .findByStudentUserIdAndSession(stu.getId(), sess)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(stu.getId());
                    x.setSession(sess);
                    return x;
                });

        model.addAttribute("student", stu);
        model.addAttribute("sa", sa);
        model.addAttribute("sessionStr", sess);   // IMPORTANT for hidden field

        return "admin-final-report-mark";
    }


    @PostMapping("/final-reports/mark")
    public String adminSaveFinalReportMark(
            @RequestParam Long studentId,
            @RequestParam String sessionStr,
            @RequestParam BigDecimal written5,
            @RequestParam BigDecimal video5,
            HttpSession session,
            RedirectAttributes ra
    ) {
        User admin = (User) session.getAttribute("user");
        if (admin == null) return "redirect:/login";
        if (!"admin".equalsIgnoreCase(admin.getRole())) return "redirect:/login";

        // clamp [0..5]
        written5 = written5.max(BigDecimal.ZERO).min(new BigDecimal("5"));
        video5   = video5.max(BigDecimal.ZERO).min(new BigDecimal("5"));

        StudentAssessment sa = studentAssessmentRepository
                .findByStudentUserIdAndSession(studentId, sessionStr)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(sessionStr);
                    return x;
                });

        sa.setAdminReportWritten5(written5);
        sa.setAdminReportVideo5(video5);

        studentAssessmentRepository.save(sa);

        ra.addFlashAttribute("toast", "Final report marks saved (Written: " + written5 + "/5, Video: " + video5 + "/5)");
        return "redirect:/admin/final-reports/mark/" + studentId + "?session=" + sessionStr;

    }


    // helper for formatting (put near your other helpers)
    private String fmt2(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ============================
    // Helpers
    // ============================
    private void logAction(String action, String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setUsername(getActorUsername().orElse("admin"));
        auditLog.setDescription(description);
        auditLog.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }

    private String generateTempPassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            int idx = ThreadLocalRandom.current().nextInt(alphabet.length());
            sb.append(alphabet.charAt(idx));
        }
        return sb.toString();
    }

    private java.util.Optional<String> getActorUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getName() != null
                    && !"anonymousUser".equals(auth.getName())) {
                return java.util.Optional.of(auth.getName());
            }
        } catch (Throwable ignored) {}
        return java.util.Optional.of("admin");
    }

    private java.util.List<String> rollingSessions(int pastYears, int futureYears) {
        int now = java.time.Year.now().getValue();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int y = now - pastYears; y <= now + futureYears; y++) {
            String base = y + "/" + (y + 1);
            out.add(base + "-2");
            out.add(base + "-1");
        }
        return out;
    }

    private String encode(String raw) {
        if (passwordEncoder == null || raw == null) return raw;
        return passwordEncoder.encode(raw);
    }

    private void requireBean(Object bean, String name) {
        if (bean == null) throw new IllegalStateException(name + " is not wired yet.");
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String computeGrade(StudentAssessment sa) {
        BigDecimal vl = nz(sa.getVlEvaluation10())
                .add(nz(sa.getVlAttendance5()))
                .add(nz(sa.getVlLogbook5()))
                .add(nz(sa.getVlFinalReport40())); // max 60

        BigDecimal ind = nz(sa.getIsSkills20())
                .add(nz(sa.getIsCommunication10()))
                .add(nz(sa.getIsTeamwork10())); // max 40

        double total = vl.add(ind).doubleValue(); // max 100
        return UpmGradeUtil.gradeFromTotal(total);
    }

    private int safeLikert(Integer v) {
        if (v == null) return 0;
        return Math.max(0, Math.min(5, v));
    }

    /**
     * Official guideline: Visiting Lecturer contributes 40%.
     * We derive it from VisitEvaluation Likert fields (same logic as student dashboard).
     */
    private BigDecimal computeVl40FromVisitEvaluation(VisitEvaluation ve) {
        if (ve == null) return null;

        int ref10 = safeLikert(ve.getReflectionLikert()) * 2;            // /10
        int eng10 = safeLikert(ve.getEngagementLikert()) * 2;            // /10
        int suit5 = safeLikert(ve.getPlacementSuitabilityLikert());      // /5
        int log5  = safeLikert(ve.getLogbookLikert());                   // /5
        int overall10 = safeLikert(ve.getLecturerOverallLikert()) * 2;   // /10

        int sum40 = ref10 + eng10 + suit5 + log5 + overall10;            // /40
        return BigDecimal.valueOf(sum40);
    }


}
