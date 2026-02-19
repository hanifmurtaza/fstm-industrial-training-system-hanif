package com.example.itsystem.controller;

import com.example.itsystem.model.*;
import com.example.itsystem.repository.*;
import com.example.itsystem.service.AdminMetricsService;
import com.example.itsystem.service.GradingService;
import com.example.itsystem.service.StudentAssessmentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpSession;

import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import com.example.itsystem.model.StudentAssessment;
import com.example.itsystem.repository.StudentAssessmentRepository;
import com.example.itsystem.util.UpmGradeUtil;
import com.example.itsystem.model.VisitSchedule;
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

    @Autowired(required = false) private SystemSettingRepository systemSettingRepository;





    // ----- Services -----
    @Autowired(required = false) private AdminMetricsService adminMetricsService;
    @Autowired(required = false) private GradingService gradingService;
    @Autowired private StudentAssessmentService studentAssessmentService;



    // Password encoder
    @Autowired(required = false) private PasswordEncoder passwordEncoder;

    // ============================
    // Dashboard
    // ============================
    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession httpSession) {
        // Sector filter (stored in HTTP session)
        CompanySector sectorFilter = resolveAdminSector(null, httpSession);

        long studentCount;
        if (sectorFilter == null) {
            studentCount = userRepository.countByRole("student");
        } else {
            // Strictly count students that match the active sector filter
            studentCount = companyInfoRepository.countDistinctStudentsBySectorNonRejected(sectorFilter);
        }
        long companyCount   = companyRepository.count();
        long evalCount      = evaluationRepository.count();
        long documentCount  = documentRepository.count();

        model.addAttribute("studentCount", studentCount);
        model.addAttribute("companyCount", companyCount);
        model.addAttribute("evalCount", evalCount);
        model.addAttribute("documentCount", documentCount);

        long pendingCompanyInfo = (sectorFilter == null)
                ? companyInfoRepository.countByStatus(CompanyInfoStatus.PENDING)
                : companyInfoRepository.countByStatusAndSector(CompanyInfoStatus.PENDING, sectorFilter);
        model.addAttribute("pendingCompanyInfo", pendingCompanyInfo);

        if (placementRepository != null) {
            long awaitingApproval = (sectorFilter == null)
                    ? placementRepository.countByStatus(PlacementStatus.AWAITING_ADMIN)
                    : placementRepository.countByStatusAndCompanySector(PlacementStatus.AWAITING_ADMIN, sectorFilter.name());
            model.addAttribute("awaitingApproval", awaitingApproval);
        } else {
            model.addAttribute("awaitingApproval", 0L);
        }

        if (logbookEntryRepository != null) {
            long awaitingSup;
            if (sectorFilter == null) {
                awaitingSup = logbookEntryRepository.countByStatusAndEndorsedFalse(ReviewStatus.PENDING);
            } else {
                List<Long> sectorStudentIds = companyInfoRepository.findDistinctStudentIdsBySectorNonRejected(sectorFilter);
                awaitingSup = (sectorStudentIds == null || sectorStudentIds.isEmpty())
                        ? 0L
                        : logbookEntryRepository.countByStudentIdInAndStatusAndEndorsedFalse(sectorStudentIds, ReviewStatus.PENDING);
            }
            long pendingLecturer = logbookEntryRepository.countAwaitingLecturer();
            model.addAttribute("awaitingSupEndorse", awaitingSup);
            model.addAttribute("pendingLogbooksLecturer", pendingLecturer);
        }

        if (adminMetricsService != null) {
            model.addAttribute("metrics", adminMetricsService.snapshot());
        }

        // Session: Admin-defined current session (used as default filter across admin pages)
        String sessionTerm = resolveAdminSession(null, httpSession);
        model.addAttribute("sessionOptions", sessionOptionsForDropdown(sessionTerm));
        model.addAttribute("currentSession", getConfiguredCurrentSession());
        model.addAttribute("selectedSession", sessionTerm);

        model.addAttribute("sectorOptions", CompanySector.values());
        model.addAttribute("selectedSector", sectorFilter == null ? SECTOR_ALL_SENTINEL : sectorFilter.name());

        return "admin-dashboard";
    }


    @PostMapping("/sector-filter")
    public String setSectorFilter(@RequestParam("sector") String sector,
                                  @RequestParam(value = "redirect", required = false) String redirect,
                                  HttpSession httpSession,
                                  RedirectAttributes ra) {
        CompanySector resolved = resolveAdminSector(sector, httpSession);
        if (resolved == null) {
            ra.addFlashAttribute("toast", "Sector filter cleared (All sectors).");
        } else {
            ra.addFlashAttribute("toast", "Sector filter set to: " + resolved.name().replace('_', ' '));
        }
        logAction("SET_SECTOR_FILTER", "Set sector filter to " + (resolved == null ? "ALL" : resolved.name()));
        if (redirect != null && !redirect.isBlank()) {
            return "redirect:" + redirect;
        }
        return "redirect:/admin/dashboard";
    }


    @PostMapping("/current-session")
    public String setCurrentSession(@RequestParam("session") String session, HttpSession httpSession, RedirectAttributes ra) {
        String norm = normalizeSessionParam(session);
        if (norm == null || norm.isBlank()) {
            ra.addFlashAttribute("toast", "Please select a valid session.");
            return "redirect:/admin/dashboard";
        }
        saveSystemSetting(SETTING_CURRENT_SESSION, norm);
        httpSession.setAttribute(ADMIN_SELECTED_SESSION_ATTR, norm);
        ra.addFlashAttribute("toast", "Current session set to: " + norm);
        logAction("SET_CURRENT_SESSION", "Set current session to " + norm);
        return "redirect:/admin/dashboard";
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
            Model model,
            HttpSession httpSession,
            RedirectAttributes ra
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        // Default to admin-selected / configured current session when session param is not provided
        String sessionTerm = resolveAdminSession(session, httpSession);


        Page<User> students = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                search != null ? search.trim() : null,
                sessionTerm,
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
        model.addAttribute("selectedSession", sessionTerm);
        model.addAttribute("sessionOptions", sessionOptionsForDropdown(sessionTerm));
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", students.getTotalPages());
        model.addAttribute("size", size);

        return "admin-students";
    }


    @GetMapping("/students/add")
    public String addStudentForm(Model model) {
        model.addAttribute("student", new User());
        model.addAttribute("sessionOptions", rollingSessions(3, 5));
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
                                 @RequestParam(required = false) String company,
                                 @RequestParam(required = false) Department department,
                                 RedirectAttributes ra) {

        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setStudentId(studentId);
        user.setSession(session);

        // Optional company string (legacy display only)
        if (company != null && !company.isBlank()) {
            user.setCompany(company);
        } else {
            user.setCompany(null);
        }

        user.setDepartment(department);
        user.setRole("student");

        if (password == null || password.isBlank()) {
            ra.addFlashAttribute("toast", "Password cannot be blank.");
            return "redirect:/admin/students/add";
        }
        user.setPassword(encode(password));

        if (user.getAccessStart() == null) user.setAccessStart(LocalDate.now());
        if (user.getAccessEnd() == null) user.setAccessEnd(LocalDate.now().plusMonths(12));
        user.setEnabled(true);

        try {
            userRepository.save(user);
            ra.addFlashAttribute("toast", "Student added.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Unable to add student. Username may already exist.");
            return "redirect:/admin/students/add";
        }

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
        model.addAttribute("sessionOptions", rollingSessions(3, 5));
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("lecturers", userRepository.findByRole("teacher"));
        return "admin-student-form";
    }

    @PostMapping("/students/save")
    public String saveStudent(@ModelAttribute("student") User incoming, RedirectAttributes ra) {
        incoming.setRole("student");

        if (incoming.getId() != null) {
            User current = userRepository.findById(incoming.getId()).orElse(null);
            if (current == null) {
                ra.addFlashAttribute("toast", "Student not found.");
                return "redirect:/admin/students";
            }

            // keep password unless a new one is typed
            if (incoming.getPassword() == null || incoming.getPassword().isBlank()) {
                incoming.setPassword(current.getPassword());
            } else {
                incoming.setPassword(encode(incoming.getPassword()));
            }

            // keep fields not present in the edit form
            if (incoming.getEnabled() == null) incoming.setEnabled(current.getEnabled());
            if (incoming.getAccessStart() == null) incoming.setAccessStart(current.getAccessStart());
            if (incoming.getAccessEnd() == null) incoming.setAccessEnd(current.getAccessEnd());
            if (incoming.getRemarks() == null) incoming.setRemarks(current.getRemarks());
        } else {
            // NEW student – password required
            if (incoming.getPassword() == null || incoming.getPassword().isBlank()) {
                ra.addFlashAttribute("toast", "Password cannot be blank.");
                return "redirect:/admin/students/add";
            }
            incoming.setPassword(encode(incoming.getPassword()));
            if (incoming.getEnabled() == null) incoming.setEnabled(true);
            if (incoming.getAccessStart() == null) incoming.setAccessStart(LocalDate.now());
            if (incoming.getAccessEnd() == null) incoming.setAccessEnd(LocalDate.now().plusMonths(12));
        }

        try {
            userRepository.save(incoming);
            ra.addFlashAttribute("toast", "Student saved.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Unable to save student. Username may already exist.");
        }

        return "redirect:/admin/students";
    }

    @PostMapping("/students/delete/{id}")
    public String deleteStudent(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userRepository.deleteById(id);
            ra.addFlashAttribute("toast", "Student deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Unable to delete. Student may be referenced by placements/logbooks/evaluations.");
        }
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
            return URLEncoder.encode(v, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return v;
        }
    }


    @GetMapping("/students/import")
    public String importForm(Model model) {
        model.addAttribute("sessionOptions", rollingSessions(3, 5));
        model.addAttribute("departmentOptions", Department.values());
        return "admin-students-import";
    }

    @PostMapping("/students/import/preview")
    public String importPreview(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                @RequestParam("defaultSession") String defaultSession,
                                @RequestParam("defaultDepartment") Department defaultDepartment,
                                Model model,
                                HttpSession session) throws IOException {

        var preview = bulkImportService.preview(file, defaultSession);

        session.setAttribute("bulkImportPreview", preview);
        session.setAttribute("bulkImportDepartment", defaultDepartment);

        model.addAttribute("preview", preview);
        model.addAttribute("defaultSession", defaultSession);
        model.addAttribute("defaultDepartment", defaultDepartment);

        return "admin-students-import-preview";
    }


    @PostMapping("/students/import/commit")
    public String importCommit(HttpSession session,
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
// Evaluations (Admin overview: VL + Industry + Report + Logbook + Total)
// ============================
    @GetMapping("/evaluations")
    public String manageEvaluations(@RequestParam(value = "search", required = false) String search,
                                    @RequestParam(value = "session", required = false) String session,
                                    @RequestParam(value = "department", required = false) String department,
                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "25") int size,
                                    Model model,
                                    HttpSession httpSession) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        // ✅ normalize empty to null (critical)
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String sessionTerm = resolveAdminSession(session, httpSession);

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
                        if (sa.getGrade() == null || sa.getGrade().isBlank()) {
                            sa.setGrade(computeGrade(sa));
                            studentAssessmentRepository.save(sa);
                        }
                        assessmentByStudentId.put(s.getId(), sa);
                    });
        }

        // =========================
        // VL (40) — show PENDING if not keyed
        // =========================
        Map<Long, BigDecimal> vlScoreByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            StudentAssessment sa = assessmentByStudentId.get(s.getId());
            if (sa == null) continue;

            // New system: VL total /40 stored in vlFinalReport40
            if (sa.getVlFinalReport40() != null && sa.getVlFinalReport40().compareTo(BigDecimal.ZERO) > 0) {
                vlScoreByStudentId.put(s.getId(), sa.getVlFinalReport40());
            }
            // If you want legacy display, add scaling here.
        }

        // =========================
        // Industry (40) — show PENDING if not keyed
        // =========================
        Map<Long, BigDecimal> industryScoreByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            StudentAssessment sa = assessmentByStudentId.get(s.getId());
            if (sa == null) continue;

            boolean hasIndustryAny =
                    sa.getIsAttributes30() != null
                            || sa.getIsOverall10() != null
                            || sa.getIsSkills20() != null
                            || sa.getIsCommunication10() != null
                            || sa.getIsTeamwork10() != null;

            if (!hasIndustryAny) continue;

            // prefer official rubric, fallback legacy
            BigDecimal official = nz(sa.getIsAttributes30()).add(nz(sa.getIsOverall10())); // /40
            if (official.compareTo(BigDecimal.ZERO) > 0) {
                industryScoreByStudentId.put(s.getId(), official);
            } else {
                BigDecimal legacy = nz(sa.getIsSkills20()).add(nz(sa.getIsCommunication10())).add(nz(sa.getIsTeamwork10()));
                if (legacy.compareTo(BigDecimal.ZERO) > 0) {
                    industryScoreByStudentId.put(s.getId(), legacy);
                }
            }
        }

        // =========================
        // Final Report (10) — show PENDING if not keyed
        // =========================
        Map<Long, BigDecimal> reportScoreByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            StudentAssessment sa = assessmentByStudentId.get(s.getId());
            if (sa == null) continue;

            boolean hasReportAny = (sa.getAdminReportWritten5() != null) || (sa.getAdminReportVideo5() != null);
            if (!hasReportAny) continue;

            BigDecimal rep10 = nz(sa.getAdminReportWritten5()).add(nz(sa.getAdminReportVideo5()));
            reportScoreByStudentId.put(s.getId(), rep10);
        }

        // =========================
        // Logbook (10) — show PENDING if not keyed
        // =========================
        Map<Long, BigDecimal> logbookScoreByStudentId = new HashMap<>();
        for (User s : students.getContent()) {
            StudentAssessment sa = assessmentByStudentId.get(s.getId());
            if (sa != null && sa.getAdminLogbook10() != null) {
                logbookScoreByStudentId.put(s.getId(), sa.getAdminLogbook10());
            }
        }

        // =========================
        // Total + Grade
        // (Pending components treated as 0 for total, but UI can still show PENDING per column)
        // =========================
        Map<Long, BigDecimal> totalByStudentId = new HashMap<>();
        Map<Long, String> gradeByStudentId = new HashMap<>();

        for (User s : students.getContent()) {
            Long sid = s.getId();
            StudentAssessment sa = assessmentByStudentId.get(sid);

            boolean hasVl = vlScoreByStudentId.containsKey(sid);
            boolean hasInd = industryScoreByStudentId.containsKey(sid);
            boolean hasReport = reportScoreByStudentId.containsKey(sid);
            boolean hasLogbook = logbookScoreByStudentId.containsKey(sid);

            // If absolutely no data exists yet, show '-' like before.
            if (sa == null && !hasVl && !hasInd && !hasReport && !hasLogbook) {
                continue;
            }

            BigDecimal vl = hasVl ? vlScoreByStudentId.get(sid) : BigDecimal.ZERO;
            BigDecimal ind = hasInd ? industryScoreByStudentId.get(sid) : BigDecimal.ZERO;
            BigDecimal rep = hasReport ? reportScoreByStudentId.get(sid) : BigDecimal.ZERO;
            BigDecimal log = hasLogbook ? logbookScoreByStudentId.get(sid) : BigDecimal.ZERO;

            BigDecimal total = vl.add(ind).add(rep).add(log); // /100
            totalByStudentId.put(sid, total);
            gradeByStudentId.put(sid, UpmGradeUtil.gradeFromTotal(total.doubleValue()));
        }

        model.addAttribute("students", students);
        model.addAttribute("assessmentByStudentId", assessmentByStudentId);

        model.addAttribute("vlScoreByStudentId", vlScoreByStudentId);
        model.addAttribute("industryScoreByStudentId", industryScoreByStudentId);
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

        model.addAttribute("sessionOptions", rollingSessions(3, 5));

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
        // Logbook (10) keyed-in by admin (null => PENDING in UI)
        BigDecimal logbook10 = (sa != null) ? sa.getAdminLogbook10() : null;


        // Total out of 100 (treat pending logbook as 0 for total)
        BigDecimal total100 = vl40.add(industry40).add(report10).add(logbook10 != null ? logbook10 : BigDecimal.ZERO);
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
                               @RequestParam(required = false) String rawPassword,
                               RedirectAttributes ra) {

        // make sure role is always teacher
        lecturer.setRole("teacher");

        // ✅ keep enabled status when editing (do NOT always force true)
        if (lecturer.getId() != null) {
            User existing = userRepository.findById(lecturer.getId()).orElse(null);
            if (existing == null) {
                ra.addFlashAttribute("toast", "Lecturer not found.");
                return "redirect:/admin/lecturers";
            }
            lecturer.setEnabled(existing.getEnabled());

            // existing lecturer — only update password if something was entered
            if (rawPassword == null || rawPassword.isBlank()) {
                lecturer.setPassword(existing.getPassword());
            } else {
                lecturer.setPassword(passwordEncoder.encode(rawPassword));
            }
        } else {
            // NEW lecturer – password is required
            lecturer.setEnabled(true);
            if (rawPassword == null || rawPassword.isBlank()) {
                ra.addFlashAttribute("toast", "Password is required for new lecturer.");
                return "redirect:/admin/lecturers/add";
            }
            lecturer.setPassword(passwordEncoder.encode(rawPassword));
        }

        try {
            userRepository.save(lecturer);
            ra.addFlashAttribute("toast", "Lecturer saved.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Unable to save lecturer. Username may already exist.");
        }

        return "redirect:/admin/lecturers";
    }

    @PostMapping("/lecturers/delete/{id}")
    public String deleteLecturer(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userRepository.deleteById(id);
            ra.addFlashAttribute("toast", "Lecturer deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Unable to delete. Lecturer may be referenced by students/visits.");
        }
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
            Model model,
            HttpSession httpSession,
            RedirectAttributes ra
    ) {
        User lecturer = userRepository.findById(lecturerId).orElse(null);

        if (lecturer == null) {
            ra.addFlashAttribute("toast", "Lecturer not found.");
            return "redirect:/admin/lecturers";
        }

// Optional safety (recommended)
        if (!"teacher".equalsIgnoreCase(lecturer.getRole())) {
            ra.addFlashAttribute("toast", "Selected user is not a lecturer.");
            return "redirect:/admin/lecturers";
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        // Respect Admin "Current Session" default (and remembered selection)
        String sessionTerm = resolveAdminSession(session, httpSession);

        Page<User> students = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                search != null ? search.trim() : null,
                sessionTerm,
                department,
                pageable
        );

        model.addAttribute("lecturer", lecturer);
        model.addAttribute("students", students);

        model.addAttribute("search", search);
        model.addAttribute("selectedSession", sessionTerm);
        model.addAttribute("department", department);

        // reuse dropdown helpers you already use in students page
        model.addAttribute("sessionOptions", sessionOptionsForDropdown(sessionTerm));
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
        User lecturer = userRepository.findById(lecturerId).orElse(null);

        if (lecturer == null) {
            ra.addFlashAttribute("toast", "Lecturer not found.");
            return "redirect:/admin/lecturers";
        }

        if (!"teacher".equalsIgnoreCase(lecturer.getRole())) {
            ra.addFlashAttribute("toast", "Selected user is not a lecturer.");
            return "redirect:/admin/lecturers";
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
                                 @RequestParam(value = "session", required = false) String session,
                                 @RequestParam(value = "department", required = false) String department,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 Model model,
                                 HttpSession httpSession) {
        requireBean(placementRepository, "PlacementRepository");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        // Default to admin-selected / configured current session when session param is not provided
        String sessionTerm = resolveAdminSession(session, httpSession);

        // Optional department filter (derived from student's User.department)
        Department departmentTerm = null;
        if (department != null && !department.isBlank()) {
            try {
                departmentTerm = Department.valueOf(department);
            } catch (IllegalArgumentException ignored) {
                departmentTerm = null;
            }
        }

        String qTerm = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<Placement> placements = placementRepository.searchAdminPlacements(status, qTerm, sessionTerm, departmentTerm, pageable);

        Set<Long> studentIds    = new HashSet<>();
        Set<Long> supervisorIds = new HashSet<>();
        Set<Long> companyIds    = new HashSet<>();

        for (Placement p : placements.getContent()) {
            if (p.getStudentId() != null)        studentIds.add(p.getStudentId());
            if (p.getSupervisorUserId() != null) supervisorIds.add(p.getSupervisorUserId());
            if (p.getCompanyId() != null)        companyIds.add(p.getCompanyId());
        }

        // Map of userId -> User (for both student + supervisor)
        Map<Long, User> userById = new HashMap<>();
        if (!studentIds.isEmpty() || !supervisorIds.isEmpty()) {
            Set<Long> allUserIds = new HashSet<>(studentIds);
            allUserIds.addAll(supervisorIds);
            for (User u : userRepository.findAllById(allUserIds)) {
                userById.put(u.getId(), u);
            }
        }

        // Map of companyId -> Company
        Map<Long, Company> companyById = new HashMap<>();
        if (!companyIds.isEmpty() && companyRepository != null) {
            for (Company c : companyRepository.findAllById(companyIds)) {
                companyById.put(c.getId(), c);
            }
        }

        model.addAttribute("placements", placements);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        model.addAttribute("selectedSession", sessionTerm);
        model.addAttribute("sessionOptions", sessionOptionsForDropdown(sessionTerm));
        model.addAttribute("selectedDepartment", department);
        model.addAttribute("departmentOptions", Department.values());
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
    public String newPlacement(@RequestParam(value = "companyId", required = false) Long companyId,
                               Model model) {

        Placement placement = new Placement();

        List<User> allIndustrySupervisors = userRepository.findByRole("industry");
        List<User> supervisors = new ArrayList<>(allIndustrySupervisors);

        boolean filtered = false;

        if (companyId != null) {
            Company selectedCompany = companyRepository.findById(companyId).orElse(null);

            if (selectedCompany != null && selectedCompany.getName() != null) {
                String companyName = selectedCompany.getName().trim();
                supervisors = allIndustrySupervisors.stream()
                        .filter(u -> u.getCompany() != null && u.getCompany().trim().equalsIgnoreCase(companyName))
                        .toList();

                filtered = true;

                if (supervisors.size() == 1) {
                    placement.setSupervisorUserId(supervisors.get(0).getId());
                }

                if (supervisors.isEmpty()) {
                    supervisors = new ArrayList<>(allIndustrySupervisors);
                    model.addAttribute("supervisorFilterWarning", true);
                }
            }
        }

        model.addAttribute("placement", placement);
        model.addAttribute("students", userRepository.findStudentsWithoutActivePlacement());
        model.addAttribute("companies", companyRepository.findAll());
        model.addAttribute("supervisors", supervisors);

        model.addAttribute("filteredByCompany", filtered);
        model.addAttribute("selectedCompanyId", companyId);
        model.addAttribute("selectedSupervisorId", placement.getSupervisorUserId());

        return "admin-placement-form";
    }



    @PostMapping("/placements")
    public String createPlacement(@RequestParam Long studentId,
                                  @RequestParam Long supervisorUserId,
                                  @RequestParam Long companyId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                  RedirectAttributes redirectAttributes) {

        requireBean(placementRepository, "PlacementRepository");

        if (placementRepository.existsByStudentIdAndStatusIn(studentId,
                List.of(PlacementStatus.AWAITING_SUPERVISOR, PlacementStatus.AWAITING_ADMIN, PlacementStatus.APPROVED))) {
            redirectAttributes.addFlashAttribute("error",
                    "This student already has an active placement. Please cancel the existing placement first.");
            return "redirect:/admin/placements/new?companyId=" + companyId;
        }

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
        Set<Long> studentIds = new HashSet<>();
        for (LogbookEntry e : data.getContent()) {
            if (e.getStudentId() != null) {
                studentIds.add(e.getStudentId());
            }
        }
        Map<Long, User> studentById = new HashMap<>();
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
        requireBean(userRepository, "UserRepository");

        // Optional beans (some deployments may not have these wired yet)
        // Used to resolve company + supervisor names for display.
        // (We intentionally don't hard fail if missing.)

        LogbookEntry entry = logbookEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Logbook not found: " + id));

        User student = null;
        if (entry.getStudentId() != null) {
            student = userRepository.findById(entry.getStudentId()).orElse(null);
        }

        // -------- Resolve company name from Placement -> Company --------
        String companyName = null;
        String supervisorName = null;

        try {
            if (placementRepository != null && entry.getStudentId() != null) {
                var pOpt = placementRepository.findTopByStudentIdOrderByIdDesc(entry.getStudentId());
                if (pOpt.isPresent()) {
                    var p = pOpt.get();

                    // Supervisor display name
                    if (p.getSupervisorUserId() != null && userRepository != null) {
                        var sup = userRepository.findById(p.getSupervisorUserId()).orElse(null);
                        if (sup != null) {
                            supervisorName = (sup.getName() != null && !sup.getName().isBlank())
                                    ? sup.getName()
                                    : sup.getUsername();
                        }
                    }

                    // Company name
                    if (companyRepository != null && p.getCompanyId() != null) {
                        var c = companyRepository.findById(p.getCompanyId()).orElse(null);
                        if (c != null) companyName = c.getName();
                    }
                }
            }
        } catch (Exception ignore) {
            // display fallback only
        }

        // -------- Resolve lecturer display name --------
        String lecturerName = null;
        try {
            if (student != null && student.getLecturer() != null) {
                var lec = student.getLecturer();
                lecturerName = (lec.getName() != null && !lec.getName().isBlank())
                        ? lec.getName()
                        : lec.getUsername();
            }
        } catch (Exception ignore) {
            // display fallback only
        }

        // If entry.reviewedBy / lecturerReviewedBy is a generic placeholder, prefer resolved names.
        String displaySupervisor = entry.getReviewedBy();
        if (isGenericReviewer(displaySupervisor) && supervisorName != null) {
            displaySupervisor = supervisorName;
        }

        String displayLecturer = entry.getLecturerReviewedBy();
        if (isGenericReviewer(displayLecturer) && lecturerName != null) {
            displayLecturer = lecturerName;
        }

        model.addAttribute("entry", entry);
        model.addAttribute("student", student);  // for name + matric

        model.addAttribute("companyName", companyName);
        model.addAttribute("supervisorDisplayName", displaySupervisor);
        model.addAttribute("lecturerDisplayName", displayLecturer);
        return "admin-logbook-view";
    }

    private boolean isGenericReviewer(String s) {
        if (s == null) return true;
        String x = s.trim().toLowerCase();
        if (x.isBlank()) return true;
        return x.equals("teacher")
                || x.equals("industry_supervisor")
                || x.equals("industry.supervisor")
                || x.equals("industry")
                || x.equals("supervisor")
                || x.contains("industry")
                || x.contains("teacher");
    }


    private static String safe(String s) {
        return s == null ? "" : s.replace("\n"," ").replace("\r"," ").replace(",", " ");
    }

    @GetMapping("/logbooks/students")
    public String logbookStudents(@RequestParam(value="session", required=false) String session,
                                  @RequestParam(value="search", required=false) String search,
                                  @RequestParam(value="department", required=false) String department,
                                  @RequestParam(value="page", defaultValue="0") int page,
                                  @RequestParam(value="size", defaultValue="10") int size,
                                  Model model,
                                  HttpSession httpSession) {

        requireBean(userRepository, "UserRepository");
        requireBean(logbookEntryRepository, "LogbookEntryRepository");
        requireBean(studentAssessmentRepository, "StudentAssessmentRepository");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        // Default session filter
        session = resolveAdminSession(session, httpSession);

        String q = (search == null) ? "" : search.trim();

        // Parse Department enum safely
        Department deptEnum = null;
        if (department != null && !department.isBlank()) {
            try {
                deptEnum = Department.valueOf(department);
            } catch (IllegalArgumentException ignored) {
                deptEnum = null;
            }
        }

        // Use your existing repository method that already supports session+department
        Page<User> studentsPage = userRepository.searchStudentsWithSessionAndDepartment(
                "student",
                q,
                session,
                deptEnum,
                pageable
        );

        // build summary map for just the current page
        List<Long> studentIds = studentsPage.getContent().stream()
                .map(User::getId)
                .toList();

        Map<Long, StudentLogbookSummaryNative> summaryByStudentId = new HashMap<>();
        if (!studentIds.isEmpty()) {
            logbookEntryRepository.summarizeNativeByStudentIds(studentIds)
                    .forEach(s -> summaryByStudentId.put(s.getStudentId(), s));
        }
        model.addAttribute("summaryByStudentId", summaryByStudentId);


        // ===== NEW: Assessment map (admin logbook score) =====
        // Because StudentAssessment is UNIQUE by (student_user_id, session),
        // we fetch by grouping by each student's session (works even when session filter is empty).
        Map<Long, StudentAssessment> assessmentByStudentId = new HashMap<>();

        if (!studentsPage.getContent().isEmpty()) {
            Map<String, List<Long>> idsBySession = new HashMap<>();
            for (User u : studentsPage.getContent()) {
                if (u.getSession() == null || u.getSession().isBlank()) continue;
                idsBySession.computeIfAbsent(u.getSession(), k -> new ArrayList<>()).add(u.getId());
            }

            for (Map.Entry<String, List<Long>> e : idsBySession.entrySet()) {
                String ses = e.getKey();
                List<Long> ids = e.getValue();
                studentAssessmentRepository.findByStudentUserIdInAndSession(ids, ses)
                        .forEach(sa -> assessmentByStudentId.put(sa.getStudentUserId(), sa));
            }
        }

        model.addAttribute("students", studentsPage);
        model.addAttribute("summaryByStudentId", summaryByStudentId);
        model.addAttribute("assessmentByStudentId", assessmentByStudentId);

        // dropdowns
        model.addAttribute("sessionOptions", rollingSessions(3, 5));
        model.addAttribute("selectedSession", session);
        model.addAttribute("search", q);

        // NEW department dropdown like final report
        model.addAttribute("departmentOptions", Department.values());
        model.addAttribute("selectedDepartment", department);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", studentsPage.getTotalPages());
        model.addAttribute("size", size);

        return "admin-logbooks-students";
    }

    @GetMapping("/logbooks/student/{studentId}")
    public String logbooksByStudent(@PathVariable Long studentId,
                                    @RequestParam(value="status", required=false) ReviewStatus status,
                                    @RequestParam(value="session", required=false) String sessionStr,
                                    @RequestParam(value="page", defaultValue="0") int page,
                                    @RequestParam(value="size", defaultValue="10") int size,
                                    HttpSession session,
                                    Model model,
                                    HttpSession httpSession) {

        User admin = (User) session.getAttribute("user");
        if (admin == null) return "redirect:/login";
        if (!"admin".equalsIgnoreCase(admin.getRole())) return "redirect:/login";

        requireBean(logbookEntryRepository, "LogbookEntryRepository");
        requireBean(userRepository, "UserRepository");

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        String sess = (sessionStr != null && !sessionStr.isBlank())
                ? sessionStr
                : (student.getSession() == null || student.getSession().isBlank() ? "DEFAULT" : student.getSession());

        // Load / create StudentAssessment row for storing logbook mark
        StudentAssessment sa = studentAssessmentRepository
                .findByStudentUserIdAndSession(student.getId(), sess)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(student.getId());
                    x.setSession(sess);
                    return x;
                });

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "weekStartDate"));

        Page<LogbookEntry> data = (status == null)
                ? logbookEntryRepository.findByStudentId(studentId, pageable)
                : logbookEntryRepository.findByStudentIdAndStatus(studentId, status, pageable);

        model.addAttribute("student", student);
        model.addAttribute("logbooks", data);
        model.addAttribute("status", status);

        // NEW
        model.addAttribute("sa", sa);
        model.addAttribute("sessionStr", sess);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        model.addAttribute("size", size);

        return "admin-logbooks-student";
    }

    @PostMapping("/logbooks/mark")
    public String adminSaveLogbookMark(@RequestParam Long studentId,
                                       @RequestParam String sessionStr,
                                       @RequestParam BigDecimal logbook10,
                                       HttpSession session,
                                       RedirectAttributes ra) {

        User admin = (User) session.getAttribute("user");
        if (admin == null) return "redirect:/login";
        if (!"admin".equalsIgnoreCase(admin.getRole())) return "redirect:/login";

        // clamp [0..10]
        logbook10 = logbook10.max(BigDecimal.ZERO).min(new BigDecimal("10"));

        StudentAssessment sa = studentAssessmentRepository
                .findByStudentUserIdAndSession(studentId, sessionStr)
                .orElseGet(() -> {
                    StudentAssessment x = new StudentAssessment();
                    x.setStudentUserId(studentId);
                    x.setSession(sessionStr);
                    return x;
                });

        sa.setAdminLogbook10(logbook10);
        studentAssessmentRepository.save(sa);

        ra.addFlashAttribute("toast", "Logbook mark saved: " + logbook10 + "/10");
        return "redirect:/admin/logbooks/student/" + studentId + "?session=" + sessionStr;
    }





    // ============================
    // Company Info (student submissions)
    // ============================
    @GetMapping("/company-info")
    public String listCompanyInfo(@RequestParam(value="status", required=false) String statusValue,
                                  @RequestParam(value="session", required=false) String session,
                                  @RequestParam(value="department", required=false) String department,
                                  @RequestParam(value="page", defaultValue="0") int page,
                                  @RequestParam(value="size", defaultValue="10") int size,
                                  Model model,
                                  HttpSession httpSession) {
        CompanyInfoStatus status = null;
        if (statusValue != null && !statusValue.isBlank()) {
            try {
                status = CompanyInfoStatus.valueOf(statusValue);
            } catch (IllegalArgumentException ignored) {
                // invalid status → treat as "all"
            }
        }

        Pageable p = PageRequest.of(page, size, Sort.by("id").descending());

        // Default to admin-selected / configured current session when session param is not provided
        String sessionTerm = resolveAdminSession(session, httpSession);

        // Optional department filter (derived from student's User.department)
        Department departmentTerm = null;
        if (department != null && !department.isBlank()) {
            try {
                departmentTerm = Department.valueOf(department);
            } catch (IllegalArgumentException ignored) {
                departmentTerm = null;
            }
        }

        // Unified query supports optional status + session + department
        Page<CompanyInfo> data = companyInfoRepository.searchAdminCompanyInfo(status, sessionTerm, departmentTerm, p);

        var ids = data.stream()
                .map(CompanyInfo::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        var users = userRepository.findAllById(ids);

        // ✅ Map <User.id, User> so Thymeleaf can access name + matric
        Map<Long, User> userById = new HashMap<>();
        for (User u : users) {
            userById.put(u.getId(), u);
        }

        model.addAttribute("infos", data);
        model.addAttribute("userById", userById);
        model.addAttribute("status", status);
        model.addAttribute("statusValue", statusValue);
        model.addAttribute("selectedSession", sessionTerm);
        model.addAttribute("sessionOptions", sessionOptionsForDropdown(sessionTerm));
        model.addAttribute("selectedDepartment", department);
        model.addAttribute("departmentOptions", Department.values());
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

    // =========================================================
    // Offer letter download (Admin only)
    // =========================================================
    @GetMapping("/company-info/{id}/offer-letter")
    public ResponseEntity<Resource> downloadOfferLetter(@PathVariable Long id, HttpSession session) throws IOException {
        User admin = (User) session.getAttribute("user");
        if (admin == null || !"admin".equalsIgnoreCase(admin.getRole())) {
            return ResponseEntity.status(403).build();
        }

        CompanyInfo info = companyInfoRepository.findById(id).orElse(null);
        if (info == null || info.getOfferLetterPath() == null || info.getOfferLetterPath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        String publicPath = info.getOfferLetterPath();
        if (!publicPath.startsWith("/uploads/")) {
            return ResponseEntity.badRequest().build();
        }

        // Convert /uploads/... -> <project>/uploads/...
        String rel = publicPath.substring("/uploads/".length());
        Path uploadsRoot = Paths.get("uploads").toAbsolutePath().normalize();
        Path filePath = uploadsRoot.resolve(rel).normalize();

        // Prevent path traversal
        if (!filePath.startsWith(uploadsRoot)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        String downloadName = "offer-letter-companyinfo-" + id + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .body(resource);
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
                                     @RequestParam(required = false) String newCompanyAddressLine1,
                                     @RequestParam(required = false) String newCompanyAddressLine2,
                                     @RequestParam(required = false) String newCompanyPostcode,
                                     @RequestParam(required = false) String newCompanyDistrict,
                                     @RequestParam(required = false) String newCompanyState,
                                     @RequestParam(required = false) String newCompanyStateOther,
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

        // Block re-processing if already decided
        if (info.getStatus() != CompanyInfoStatus.PENDING) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This submission is already " + info.getStatus() + " and cannot be processed again.");
            return "redirect:/admin/company-info/process?id=" + id;
        }

        // ✅ Defensive: block processing if student already has an active placement
        if (placementRepository.existsByStudentIdAndStatusIn(info.getStudentId(),
                List.of(PlacementStatus.AWAITING_SUPERVISOR, PlacementStatus.AWAITING_ADMIN, PlacementStatus.APPROVED))) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Student already has an active placement. Please cancel the current placement before processing a new company info.");
            return "redirect:/admin/company-info/process?id=" + id;
        }

        // -------------------------
        // Resolve Company (existing/new)
        // -------------------------
        Long companyId;
        Company chosenCompany = null;

        if ("existing".equalsIgnoreCase(companyMode)) {
            if (existingCompanyId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Existing company must be selected.");
                return "redirect:/admin/company-info/process?id=" + id;
            }
            companyId = existingCompanyId;
            chosenCompany = companyRepository.findById(companyId).orElse(null);
        } else {
            requireBean(companyRepository, "CompanyRepository");

            Company company;
            if (info.getLinkedCompanyId() != null) {
                company = companyRepository.findById(info.getLinkedCompanyId()).orElse(new Company());
            } else {
                company = new Company();
            }

            String name = (newCompanyName != null && !newCompanyName.isBlank())
                    ? newCompanyName.trim()
                    : info.getCompanyName();
            if (name == null || name.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Company name is required.");
                return "redirect:/admin/company-info/process?id=" + id;
            }

            company.setName(name);

            // ✅ Detailed address (preferred). If admin leaves them empty, fall back to student submission.
            String l1 = trimToNull(newCompanyAddressLine1);
            String l2 = trimToNull(newCompanyAddressLine2);
            String pc = trimToNull(newCompanyPostcode);
            String dist = trimToNull(newCompanyDistrict);
            MalaysiaState st = parseMalaysiaState(newCompanyState);
            String other = trimToNull(newCompanyStateOther);

            if (l1 == null) l1 = info.getAddressLine1();
            if (l2 == null) l2 = info.getAddressLine2();
            if (pc == null) pc = info.getPostcode();
            if (dist == null) dist = info.getDistrict();
            if (st == null) st = info.getState();
            if (st == MalaysiaState.OTHER && other == null) other = info.getStateOther();
            if (st != MalaysiaState.OTHER) other = null;

            company.setAddressLine1(trimToNull(l1));
            company.setAddressLine2(trimToNull(l2));
            company.setPostcode(trimToNull(pc));
            company.setDistrict(trimToNull(dist));
            company.setState(st);
            company.setStateOther(other);

            // ✅ Keep legacy address string for existing UIs (override only if admin explicitly fills it)
            String legacyOverride = trimToNull(newCompanyAddress);
            company.setAddress(legacyOverride != null ? legacyOverride : buildFullAddress(company.getAddressLine1(), company.getAddressLine2(), company.getPostcode(), company.getDistrict(), company.getState(), company.getStateOther()));

            // ✅ normalize sector to enum name string for Company
            if (sector != null && !sector.isBlank()) {
                try {
                    company.setSector(CompanySector.valueOf(sector.trim()).name());
                } catch (Exception ex) {
                    company.setSector(CompanySector.OTHERS.name());
                }
            }

            companyRepository.save(company);
            companyId = company.getId();
            chosenCompany = company;
        }

        // -------------------------
        // Resolve Supervisor (existing/new/none)
        // -------------------------
        Long supervisorUserId = null;

        if ("existing".equalsIgnoreCase(supervisorMode)) {
            if (existingSupervisorId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Existing supervisor must be selected.");
                return "redirect:/admin/company-info/process?id=" + id;
            }

            // ✅ Sync chosen supervisor with this company (IMPORTANT)
            User sup = userRepository.findById(existingSupervisorId)
                    .orElseThrow(() -> new IllegalArgumentException("Supervisor not found: " + existingSupervisorId));

            sup.setCompanyId(companyId);

            // Keep User.company string consistent (UI + legacy)
            if (chosenCompany == null) chosenCompany = companyRepository.findById(companyId).orElse(null);
            if (chosenCompany != null) sup.setCompany(chosenCompany.getName());

            userRepository.save(sup);

            supervisorUserId = sup.getId();
        } else if ("new".equalsIgnoreCase(supervisorMode)) {

            String username = (supervisorUsername != null && !supervisorUsername.isBlank())
                    ? supervisorUsername.trim().toLowerCase()
                    : (info.getSupervisorEmail() != null ? info.getSupervisorEmail().trim().toLowerCase() : null);

            if (username == null || username.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Supervisor username/email is required for new account.");
                return "redirect:/admin/company-info/process?id=" + id;
            }

            if (userRepository.findByUsername(username).isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Username already exists: " + username);
                return "redirect:/admin/company-info/process?id=" + id;
            }

            String rawPassword = (supervisorPassword != null && !supervisorPassword.isBlank())
                    ? supervisorPassword
                    : generateTempPassword();

            if (chosenCompany == null) chosenCompany = companyRepository.findById(companyId).orElse(null);

            User supervisor = new User();
            supervisor.setUsername(username);
            supervisor.setPassword(encode(rawPassword));
            supervisor.setRole("industry");
            supervisor.setName((supervisorName != null && !supervisorName.isBlank())
                    ? supervisorName.trim()
                    : info.getSupervisorName());

            supervisor.setCompanyId(companyId);
            supervisor.setCompany(chosenCompany != null ? chosenCompany.getName() : info.getCompanyName());

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
            redirectAttributes.addFlashAttribute("errorMessage", "Unsupported supervisor mode.");
            return "redirect:/admin/company-info/process?id=" + id;
        }

        // -------------------------
        // Create Placement
        // -------------------------
        if (placementRepository == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "System configuration error: PlacementRepository not available.");
            return "redirect:/admin/company-info/process?id=" + id;
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

        // -------------------------
        // Update CompanyInfo
        // -------------------------
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

        // if CompanyInfo has sector (enum), keep it consistent
        if (sector != null && !sector.isBlank()) {
            try {
                info.setSector(CompanySector.valueOf(sector.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        companyInfoRepository.save(info);

        logAction("PROCESS_COMPANY_INFO",
                (isNewPlacement ? "Created" : "Updated") + " placement " + placement.getId()
                        + " for CompanyInfo#" + id + " (company=" + companyId
                        + ", supervisor=" + (supervisorUserId != null ? supervisorUserId : "none") + ")");

        redirectAttributes.addFlashAttribute("successMessage",
                "Placement " + placement.getId() + " is now awaiting supervisor verification.");

        // ✅ IMPORTANT: redirect to the correct GET page pattern used elsewhere
        return "redirect:/admin/company-info/process?id=" + id;
    }


    @PostMapping("/company-info/{id}/verify")
    public String verifyCompanyInfo(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();

        // ✅ Prevent "VERIFIED" without a placement link (can cause data mismatch).
        // Admin should use "Save & Create/Update Placement" first.
        if (placementRepository.findFirstByCompanyInfoId(id).isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Please click 'Save & Create/Update Placement' before verifying."
            );
            return "redirect:/admin/company-info/process?id=" + id;
        }

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

        // ✅ Copy detailed location from student submission (CompanyInfo)
        company.setAddressLine1(trimToNull(ci.getAddressLine1()));
        company.setAddressLine2(trimToNull(ci.getAddressLine2()));
        company.setPostcode(trimToNull(ci.getPostcode()));
        company.setDistrict(trimToNull(ci.getDistrict()));
        company.setState(ci.getState());
        company.setStateOther(ci.getState() == MalaysiaState.OTHER ? trimToNull(ci.getStateOther()) : null);

        // Keep legacy combined address for older UI
        company.setAddress(buildFullAddress(company.getAddressLine1(), company.getAddressLine2(), company.getPostcode(), company.getDistrict(), company.getState(), company.getStateOther()));
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

    @PostMapping("/placements/{id}/delete")
    @Transactional
    public String cancelPlacement(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {

        Placement p = placementRepository.findById(id).orElse(null);

        if (p == null) {
            redirectAttributes.addFlashAttribute("error", "Placement not found.");
            return "redirect:/admin/placements";
        }

        // ✅ Soft cancel (instead of delete)
        p.setStatus(PlacementStatus.CANCELLED);
        placementRepository.save(p);

        // ✅ IMPORTANT: allow student to re-submit company info after mid-internship company change.
        // Student submission is blocked unless the latest CompanyInfo is REJECTED.
        // So when we cancel a placement, mark the linked CompanyInfo as REJECTED too.
        try {
            Long companyInfoId = p.getCompanyInfoId();
            if (companyInfoId != null) {
                CompanyInfo ci = companyInfoRepository.findById(companyInfoId).orElse(null);
                if (ci != null && ci.getStatus() != CompanyInfoStatus.REJECTED) {
                    ci.setStatus(CompanyInfoStatus.REJECTED);
                    companyInfoRepository.save(ci);
                }
            }
        } catch (Exception ignored) {
            // Do not block placement cancellation if company info update fails.
        }

        redirectAttributes.addFlashAttribute(
                "success",
                "Placement cancelled successfully. Student can now submit a new company information form."
        );
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
                                @RequestParam(value = "sector", required = false) String sector,
                                @RequestParam(value = "state", required = false) String state,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                @RequestParam(value = "size", defaultValue = "10") int size,
                                Model model,
                                HttpSession httpSession) {
        requireBean(companyRepository, "CompanyRepository");

        // Remember sector filter in HTTP session (shared across admin dashboard)
        CompanySector sectorFilter = resolveAdminSector(sector, httpSession);

        MalaysiaState stateFilter = null;
        if (state != null && !state.isBlank() && !"__ALL__".equals(state)) {
            stateFilter = parseMalaysiaState(state);
        }

        Pageable p = PageRequest.of(page, size, Sort.by("name").ascending());

        String qv = (q == null || q.isBlank()) ? null : q.trim();
        String sec = (sectorFilter == null) ? null : sectorFilter.name();
        Page<Company> data = companyRepository.searchCompanyMaster(qv, sec, stateFilter, p);

        model.addAttribute("companies", data);
        model.addAttribute("sectorOptions", CompanySector.values());
        model.addAttribute("selectedSector", sectorFilter == null ? SECTOR_ALL_SENTINEL : sectorFilter.name());
        model.addAttribute("stateOptions", MalaysiaState.values());
        model.addAttribute("selectedState", stateFilter == null ? "__ALL__" : stateFilter.name());
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
                                @RequestParam(required = false) String addressLine1,
                                @RequestParam(required = false) String addressLine2,
                                @RequestParam(required = false) String postcode,
                                @RequestParam(required = false) String district,
                                @RequestParam(required = false) String companyState,
                                @RequestParam(required = false) String companyStateOther,
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

        // ✅ Detailed location (new) + keep legacy combined address for older UI
        c.setAddressLine1(trimToNull(addressLine1));
        c.setAddressLine2(trimToNull(addressLine2));
        c.setPostcode(trimToNull(postcode));
        c.setDistrict(trimToNull(district));
        MalaysiaState st = parseMalaysiaState(companyState);
        c.setState(st);
        c.setStateOther((st == MalaysiaState.OTHER) ? trimToNull(companyStateOther) : null);

        // If admin still passes legacy address field, keep it as override; otherwise auto-build.
        String legacyOverride = trimToNull(address);
        c.setAddress(legacyOverride != null ? legacyOverride : buildFullAddress(c.getAddressLine1(), c.getAddressLine2(), c.getPostcode(), c.getDistrict(), c.getState(), c.getStateOther()));

        // ✅ Normalize sector into enum name (string)
        if (sector == null || sector.isBlank()) {
            c.setSector(null);
        } else {
            try {
                c.setSector(CompanySector.valueOf(sector.trim()).name());
            } catch (Exception ex) {
                c.setSector(CompanySector.OTHERS.name());
            }
        }

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

        // All industry users (supervisors)
        List<User> allIndustry = userRepository.findByRole("industry");

        // Linked supervisors:
        // - prefer companyId match (source of truth)
        // - fallback to legacy string match for older records
        String companyName = company.getName() != null ? company.getName().trim() : null;
        List<User> supervisors = new ArrayList<>();

        for (User u : allIndustry) {
            boolean byId = (u.getCompanyId() != null && u.getCompanyId().equals(company.getId()));
            boolean byLegacyName = (u.getCompanyId() == null
                    && companyName != null
                    && u.getCompany() != null
                    && companyName.equalsIgnoreCase(u.getCompany().trim()));

            if (byId || byLegacyName) {
                supervisors.add(u);
            }
        }

        // Available supervisors for linking:
        // only show truly unlinked accounts (no companyId and no legacy company name).
        List<User> available = new ArrayList<>();
        for (User u : allIndustry) {
            boolean hasIdLink = u.getCompanyId() != null;
            boolean hasLegacyLink = u.getCompany() != null && !u.getCompany().isBlank();
            if (!hasIdLink && !hasLegacyLink) {
                available.add(u);
            }
        }

        model.addAttribute("company", company);
        model.addAttribute("supervisors", supervisors);
        // list of unlinked industry users – used in "link existing" dropdown
        model.addAttribute("availableSupervisors", available);
        model.addAttribute("sectorOptions", CompanySector.values());
        return "admin-company-detail";
    }

    // Link an existing industry user to this company
    @PostMapping("/company-master/{companyId}/link-supervisor")
    public String linkSupervisorToCompany
    (@PathVariable Long companyId,
     @RequestParam Long supervisorId,
     RedirectAttributes ra) {
        requireBean(companyRepository, "CompanyRepository");
        Company company = companyRepository.findById(companyId).orElseThrow();
        User sup = userRepository.findById(supervisorId).orElseThrow();

        // Enforce: one supervisor account can only be linked to ONE company.
        if (sup.getCompanyId() != null && !sup.getCompanyId().equals(companyId)) {
            ra.addFlashAttribute("toast",
                    "This supervisor is already linked to another company. Please unlink first.");
            return "redirect:/admin/company-master/" + companyId;
        }

        // Legacy safety: if company string is set but companyId is null, treat as linked
        if (sup.getCompanyId() == null
                && sup.getCompany() != null
                && !sup.getCompany().isBlank()
                && (company.getName() == null || !sup.getCompany().trim().equalsIgnoreCase(company.getName().trim()))) {
            ra.addFlashAttribute("toast",
                    "This supervisor is already linked to another company (legacy record). Please unlink first.");
            return "redirect:/admin/company-master/" + companyId;
        }

        sup.setRole("industry"); // make sure it is an industry user
        sup.setCompanyId(companyId);
        sup.setCompany(company.getName()); // keep display name in sync
        userRepository.save(sup);

        logAction("LINK_INDUSTRY_SUPERVISOR",
                "Linked userId=" + supervisorId + " to companyId=" + companyId);
        ra.addFlashAttribute("toast", "Supervisor linked to company.");
        return "redirect:/admin/company-master/" + companyId;
    }

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

        // Lock to ONE company (source of truth: companyId)
        sup.setCompanyId(companyId);
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

        boolean linkedById = (sup.getCompanyId() != null && sup.getCompanyId().equals(companyId));
        boolean linkedByLegacyName = (sup.getCompanyId() == null
                && sup.getCompany() != null
                && company.getName() != null
                && company.getName().trim().equalsIgnoreCase(sup.getCompany().trim()));

        if (linkedById || linkedByLegacyName) {
            sup.setCompanyId(null);
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


    @PostMapping("/industry-supervisors/save")
    public String saveIndustrySupervisor(
            @ModelAttribute User supervisor,
            @RequestParam(value = "newPassword", required = false) String newPassword
    ) {
        // This screen is now edit-only (create & link happens in Company Detail page)
        if (supervisor.getId() == null) {
            return "redirect:/admin/industry-supervisors";
        }

        User existing = userRepository.findById(supervisor.getId()).orElseThrow();

        // Keep role correct
        existing.setRole("industry");

        // Update editable fields
        existing.setName(supervisor.getName());
        existing.setUsername(supervisor.getUsername());

        // Update password only if provided
        if (newPassword != null && !newPassword.isBlank()) {
            existing.setPassword(encode(newPassword));
        }

        // IMPORTANT: do NOT change company/companyId here (locked)
        userRepository.save(existing);

        return "redirect:/admin/industry-supervisors";
    }


    @GetMapping("/industry-supervisors/edit/{id}")
    public String editIndustrySupervisor(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User sup = userRepository.findById(id).orElse(null);
        if (sup == null) {
            ra.addFlashAttribute("toast", "Industry supervisor not found.");
            return "redirect:/admin/industry-supervisors";
        }
        model.addAttribute("supervisor", sup);
        return "admin-industry-supervisor-form";
    }


    @PostMapping("/industry-supervisors/delete/{id}")
    public String deleteIndustrySupervisor(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userRepository.deleteById(id);
            ra.addFlashAttribute("toast", "Supervisor deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Unable to delete. This supervisor may be used in placements/logbooks.");
        }
        return "redirect:/admin/industry-supervisors";
    }


    @GetMapping("/company-master/new")
    public String companyMasterCreate(Model model) {
        requireBean(companyRepository, "CompanyRepository");
        model.addAttribute("company", new Company());
        model.addAttribute("sectorOptions", CompanySector.values());
        return "admin-company-master-create";
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

            String[] headers = {"No", "Student", "Matric", "Session", "VL (40)", "Industry (40)", "Admin Report (10)", "Admin Logbook (10)", "Total", "Grade"};
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

                // OFFICIAL 2026 totals
                BigDecimal vl = BigDecimal.ZERO;
                BigDecimal ind = BigDecimal.ZERO;
                BigDecimal report10 = BigDecimal.ZERO;
                BigDecimal logbook10 = BigDecimal.ZERO;

                if (sa != null) {
                    // VL: if legacy buckets exist, scale 60 -> 40; otherwise treat vlFinalReport40 as /40
                    boolean hasLegacyVlBuckets =
                            nz(sa.getVlEvaluation10()).compareTo(BigDecimal.ZERO) > 0
                                    || nz(sa.getVlAttendance5()).compareTo(BigDecimal.ZERO) > 0
                                    || nz(sa.getVlLogbook5()).compareTo(BigDecimal.ZERO) > 0;

                    BigDecimal legacyVl60 = nz(sa.getVlEvaluation10())
                            .add(nz(sa.getVlAttendance5()))
                            .add(nz(sa.getVlLogbook5()))
                            .add(nz(sa.getVlFinalReport40()));

                    vl = hasLegacyVlBuckets
                            ? legacyVl60.multiply(new BigDecimal("40"))
                            .divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP)
                            : nz(sa.getVlFinalReport40()); // new /40

                    // Industry: prefer official rubric
                    ind = nz(sa.getIsAttributes30()).add(nz(sa.getIsOverall10()));
                    if (ind.compareTo(BigDecimal.ZERO) == 0) {
                        ind = nz(sa.getIsSkills20())
                                .add(nz(sa.getIsCommunication10()))
                                .add(nz(sa.getIsTeamwork10()));
                    }

                    report10 = nz(sa.getAdminReportWritten5()).add(nz(sa.getAdminReportVideo5()));
                    logbook10 = nz(sa.getAdminLogbook10());
                }

                boolean vlNoData = (sa == null) || vl.compareTo(BigDecimal.ZERO) == 0;
                boolean indNoData = (sa == null) || ind.compareTo(BigDecimal.ZERO) == 0;
                boolean reportNoData = (sa == null) || report10.compareTo(BigDecimal.ZERO) == 0;
                boolean logbookNoData = (sa == null) || logbook10.compareTo(BigDecimal.ZERO) == 0;

                BigDecimal total = vl.add(ind).add(report10).add(logbook10);

                String vlText = vlNoData ? "No data" : fmt2(vl) + " / 40";
                String indText = indNoData ? "No data" : fmt2(ind) + " / 40";
                String reportText = reportNoData ? "No data" : fmt2(report10) + " / 10";
                String logbookText = logbookNoData ? "No data" : fmt2(logbook10) + " / 10";

                String totalText = (vlNoData && indNoData && reportNoData && logbookNoData) ? "-" : fmt2(total);
                String grade = (sa != null && sa.getGrade() != null && !sa.getGrade().isBlank()) ? sa.getGrade() : "-";

                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(no++);
                r.createCell(1).setCellValue(studentName);
                r.createCell(2).setCellValue(matric);
                r.createCell(3).setCellValue(sessionText);
                r.createCell(4).setCellValue(vlText);
                r.createCell(5).setCellValue(indText);
                r.createCell(6).setCellValue(reportText);
                r.createCell(7).setCellValue(logbookText);
                r.createCell(8).setCellValue(totalText);
                r.createCell(9).setCellValue(grade);
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
                               Model model,
                               HttpSession httpSession) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

        // ✅ normalize empty -> null (fix search bug)
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String sessionTerm = resolveAdminSession(session, httpSession);

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

        model.addAttribute("sessionOptions", rollingSessions(3, 5));

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

        studentAssessmentService.saveAdminFinalReportMarks(studentId, sessionStr, written5, video5);

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

    // ===== Current Session (Admin) =====
    private static final String SETTING_CURRENT_SESSION = "CURRENT_SESSION";
    private static final String ADMIN_SELECTED_SESSION_ATTR = "ADMIN_SELECTED_SESSION";
    private static final String SESSION_ALL_SENTINEL = "__ALL__";

    // ===== Current Sector Filter (Admin, stored in HTTP session) =====
    private static final String ADMIN_SELECTED_SECTOR_ATTR = "ADMIN_SELECTED_SECTOR";
    private static final String SECTOR_ALL_SENTINEL = "__ALL__";

    /**
     * Sector filter behavior:
     * - If admin chooses a sector => remember it in HTTP session
     * - If admin chooses "All sectors" => clear remembered sector and return null
     * - If no sector param provided => fall back to remembered sector
     */
    private CompanySector resolveAdminSector(String requested, HttpSession httpSession) {
        if (httpSession == null) {
            return parseSectorOrNull(requested);
        }

        if (requested != null && SECTOR_ALL_SENTINEL.equalsIgnoreCase(requested.trim())) {
            httpSession.removeAttribute(ADMIN_SELECTED_SECTOR_ATTR);
            return null;
        }

        CompanySector parsed = parseSectorOrNull(requested);
        if (parsed != null) {
            httpSession.setAttribute(ADMIN_SELECTED_SECTOR_ATTR, parsed.name());
            return parsed;
        }

        Object saved = httpSession.getAttribute(ADMIN_SELECTED_SECTOR_ATTR);
        if (saved instanceof String s && !s.isBlank()) {
            try {
                return CompanySector.valueOf(s.trim());
            } catch (Exception ignored) {
                httpSession.removeAttribute(ADMIN_SELECTED_SECTOR_ATTR);
            }
        }
        return null;
    }

    private CompanySector parseSectorOrNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return null;
        if (SECTOR_ALL_SENTINEL.equalsIgnoreCase(s)) return null;
        try {
            return CompanySector.valueOf(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ===== Company location helpers =====
    private String trimToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private MalaysiaState parseMalaysiaState(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return MalaysiaState.valueOf(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildFullAddress(String line1, String line2, String postcode, String district, MalaysiaState state, String stateOther) {
        String l1 = trimToNull(line1);
        String l2 = trimToNull(line2);
        String pc = trimToNull(postcode);
        String dist = trimToNull(district);
        String st;
        if (state == null) st = null;
        else if (state == MalaysiaState.OTHER) st = trimToNull(stateOther);
        else st = prettyState(state);

        StringBuilder sb = new StringBuilder();
        if (l1 != null) sb.append(l1);
        if (l2 != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(l2);
        }
        if (pc != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(pc);
        }
        if (dist != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(dist);
        }
        if (st != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(st);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String prettyState(MalaysiaState s) {
        if (s == null) return null;
        String raw = s.name().toLowerCase().replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            out.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.length() > 1 ? p.substring(1) : "")
                    .append(' ');
        }
        return out.toString().trim();
    }

    private String normalizeSessionParam(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        if (SESSION_ALL_SENTINEL.equalsIgnoreCase(v)) return null;
        return v;
    }

    private String getConfiguredCurrentSession() {
        if (systemSettingRepository == null) return null;
        return systemSettingRepository.findByKey(SETTING_CURRENT_SESSION)
                .map(SystemSetting::getValue)
                .orElse(null);
    }

    private void saveSystemSetting(String key, String value) {
        if (systemSettingRepository == null) return;
        SystemSetting s = systemSettingRepository.findByKey(key)
                .orElseGet(() -> new SystemSetting(key, null));
        s.setValue(value);
        systemSettingRepository.save(s);
    }

    /**
     * Default admin session behavior:
     * - If admin chooses a session => use it and remember it in HTTP session
     * - If admin chooses "All sessions" => clear remembered session and return null
     * - If no session param provided => fall back to remembered session, then configured current session
     */
    private String resolveAdminSession(String requested, HttpSession httpSession) {
        if (httpSession == null) return normalizeSessionParam(requested);

        if (requested != null && SESSION_ALL_SENTINEL.equalsIgnoreCase(requested.trim())) {
            httpSession.removeAttribute(ADMIN_SELECTED_SESSION_ATTR);
            return null;
        }

        String norm = normalizeSessionParam(requested);
        if (norm != null) {
            httpSession.setAttribute(ADMIN_SELECTED_SESSION_ATTR, norm);
            return norm;
        }

        Object saved = httpSession.getAttribute(ADMIN_SELECTED_SESSION_ATTR);
        if (saved instanceof String s && !s.isBlank()) {
            return s;
        }

        String cfg = getConfiguredCurrentSession();
        return (cfg == null || cfg.isBlank()) ? null : cfg.trim();
    }

    private Optional<String> getActorUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getName() != null
                    && !"anonymousUser".equals(auth.getName())) {
                return Optional.of(auth.getName());
            }
        } catch (Throwable ignored) {}
        return Optional.of("admin");
    }

    private List<String> rollingSessions(int pastYears, int futureYears) {
        int now = java.time.Year.now().getValue();
        List<String> out = new ArrayList<>();
        for (int y = now - pastYears; y <= now + futureYears; y++) {
            String base = y + "/" + (y + 1);
            out.add(base + "-1");
            out.add(base + "-2");
        }
        return out;
    }

    /**
     * Builds the session dropdown list.
     * Ensures the currently selected session is included even if it falls outside the rolling range,
     * so the dropdown won't default back to "All sessions".
     */
    private List<String> sessionOptionsForDropdown(String selectedSession) {
        List<String> opts = new ArrayList<>(rollingSessions(3, 5));
        if (selectedSession != null) {
            String s = selectedSession.trim();
            if (!s.isBlank() && !opts.contains(s)) {
                opts.add(0, s);
            }
        }
        return opts;
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

        // VL: if legacy VL(60) buckets exist, scale 60 -> 40; otherwise treat vlFinalReport40 as /40
        boolean hasLegacyVlBuckets =
                nz(sa.getVlEvaluation10()).compareTo(BigDecimal.ZERO) > 0
                        || nz(sa.getVlAttendance5()).compareTo(BigDecimal.ZERO) > 0
                        || nz(sa.getVlLogbook5()).compareTo(BigDecimal.ZERO) > 0;

        BigDecimal legacyVl60 = nz(sa.getVlEvaluation10())
                .add(nz(sa.getVlAttendance5()))
                .add(nz(sa.getVlLogbook5()))
                .add(nz(sa.getVlFinalReport40())); // legacy max 60

        BigDecimal vl40 = hasLegacyVlBuckets
                ? legacyVl60.multiply(new BigDecimal("40"))
                .divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP)
                : nz(sa.getVlFinalReport40()); // new /40

        // Industry (40): prefer official rubric fields, fallback to legacy
        BigDecimal ind40 = nz(sa.getIsAttributes30()).add(nz(sa.getIsOverall10()));
        if (ind40.compareTo(BigDecimal.ZERO) == 0) {
            ind40 = nz(sa.getIsSkills20())
                    .add(nz(sa.getIsCommunication10()))
                    .add(nz(sa.getIsTeamwork10()));
        }

        // Admin: report (10) + logbook (10)
        BigDecimal report10 = nz(sa.getAdminReportWritten5()).add(nz(sa.getAdminReportVideo5()));
        BigDecimal logbook10 = nz(sa.getAdminLogbook10());

        BigDecimal total100 = vl40.add(ind40).add(report10).add(logbook10);

        return UpmGradeUtil.gradeFromTotal(total100.doubleValue());
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