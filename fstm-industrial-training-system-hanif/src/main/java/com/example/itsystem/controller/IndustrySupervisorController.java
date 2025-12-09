package com.example.itsystem.controller;

import com.example.itsystem.model.AuditLog;
import com.example.itsystem.model.Company;
import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.ReviewStatus;
import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;
import com.example.itsystem.model.SupervisorEvaluation;
import com.example.itsystem.repository.AuditLogRepository;
import com.example.itsystem.repository.CompanyRepository;
import com.example.itsystem.repository.LogbookEntryRepository;
import com.example.itsystem.repository.PlacementRepository;
import com.example.itsystem.repository.SupervisorEvaluationRepository;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.model.User;


import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/industry")
public class IndustrySupervisorController {

    private final LogbookEntryRepository logbookRepo;
    private final AuditLogRepository auditLogRepo;
    private final PlacementRepository placementRepo;
    private final SupervisorEvaluationRepository evalRepo;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    public IndustrySupervisorController(LogbookEntryRepository logbookRepo,
                                        AuditLogRepository auditLogRepo,
                                        PlacementRepository placementRepo,
                                        SupervisorEvaluationRepository evalRepo,
                                        UserRepository userRepository,
                                        CompanyRepository companyRepository) {
        this.logbookRepo = logbookRepo;
        this.auditLogRepo = auditLogRepo;
        this.placementRepo = placementRepo;
        this.evalRepo = evalRepo;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    private boolean notIndustry(HttpSession session) {
        Object auth = session.getAttribute("auth");
        if (auth instanceof java.util.Map<?, ?> m) {
            Object role = m.get("role");
            return role == null || !"industry".equalsIgnoreCase(String.valueOf(role));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Long currentUserId(HttpSession session) {
        Object auth = session.getAttribute("auth");
        if (auth instanceof Map<?, ?> m && m.get("id") != null) {
            try { return Long.valueOf(String.valueOf(m.get("id"))); } catch (Exception ignore) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String currentDisplayName(HttpSession session) {
        Object auth = session.getAttribute("auth");
        if (auth instanceof Map<?, ?> m && m.get("name") != null) {
            return String.valueOf(m.get("name"));
        }
        return "industry_supervisor";
    }

    // =========================================================
    //                       DASHBOARD
    // =========================================================
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        long pending  = logbookRepo.countByStatus(ReviewStatus.PENDING);
        long approved = logbookRepo.countByStatus(ReviewStatus.APPROVED);
        long rejected = logbookRepo.countByStatus(ReviewStatus.REJECTED);

        model.addAttribute("pending", pending);
        model.addAttribute("approved", approved);
        model.addAttribute("rejected", rejected);
        return "industry-dashboard";
    }

    @GetMapping("/industry-dashboard")
    public String legacyDashboard(HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";
        return "redirect:/industry/dashboard";
    }

    // =========================================================
    //                   LOGBOOKS (UPDATED)
    // =========================================================
    @GetMapping("/logbooks")
    public String listLogbooks(@RequestParam(value = "status", required = false) ReviewStatus status,
                               @RequestParam(value = "studentId", required = false) Long studentIdFilter,
                               @RequestParam(value = "page", defaultValue = "0") int page,
                               @RequestParam(value = "size", defaultValue = "10") int size,
                               HttpSession session,
                               Model model) {

        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // 1) Find all students supervised by this user
        List<Long> myStudentIds = placementRepo.findStudentIdsBySupervisor(me);
        if (myStudentIds == null || myStudentIds.isEmpty()) {
            // no placements → no logbooks
            Page<LogbookEntry> emptyPage = Page.empty();
            model.addAttribute("entries", emptyPage);
            model.addAttribute("status", status);
            model.addAttribute("studentId", studentIdFilter);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", 0);
            model.addAttribute("statuses", ReviewStatus.values());
            return "industry-logbooks";
        }

        Pageable pageable = PageRequest.of(page, size);

        // 2) Page logbooks only for those students
        Page<LogbookEntry> entries = logbookRepo.findForSupervisor(
                myStudentIds,
                status,
                studentIdFilter,
                pageable
        );

        // 🔹 NEW: resolve User (name + matric) for each studentId on this page
        // --- NEW: resolve students for name + matric ---
        java.util.Set<Long> studentIds = new java.util.HashSet<>();
        for (LogbookEntry e : entries.getContent()) {
            if (e.getStudentId() != null) {
                studentIds.add(e.getStudentId());
            }
        }

        java.util.Map<Long, com.example.itsystem.model.User> studentById = new java.util.HashMap<>();
        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds)
                    .forEach(u -> studentById.put(u.getId(), u));
        }


        model.addAttribute("entries", entries);
        model.addAttribute("studentById", studentById);
        model.addAttribute("status", status);
        model.addAttribute("studentId", studentIdFilter);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", entries.getTotalPages());
        model.addAttribute("statuses", ReviewStatus.values());



        return "industry-logbooks";
    }


    @GetMapping("/logbooks/{id}")
    public String viewLogbook(@PathVariable Long id, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Optional<LogbookEntry> opt = logbookRepo.findById(id);
        if (opt.isEmpty()) return "redirect:/industry/logbooks";

        LogbookEntry entry = opt.get();

        // Security: ensure this student is actually supervised by this user
        boolean owned = placementRepo.existsByStudentIdAndSupervisorUserId(entry.getStudentId(), me);
        if (!owned) {
            return "redirect:/industry/logbooks";
        }

        // 🔹 NEW: load student (name + matric)
        User student = null;
        if (entry.getStudentId() != null) {
            student = userRepository.findById(entry.getStudentId()).orElse(null);
        }

        model.addAttribute("entry", entry);
        model.addAttribute("student", student);   // <– NEW
        model.addAttribute("statuses", ReviewStatus.values());
        return "industry-logbook-view";
    }


    @PostMapping("/logbooks/{id}/review")
    public String reviewLogbook(@PathVariable Long id,
                                @RequestParam("action") String action, // "approve" or "reject"
                                @RequestParam(value = "comment", required = false) String comment,
                                HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";

        String who = currentDisplayName(session);
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        logbookRepo.findById(id).ifPresent(e -> {
            // Security: ensure ownership
            boolean owned = placementRepo.existsByStudentIdAndSupervisorUserId(e.getStudentId(), me);
            if (!owned) return;

            ReviewStatus newStatus =
                    "approve".equalsIgnoreCase(action) ? ReviewStatus.APPROVED : ReviewStatus.REJECTED;

            e.setStatus(newStatus);
            e.setReviewComment(comment);
            e.setReviewedBy(who);
            e.setReviewedAt(LocalDateTime.now());
            logbookRepo.save(e);

            AuditLog log = new AuditLog();
            log.setAction("LOGBOOK_REVIEW");
            log.setUsername(who);
            log.setDescription("Set logbook " + id + " to " + newStatus +
                    (comment != null && !comment.isBlank() ? " | " + comment : ""));
            log.setTimestamp(LocalDateTime.now());
            auditLogRepo.save(log);
        });

        return "redirect:/industry/logbooks";
    }

    // =========================================================
    //         PLACEMENTS (verify by supervisor – existing)
    // =========================================================
    @GetMapping("/placements")
    public String listPlacements(@RequestParam(value = "status", required = false) PlacementStatus status,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 HttpSession session,
                                 Model model) {

        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Pageable pageable = PageRequest.of(page, size);

        Page<Placement> items;
        // ✅ If a status is selected -> filter by that status
        // ✅ If no status (or "All") -> show ALL placements for this supervisor
        if (status != null) {
            items = placementRepo.findBySupervisorUserIdAndStatus(me, status, pageable);
        } else {
            items = placementRepo.findBySupervisorUserId(me, pageable);
        }

        Map<Long, String> studentNames = new HashMap<>();
        Map<Long, String> companyNames = new HashMap<>();

        Set<Long> studentIds = new HashSet<>();
        Set<Long> companyIds = new HashSet<>();

        for (Placement p : items.getContent()) {
            if (p.getStudentId() != null) studentIds.add(p.getStudentId());
            if (p.getCompanyId() != null) companyIds.add(p.getCompanyId());
        }

        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds).forEach(u ->
                    studentNames.put(u.getId(),
                            (u.getName() != null && !u.getName().isBlank())
                                    ? u.getName()
                                    : u.getUsername()));
        }
        if (!companyIds.isEmpty()) {
            companyRepository.findAllById(companyIds).forEach(c ->
                    companyNames.put(c.getId(), c.getName()));
        }

        model.addAttribute("items", items);
        model.addAttribute("status", status);                 // <– for dropdown selection
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", items.getTotalPages());
        model.addAttribute("studentNames", studentNames);
        model.addAttribute("companyNames", companyNames);
        return "industry-placements";
    }


