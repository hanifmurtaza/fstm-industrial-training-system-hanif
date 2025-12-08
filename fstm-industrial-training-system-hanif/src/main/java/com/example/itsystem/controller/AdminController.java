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

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

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
        long companyCount   = companyInfoRepository.count();
        long evalCount      = evaluationRepository.count();
        long documentCount  = documentRepository.count();

        model.addAttribute("studentCount", studentCount);
        model.addAttribute("companyCount", companyCount);
        model.addAttribute("evalCount", evalCount);
        model.addAttribute("documentCount", documentCount);

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
    public String students(@RequestParam(value = "search", required = false) String search,
                           @RequestParam(value = "session", required = false) String session,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "size", defaultValue = "15") int size,
                           Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<User> students;

        if (session != null && !session.isBlank()) {
            students = (search != null && !search.isBlank())
                    ? userRepository.searchStudentsBySession("student", session, search.trim(), pageable)
                    : userRepository.findAllByRoleAndSession("student", session, pageable);
        } else {
            students = (search != null && !search.isBlank())
                    ? userRepository.searchStudents("student", search.trim(), pageable)
                    : userRepository.findAllByRole("student", pageable);
        }

        model.addAttribute("students", students);
        model.addAttribute("search", search);
        model.addAttribute("selectedSession", session);
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", students.getTotalPages());
        model.addAttribute("size", size);
        return "admin-students";
    }

    @GetMapping("/students/add")
    public String addStudentForm(Model model) {
        model.addAttribute("student", new User());
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        return "admin-student-form";
    }

    @PostMapping("/students/add")
    public String saveNewStudent(@RequestParam String name,
                                 @RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam String studentId,
                                 @RequestParam String session,
                                 @RequestParam String company) {

        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setStudentId(studentId);
        user.setSession(session);
        user.setCompany(company);
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
        model.addAttribute("student", userRepository.findById(id).orElse(null));
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
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
                } else {
                    incoming.setPassword(encode(incoming.getPassword()));
                }
                if (incoming.getEnabled() == null) incoming.setEnabled(current.getEnabled());
                if (incoming.getAccessStart() == null) incoming.setAccessStart(current.getAccessStart());
                if (incoming.getAccessEnd() == null) incoming.setAccessEnd(current.getAccessEnd());
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
    public String bulkStudents(@RequestParam(value = "ids", required = false) List<Long> ids,
                               @RequestParam("mode") String mode,
                               @RequestParam(value = "enabled", required = false) Boolean enabled,
                               @RequestParam(value = "start", required = false) String start,
                               @RequestParam(value = "end", required = false) String end,
                               RedirectAttributes ra) {

        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("toast", "No students selected for bulk action.");
            return "redirect:/admin/students";
        }

        List<User> users = userRepository.findAllById(ids);

        if ("status".equals(mode) && enabled != null) {
            for (User u : users) {
                u.setEnabled(enabled);
            }
            userRepository.saveAll(users);
            logAction("BULK_USER_STATUS", "Set enabled=" + enabled + " for " + ids.size() + " students");
            ra.addFlashAttribute("toast", "Updated status for " + ids.size() + " students.");

        } else if ("window".equals(mode)
                && start != null && !start.isBlank()
                && end != null && !end.isBlank()) {

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
            logAction("BULK_USER_ACCESS_WINDOW", "Set access window for " + ids.size() + " students");
            ra.addFlashAttribute("toast", "Updated access window for " + ids.size() + " students.");
        } else {
            ra.addFlashAttribute("toast", "Invalid bulk operation.");
        }

        return "redirect:/admin/students";
    }

    @GetMapping("/students/import")
    public String importForm(Model model) {
        model.addAttribute("sessionOptions", rollingSessions(3, 1));
        return "admin-students-import";
    }

    @PostMapping("/students/import/preview")
    public String importPreview(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                @RequestParam("defaultSession") String defaultSession,
                                Model model,
                                jakarta.servlet.http.HttpSession session) throws IOException {

        var preview = bulkImportService.preview(file, defaultSession);
        session.setAttribute("bulkImportPreview", preview);

        model.addAttribute("preview", preview);
        model.addAttribute("defaultSession", defaultSession);
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

        int created = bulkImportService.commit(preview.validRows(), this::ensureUser);
        session.removeAttribute("bulkImportPreview");

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
    // Evaluations
    // ============================
    @GetMapping("/evaluations")
    public String manageEvaluations(@RequestParam(value = "search", required = false) String search,
                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "5") int size,
                                    Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Evaluation> evaluations = (search != null && !search.isBlank())
                ? evaluationRepository.findByStudent_NameContainingOrStudent_StudentIdContaining(
                search.trim(), search.trim(), pageable)
                : evaluationRepository.findAll(pageable);

        model.addAttribute("evaluations", evaluations);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", evaluations.getTotalPages());
        return "admin-evaluations";
    }

    @GetMapping("/evaluations/evaluate/{id}")
    public String evaluateStudent(@PathVariable Long id, Model model) {
        User student = userRepository.findById(id).orElse(null);
        if (student != null) {
            model.addAttribute("student", student);
            model.addAttribute("evaluation", new Evaluation());
        }
        return "admin-student-evaluation";
    }

    @PostMapping("/evaluations/evaluate/{id}")
    public String saveEvaluation(@PathVariable Long id, @ModelAttribute Evaluation evaluation) {
        User student = userRepository.findById(id).orElse(null);
        if (student != null) {
            evaluation.setStudent(student);
            evaluationRepository.save(evaluation);
            logAction("EVAL_ADMIN_REPORT", "Saved admin evaluation for studentId=" + id);
        }
        return "redirect:/admin/evaluations";
    }

    // ============================
    // Lecturers
    // ============================
    @GetMapping("/lecturers")
    public String manageLecturers(Model model) {
        List<User> lecturers = userRepository.findByRole("teacher");
        model.addAttribute("lecturers", lecturers);
        return "admin-lecturers";
    }

    @GetMapping("/lecturers/add")
    public String addLecturerForm(Model model) {
        model.addAttribute("lecturer", new User());
        return "admin-lecturer-form";
    }

    @PostMapping("/lecturers/save")
    public String saveLecturer(@ModelAttribute User lecturer) {
        lecturer.setRole("teacher");
        if (lecturer.getPassword() != null && !lecturer.getPassword().isBlank()) {
            lecturer.setPassword(encode(lecturer.getPassword()));
        }
        if (lecturer.getEnabled() == null) lecturer.setEnabled(true);
        if (lecturer.getAccessStart() == null) lecturer.setAccessStart(LocalDate.now());
        if (lecturer.getAccessEnd() == null) lecturer.setAccessEnd(LocalDate.now().plusMonths(6));

        userRepository.save(lecturer);
        logAction("ADD_LECTURER", "Added/Updated lecturer: " + lecturer.getUsername());
        return "redirect:/admin/lecturers";
    }

    @GetMapping("/lecturers/edit/{id}")
    public String editLecturer(@PathVariable Long id, Model model) {
        model.addAttribute("lecturer", userRepository.findById(id).orElse(null));
        return "admin-lecturer-form";
    }

    @PostMapping("/lecturers/delete/{id}")
    public String deleteLecturer(@PathVariable Long id) {
        userRepository.deleteById(id);
        logAction("DELETE_LECTURER", "Deleted lecturer id=" + id);
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

        model.addAttribute("logbooks", data);
        model.addAttribute("status", status);
        model.addAttribute("studentId", studentId);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        return "admin-logbooks";
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

    private static String safe(String s) {
        return s == null ? "" : s.replace("\n"," ").replace("\r"," ").replace(",", " ");
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
                                     @RequestParam(required = false) String newCompanySector,
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
            if (newCompanySector != null && !newCompanySector.isBlank()) {
                company.setSector(newCompanySector.trim());
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
        placement.setStatus(PlacementStatus.AWAITING_SUPERVISOR);
        if (placementNotes != null && !placementNotes.isBlank()) {
            placement.setJobScope(placementNotes.trim());
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
            info.setJobScope(placementNotes.trim());
        }
        if (newCompanySector != null && !newCompanySector.isBlank()) {
            try {
                info.setSector(CompanySector.valueOf(newCompanySector.trim()));
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


    // ============================
    // Grades export
    // ============================
    @GetMapping("/grades/export")
    public void exportGrades(@RequestParam String semester, HttpServletResponse resp) throws IOException {
        requireBean(gradingService, "GradingService");
        gradingService.exportXlsx(semester, resp);
        logAction("EXPORT_GRADES", "Exported final grades for semester=" + semester);
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
}
