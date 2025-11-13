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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.example.itsystem.model.CompanyInfoStatus;



import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    // ----- Existing repos -----
    @Autowired private UserRepository userRepository;
    @Autowired private EvaluationRepository evaluationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private CompanyInfoRepository companyInfoRepository;
    @Autowired(required = false) private PlacementRepository placementRepository; // Placement + status FSM
    @Autowired(required = false) private CompanyRepository companyRepository;     // Company master list
    @Autowired(required = false) private LogbookEntryRepository logbookEntryRepository;
    @Autowired private com.example.itsystem.service.BulkStudentImportService bulkImportService;



    // ----- Services -----
    @Autowired(required = false) private AdminMetricsService adminMetricsService; // KPIs for dashboard
    @Autowired(required = false) private GradingService gradingService;           // Final grade compute + XLSX

    // Hash passwords on create/update (optional if you already wire it)
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
                        : placementRepository.findByStatus(PlacementStatus.AWAITING_ADMIN, PageRequest.of(0, 10)));

        if (logbookEntryRepository != null) {
            model.addAttribute("logbooksAwaitingSup",
                    logbookEntryRepository.findByStatusAndEndorsedFalse(ReviewStatus.PENDING, PageRequest.of(0, 10)));
            model.addAttribute("logbooksAwaitingLec",
                    logbookEntryRepository.findByEndorsedTrueAndEndorsedByLecturerFalse(PageRequest.of(0, 10)));
        } else {
            model.addAttribute("logbooksAwaitingSup", Page.empty());
            model.addAttribute("logbooksAwaitingLec", Page.empty());
        }
        return "admin-notifications";
    }



    // ============================
    // Students (existing + access window + enable/disable)
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
        // default access window (optional): open now → +6 months
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
        incoming.setRole("student"); // enforce role
        if (incoming.getId() != null) {
            User current = userRepository.findById(incoming.getId()).orElse(null);
            if (current != null) {
                // keep existing password if left blank
                if (incoming.getPassword() == null || incoming.getPassword().isBlank()) {
                    incoming.setPassword(current.getPassword());
                } else {
                    incoming.setPassword(encode(incoming.getPassword()));
                }
                // keep existing enable/access if not supplied
                if (incoming.getEnabled() == null) incoming.setEnabled(current.getEnabled());
                if (incoming.getAccessStart() == null) incoming.setAccessStart(current.getAccessStart());
                if (incoming.getAccessEnd() == null) incoming.setAccessEnd(current.getAccessEnd());
            }
        } else {
            // creating via this endpoint
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

    // NEW: enable/disable + access window (works for any user, reuse on lecturers too)
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
            @RequestParam(value = "ids", required = false) java.util.List<Long> ids,
            @RequestParam("mode") String mode,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("toast", "No students selected for bulk action.");
            return "redirect:/admin/students";
        }

        java.util.List<User> users = userRepository.findAllById(ids);

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

            java.time.LocalDate s = java.time.LocalDate.parse(start);
            java.time.LocalDate e = java.time.LocalDate.parse(end);

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
        model.addAttribute("sessionOptions", rollingSessions(3,1));
        return "admin-students-import";
    }

    @PostMapping("/students/import/preview")
    public String importPreview(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                @RequestParam("defaultSession") String defaultSession,
                                Model model,
                                jakarta.servlet.http.HttpSession session) throws IOException {

        var preview = bulkImportService.preview(file, defaultSession);

        // Store preview in session so commit can reuse it
        session.setAttribute("bulkImportPreview", preview);

        model.addAttribute("preview", preview);
        model.addAttribute("defaultSession", defaultSession);
        return "admin-students-import-preview";
    }


    @PostMapping("/students/import/commit")
    public String importCommit(jakarta.servlet.http.HttpSession session,
                               org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        com.example.itsystem.dto.BulkPreviewResult preview =
                (com.example.itsystem.dto.BulkPreviewResult) session.getAttribute("bulkImportPreview");

        if (preview == null) {
            ra.addFlashAttribute("toast", "No bulk import preview found. Please upload the file again.");
            return "redirect:/admin/students";
        }

        int created = bulkImportService.commit(preview.validRows(), this::ensureUser);

        // Clear it so you don't accidentally re-use old data
        session.removeAttribute("bulkImportPreview");

        logAction("BULK_IMPORT_STUDENTS", "Imported " + created + " students");
        ra.addFlashAttribute("toast", "Imported " + created + " students.");
        return "redirect:/admin/students";
    }




    // Minimal user bootstrap if missing
    private void ensureUser(String matric, String name) {
        userRepository.findByUsername(matric).orElseGet(() -> {
            User u = new User();
            u.setUsername(matric);
            u.setStudentId(matric);
            u.setName(name);
            u.setRole("student");
            u.setPassword(encode("changeme"));
            u.setEnabled(false);
            if (u.getAccessStart() == null) u.setAccessStart(java.time.LocalDate.now());
            if (u.getAccessEnd() == null)   u.setAccessEnd(java.time.LocalDate.now().plusMonths(6));
            return userRepository.save(u);
        });
    }


    // ============================
    // Evaluations (existing)  — keep for Admin-owned report marks (10%)
    // ============================
    @GetMapping("/evaluations")
    public String manageEvaluations(@RequestParam(value = "search", required = false) String search,
                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "5") int size,
                                    Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Evaluation> evaluations = (search != null && !search.isBlank())
                ? evaluationRepository.findByStudent_NameContainingOrStudent_StudentIdContaining(search.trim(), search.trim(), pageable)
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
            model.addAttribute("evaluation", new Evaluation()); // Admin’s 10% report slot if you use it this way
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
    // Lecturers (existing)
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
    // Placements (NEW): list + approve (final lock)
    // ============================
    @GetMapping("/placements")
    public String listPlacements(@RequestParam(value = "status", required = false) PlacementStatus status,
                                 @RequestParam(value = "q", required = false) String q,   // <- added
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 Model model) {
        requireBean(placementRepository, "PlacementRepository");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Placement> placements = (status == null)
                ? placementRepository.findAll(pageable)
                : placementRepository.findByStatus(status, pageable);

        // ---- Build ID -> Name maps for just the items on this page ----
        java.util.Set<Long> studentIds   = new java.util.HashSet<>();
        java.util.Set<Long> supervisorIds= new java.util.HashSet<>();
        java.util.Set<Long> companyIds   = new java.util.HashSet<>();

        for (Placement p : placements.getContent()) {
            if (p.getStudentId() != null)        studentIds.add(p.getStudentId());
            if (p.getSupervisorUserId() != null) supervisorIds.add(p.getSupervisorUserId());
            if (p.getCompanyId() != null)        companyIds.add(p.getCompanyId());
        }

        // users (students + supervisors)
        java.util.Map<Long,String> userById = new java.util.HashMap<>();
        if (!studentIds.isEmpty() || !supervisorIds.isEmpty()) {
            java.util.Set<Long> allUserIds = new java.util.HashSet<>(studentIds);
            allUserIds.addAll(supervisorIds);
            for (User u : userRepository.findAllById(allUserIds)) {
                userById.put(u.getId(), (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername());
            }
        }

        // companies
        java.util.Map<Long,String> companyById = new java.util.HashMap<>();
        if (!companyIds.isEmpty() && companyRepository != null) {
            for (Company c : companyRepository.findAllById(companyIds)) {
                companyById.put(c.getId(), c.getName());
            }
        }

        model.addAttribute("placements", placements);
        model.addAttribute("status", status);
        model.addAttribute("q", q);                 // keep q in model so template links work
        model.addAttribute("userById", userById);   // maps for names
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
            // only approve when already verified by supervisor
            return "redirect:/admin/placements?status=" + plc.getStatus();
        }
        plc.setStatus(PlacementStatus.APPROVED);
        placementRepository.save(plc);

        // TODO (optional): lock critical fields / propagate to Student-Company links
        logAction("APPROVE_PLACEMENT", "Approved placement id=" + id);
        return "redirect:/admin/placements";
    }


    // --- Company MASTER (new, separate from CompanyInfo) ---
    @GetMapping("/company-master")
    public String companyMaster(@RequestParam(value = "q", required = false) String q,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                @RequestParam(value = "size", defaultValue = "10") int size,
                                Model model) {
        requireBean(companyRepository, "CompanyRepository"); // master repo

        org.springframework.data.domain.Pageable p =
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("name").ascending());

        org.springframework.data.domain.Page<com.example.itsystem.model.Company> data =
                (q == null || q.isBlank())
                        ? companyRepository.findAll(p)
                        : companyRepository.findByNameContainingIgnoreCase(q.trim(), p);

        model.addAttribute("companies", data);   // Page<Company>
        model.addAttribute("q", q);
        return "admin-company-master";           // new template below
    }

    @PostMapping("/company-master")
    public String upsertCompany(@RequestParam(required = false) Long id,
                                @RequestParam String name,
                                @RequestParam(required = false) String address,
                                @RequestParam(required = false) String sector,
                                @RequestParam(required = false) String defaultJobScope,
                                @RequestParam(required = false) java.math.BigDecimal typicalAllowance,
                                @RequestParam(required = false, defaultValue = "false") boolean accommodation) {
        requireBean(companyRepository, "CompanyRepository");

        var c = (id == null)
                ? new com.example.itsystem.model.Company()
                : companyRepository.findById(id).orElse(new com.example.itsystem.model.Company());

        c.setName(name);
        c.setAddress(address);
        c.setSector(sector);
        c.setDefaultJobScope(defaultJobScope);
        c.setTypicalAllowance(typicalAllowance);
        c.setAccommodation(accommodation);

        companyRepository.save(c);
        logAction("UPSERT_COMPANY_MASTER", (id == null ? "Created" : "Updated") + " company: " + name);
        return "redirect:/admin/company-master";
    }

    @PostMapping("/company-master/{id}/delete")
    public String deleteCompany(@PathVariable Long id) {
        requireBean(companyRepository, "CompanyRepository");
        companyRepository.deleteById(id);
        logAction("DELETE_COMPANY_MASTER", "Deleted company id=" + id);
        return "redirect:/admin/company-master";
    }

    // ============================
    // Final grades export (NEW)
    // ============================
    @GetMapping("/grades/export")
    public void exportGrades(@RequestParam String semester, HttpServletResponse resp) throws IOException {
        requireBean(gradingService, "GradingService");

        // Will set headers and write XLSX bytes to response
        gradingService.exportXlsx(semester, resp);
        logAction("EXPORT_GRADES", "Exported final grades for semester=" + semester);
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
                                  @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                                  @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        requireBean(placementRepository, "PlacementRepository");
        com.example.itsystem.model.Placement p = new com.example.itsystem.model.Placement();
        p.setStudentId(studentId);
        p.setSupervisorUserId(supervisorUserId);
        p.setCompanyId(companyId);
        p.setStartDate(startDate);
        p.setEndDate(endDate);
        p.setStatus(com.example.itsystem.model.PlacementStatus.AWAITING_ADMIN); // simulate after supervisor verify
        placementRepository.save(p);
        logAction("CREATE_PLACEMENT", "Created placement for studentId=" + studentId);
        return "redirect:/admin/placements?status=AWAITING_ADMIN";
    }

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
            rows = logbookEntryRepository.findByStudentId(studentId, PageRequest.of(0, 10000)).getContent();
        } else if (status != null) {
            rows = logbookEntryRepository.findByStatus(status, PageRequest.of(0, 10000)).getContent();
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

    @GetMapping("/company-info")
    public String listCompanyInfo(@RequestParam(value="status", required=false) CompanyInfoStatus status,
                                  @RequestParam(value="page", defaultValue="0") int page,
                                  @RequestParam(value="size", defaultValue="10") int size,
                                  Model model) {
        Pageable p = PageRequest.of(page, size, Sort.by("id").descending());
        Page<CompanyInfo> data = (status == null)
                ? companyInfoRepository.findAll(p)
                : companyInfoRepository.findByStatus(status, p);

        // build id -> name map
        var ids = data.stream().map(CompanyInfo::getStudentId).distinct().toList();
        var users = userRepository.findAllById(ids);
        java.util.Map<Long,String> userById = new java.util.HashMap<>();
        for (var u : users) userById.put(u.getId(), u.getName() != null ? u.getName() : u.getUsername());

        model.addAttribute("infos", data);
        model.addAttribute("userById", userById);
        model.addAttribute("status", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", data.getTotalPages());
        return "admin-company-info";
    }

    // Verify (mark as VERIFIED) — does NOT create catalog or placement yet
    @PostMapping("/company-info/{id}/verify")
    public String verifyCompanyInfo(@PathVariable Long id) {
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();
        ci.setStatus(CompanyInfoStatus.VERIFIED);
        companyInfoRepository.save(ci);
        logAction("VERIFY_COMPANY_INFO", "Verified CompanyInfo id=" + id);
        return "redirect:/admin/company-info?status=VERIFIED";
    }

    // Reject
    @PostMapping("/company-info/{id}/reject")
    public String rejectCompanyInfo(@PathVariable Long id) {
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();
        ci.setStatus(CompanyInfoStatus.REJECTED);
        companyInfoRepository.save(ci);
        logAction("REJECT_COMPANY_INFO", "Rejected CompanyInfo id=" + id);
        return "redirect:/admin/company-info?status=REJECTED"; // <- change to REJECTED
    }


    // Promote to master Company (dedupe by name); leaves status as-is (usually VERIFIED)
    @PostMapping("/company-info/{id}/promote")
    public String promoteCompanyInfo(@PathVariable Long id) {
        requireBean(companyRepository, "CompanyRepository");
        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();

        var company = companyRepository.findByNameIgnoreCase(ci.getCompanyName())
                .orElseGet(com.example.itsystem.model.Company::new);

        company.setName(ci.getCompanyName());
        company.setAddress(ci.getAddress());
        // Optionally set sector/defaultJobScope if you capture them on CompanyInfo
        companyRepository.save(company);

        ci.setLinkedCompanyId(company.getId());
        if (ci.getStatus() == CompanyInfoStatus.PENDING) ci.setStatus(CompanyInfoStatus.VERIFIED);
        companyInfoRepository.save(ci);

        logAction("PROMOTE_COMPANY", "Promoted '" + company.getName() + "' from CompanyInfo#" + id);
        return "redirect:/admin/company-info?status=VERIFIED";
    }

    // One-click: Promote (if needed) + Create Placement (AWAITING_ADMIN)
    @PostMapping("/company-info/{id}/create-placement")
    public String createPlacementFromInfo(@PathVariable Long id) {
        requireBean(companyRepository, "CompanyRepository");
        requireBean(placementRepository, "PlacementRepository");

        CompanyInfo ci = companyInfoRepository.findById(id).orElseThrow();

        // Ensure catalog company exists
        Long companyId = ci.getLinkedCompanyId();
        if (companyId == null) {
            var company = companyRepository.findByNameIgnoreCase(ci.getCompanyName())
                    .orElseGet(com.example.itsystem.model.Company::new);
            company.setName(ci.getCompanyName());
            company.setAddress(ci.getAddress());
            companyRepository.save(company);
            companyId = company.getId();
            ci.setLinkedCompanyId(companyId);
            if (ci.getStatus() == CompanyInfoStatus.PENDING) ci.setStatus(CompanyInfoStatus.VERIFIED);
            companyInfoRepository.save(ci);
        }

        // Create placement
        Placement p = new Placement();
        p.setStudentId(ci.getStudentId());
        p.setCompanyId(companyId);
        p.setCompanyInfoId(ci.getId()); // backref
        p.setStatus(PlacementStatus.AWAITING_ADMIN);
        placementRepository.save(p);

        logAction("CREATE_PLACEMENT_FROM_INFO", "Placement " + p.getId() + " from CompanyInfo#" + id);
        return "redirect:/admin/placements?status=AWAITING_ADMIN";
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

    private java.util.Optional<String> getActorUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getName() != null
                    && !"anonymousUser".equals(auth.getName())) {
                return java.util.Optional.of(auth.getName());
            }
        } catch (Throwable ignored) {}
        // fallback so logging never crashes
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
        return out; // newest first
    }


    private String encode(String raw) {
        if (passwordEncoder == null || raw == null) return raw;
        return passwordEncoder.encode(raw);
    }

    private void requireBean(Object bean, String name) {
        if (bean == null) throw new IllegalStateException(name + " is not wired yet.");
    }
}