    @GetMapping("/placements/{id}")
    public String viewPlacement(@PathVariable Long id, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Optional<Placement> opt = placementRepo.findById(id);
        if (opt.isEmpty()) return "redirect:/industry/placements";
        Placement placement = opt.get();
        model.addAttribute("placement", placement);
        if (placement.getStudentId() != null) {
            userRepository.findById(placement.getStudentId()).ifPresent(u ->
                    model.addAttribute("studentName",
                            (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername()));
        }
        if (placement.getCompanyId() != null) {
            companyRepository.findById(placement.getCompanyId()).ifPresent(c ->
                    model.addAttribute("companyName", c.getName()));
        }
        model.addAttribute("sectorOptions", List.of("Bakery","Food Manufacturing","Frozen Food","Catering","Others"));
        return "industry-placement-edit";
    }

    @PostMapping("/placements/{id}/verify")
    @Transactional
    public String verifyPlacement(@PathVariable Long id,
                                  @RequestParam String department,
                                  @RequestParam String jobScope,
                                  @RequestParam(required = false) String allowance,
                                  @RequestParam(defaultValue = "false") boolean accommodation,
                                  @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate reportDutyDate,
                                  @RequestParam(required = false) List<String> sectors,
                                  HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";
        String who = currentDisplayName(session);

        placementRepo.findById(id).ifPresent(p -> {
            p.setDepartment(department);
            p.setJobScope(jobScope);
            p.setAllowance(allowance);
            p.setAccommodation(accommodation);
            p.setReportDutyDate(reportDutyDate);
            // if you later add sectorTags: p.setSectorTags(sectors != null ? String.join(",", sectors) : null);
            p.setStatus(PlacementStatus.AWAITING_ADMIN);
            placementRepo.save(p);

            AuditLog log = new AuditLog();
            log.setAction("PLACEMENT_VERIFY");
            log.setUsername(who);
            log.setDescription("Verified placement " + id + " and set status to AWAITING_ADMIN");
            log.setTimestamp(LocalDateTime.now());
            auditLogRepo.save(log);
        });

        return "redirect:/industry/placements";
    }

    // =========================================================
    //            COMPANY PROFILE (for this supervisor)
    // =========================================================

    @GetMapping("/companies")
    public String listMyCompanies(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // Find all distinct company IDs linked to placements under this supervisor
        List<Long> companyIds = placementRepo.findDistinctCompanyIdsBySupervisorUserId(me);
        if (companyIds == null || companyIds.isEmpty()) {
            model.addAttribute("companies", List.of());
            return "industry-companies"; // HTML to be created later
        }

        Iterable<Company> companies = companyRepository.findAllById(companyIds);
        model.addAttribute("companies", companies);
        return "industry-companies";
    }

    @GetMapping("/companies/{companyId}/edit")
    public String editCompany(@PathVariable Long companyId,
                              HttpSession session,
                              Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // Ensure this company is really associated with this supervisor
        List<Long> myCompanyIds = placementRepo.findDistinctCompanyIdsBySupervisorUserId(me);
        if (myCompanyIds == null || !myCompanyIds.contains(companyId)) {
            return "redirect:/industry/companies";
        }

        Optional<Company> opt = companyRepository.findById(companyId);
        if (opt.isEmpty()) {
            return "redirect:/industry/companies";
        }

        model.addAttribute("company", opt.get());
        return "industry-company-edit"; // HTML to be created later
    }

    @PostMapping("/companies/{companyId}/edit")
    public String updateCompany(@PathVariable Long companyId,
                                @ModelAttribute("company") @Valid Company form,
                                HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";
        String who = currentDisplayName(session);
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // Ensure this company belongs to this supervisor via placements
        List<Long> myCompanyIds = placementRepo.findDistinctCompanyIdsBySupervisorUserId(me);
        if (myCompanyIds == null || !myCompanyIds.contains(companyId)) {
            return "redirect:/industry/companies";
        }

        companyRepository.findById(companyId).ifPresent(company -> {
            // Update only fields supervisor is allowed to maintain
            company.setName(form.getName());
            company.setAddress(form.getAddress());
            company.setSector(form.getSector());
            company.setDefaultJobScope(form.getDefaultJobScope());
            company.setTypicalAllowance(form.getTypicalAllowance());
            company.setAccommodation(form.getAccommodation());
            company.setContactName(form.getContactName());
            company.setContactEmail(form.getContactEmail());
            company.setContactPhone(form.getContactPhone());
            company.setWebsite(form.getWebsite());
            // We intentionally do NOT touch ratingAvg, ratingCount, notes here.

            companyRepository.save(company);

            AuditLog log = new AuditLog();
            log.setAction("COMPANY_PROFILE_UPDATE");
            log.setUsername(who);
            log.setDescription("Updated company profile ID " + companyId + " (" + company.getName() + ")");
            log.setTimestamp(LocalDateTime.now());
            auditLogRepo.save(log);
        });

        return "redirect:/industry/companies";
    }

    // =========================================================
    //             End-of-internship evaluation (40%)
    // =========================================================
    // --- End-of-internship evaluation (40%) ---
    @GetMapping("/evaluations")
    public String listEvaluations(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // placements that need this supervisor's evaluation
        List<Placement> toEval = placementRepo.findReadyForSupervisorEvaluation(me);
        model.addAttribute("placements", toEval);

        // ---- NEW: resolve student + company details ----
        java.util.Set<Long> studentIds = new java.util.HashSet<>();
        java.util.Set<Long> companyIds = new java.util.HashSet<>();

        for (Placement p : toEval) {
            if (p.getStudentId() != null) studentIds.add(p.getStudentId());
            if (p.getCompanyId() != null) companyIds.add(p.getCompanyId());
        }

        java.util.Map<Long, com.example.itsystem.model.User> userById = new java.util.HashMap<>();
        java.util.Map<Long, com.example.itsystem.model.Company> companyById = new java.util.HashMap<>();

        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds)
                    .forEach(u -> userById.put(u.getId(), u));
        }
        if (!companyIds.isEmpty()) {
            companyRepository.findAllById(companyIds)
                    .forEach(c -> companyById.put(c.getId(), c));
        }

        model.addAttribute("userById", userById);
        model.addAttribute("companyById", companyById);

        return "industry-evaluations";
    }


    @GetMapping("/evaluations/{placementId}")
    public String evaluationForm(@PathVariable Long placementId,
                                 HttpSession session,
                                 Model model) {
        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(placementId)
                .orElse(null);
        if (placement == null) {
            return "redirect:/industry/evaluations";
        }

        // Security: ensure this placement belongs to this supervisor
        if (placement.getSupervisorUserId() == null ||
                !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/evaluations";
        }

        SupervisorEvaluation se = evalRepo.findByPlacementId(placementId)
                .orElseGet(() -> {
                    SupervisorEvaluation x = new SupervisorEvaluation();
                    x.setPlacementId(placementId);
                    return x;
                });

        model.addAttribute("placement", placement);

        // Optional: add student and company info for the view
        if (placement.getStudentId() != null) {
            userRepository.findById(placement.getStudentId()).ifPresent(u ->
                    model.addAttribute("studentName",
                            (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername()));
        }
        if (placement.getCompanyId() != null) {
            companyRepository.findById(placement.getCompanyId()).ifPresent(c ->
                    model.addAttribute("companyName", c.getName()));
        }

        model.addAttribute("form", se);
        return "eval_form"; // existing template; we’ll style later
    }

    @PostMapping("/evaluations/{placementId}")
    public String submitEvaluation(@PathVariable Long placementId,
                                   @ModelAttribute("form") @Valid SupervisorEvaluation form,
                                   HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";
        String who = currentDisplayName(session);

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(placementId)
                .orElse(null);
        if (placement == null) {
            return "redirect:/industry/evaluations";
        }

        // Security: ensure this placement belongs to this supervisor
        if (placement.getSupervisorUserId() == null ||
                !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/evaluations";
        }

        form.setPlacementId(placementId);
        form.setSubmittedAt(LocalDateTime.now());
        form.setSubmittedBy(who);
        evalRepo.save(form);

        AuditLog log = new AuditLog();
        log.setAction("EVALUATION_SUPERVISOR_SUBMIT");
        log.setUsername(who);
        log.setDescription("Submitted evaluation for placement " + placementId + " (40%)");
        log.setTimestamp(LocalDateTime.now());
        auditLogRepo.save(log);

        return "redirect:/industry/evaluations";
    }
}
