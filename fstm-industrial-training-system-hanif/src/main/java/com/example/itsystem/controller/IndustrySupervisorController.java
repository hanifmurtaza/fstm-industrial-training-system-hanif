package com.example.itsystem.controller;

import com.example.itsystem.model.*;
import com.example.itsystem.repository.*;
import com.example.itsystem.service.StudentAssessmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final StudentAssessmentService studentAssessmentService;

    public IndustrySupervisorController(LogbookEntryRepository logbookRepo,
                                        AuditLogRepository auditLogRepo,
                                        PlacementRepository placementRepo,
                                        SupervisorEvaluationRepository evalRepo,
                                        UserRepository userRepository,
                                        CompanyRepository companyRepository,
                                        StudentAssessmentService studentAssessmentService) {
        this.logbookRepo = logbookRepo;
        this.auditLogRepo = auditLogRepo;
        this.placementRepo = placementRepo;
        this.evalRepo = evalRepo;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.studentAssessmentService = studentAssessmentService;
    }

    // =========================
    // Helpers
    // =========================
    private boolean notIndustry(HttpSession session) {
        Object auth = session.getAttribute("auth");
        if (auth instanceof Map<?, ?> m) {
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

    private String fallbackSession() {
        return "2024/2025-2";
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 1..5 -> 0..maxOut */
    private static int scaleFrom5To(int score1to5, int maxOut) {
        int s = clamp(score1to5, 1, 5);
        double out = ((double) (s - 1) / 4.0) * (double) maxOut;
        return (int) Math.round(out);
    }

    // =========================
    // Dashboard
    // =========================
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

    // =========================
    // Logbooks
    // =========================
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

        List<Long> myStudentIds = placementRepo.findStudentIdsBySupervisor(me);
        if (myStudentIds == null || myStudentIds.isEmpty()) {
            model.addAttribute("entries", Page.empty());
            model.addAttribute("status", status);
            model.addAttribute("studentId", studentIdFilter);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", 0);
            model.addAttribute("statuses", ReviewStatus.values());
            return "industry-logbooks";
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<LogbookEntry> entries = logbookRepo.findForSupervisor(
                myStudentIds,
                status,
                studentIdFilter,
                pageable
        );

        Set<Long> studentIds = new HashSet<>();
        for (LogbookEntry e : entries.getContent()) {
            if (e.getStudentId() != null) studentIds.add(e.getStudentId());
        }

        Map<Long, User> studentById = new HashMap<>();
        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds).forEach(u -> studentById.put(u.getId(), u));
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

        boolean owned = placementRepo.existsByStudentIdAndSupervisorUserId(entry.getStudentId(), me);
        if (!owned) return "redirect:/industry/logbooks";

        User student = (entry.getStudentId() == null) ? null
                : userRepository.findById(entry.getStudentId()).orElse(null);

        model.addAttribute("entry", entry);
        model.addAttribute("student", student);
        model.addAttribute("statuses", ReviewStatus.values());
        return "industry-logbook-view";
    }

    @PostMapping("/logbooks/{id}/review")
    public String reviewLogbook(@PathVariable Long id,
                                @RequestParam("action") String action,
                                @RequestParam(value = "comment", required = false) String comment,
                                HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";

        String who = currentDisplayName(session);
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        logbookRepo.findById(id).ifPresent(e -> {
            boolean owned = placementRepo.existsByStudentIdAndSupervisorUserId(e.getStudentId(), me);
            if (!owned) return;

            ReviewStatus newStatus = "approve".equalsIgnoreCase(action)
                    ? ReviewStatus.APPROVED
                    : ReviewStatus.REJECTED;

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

    // =========================
    // Placements
    // =========================
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
        Page<Placement> placements = placementRepo.findBySupervisorUserIdAndStatus(me, status, pageable);

        model.addAttribute("placements", placements);
        model.addAttribute("status", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", placements.getTotalPages());
        model.addAttribute("statuses", PlacementStatus.values());

        return "industry-placements";
    }

    @GetMapping("/placements/{id}")
    public String placementView(@PathVariable Long id, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(id).orElse(null);
        if (placement == null) return "redirect:/industry/placements";

        if (placement.getSupervisorUserId() == null || !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/placements";
        }

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

        return "industry-placement-view";
    }

    @PostMapping("/placements/{id}/verify")
    public String verifyPlacement(@PathVariable Long id,
                                  @RequestParam("action") String action,
                                  @RequestParam(value = "comment", required = false) String comment,
                                  HttpSession session) {

        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        String who = currentDisplayName(session);

        Placement placement = placementRepo.findById(id).orElse(null);
        if (placement == null) return "redirect:/industry/placements";

        if (placement.getSupervisorUserId() == null || !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/placements";
        }

        placement.setStatus("approve".equalsIgnoreCase(action) ? PlacementStatus.APPROVED : PlacementStatus.REJECTED);
        placementRepo.save(placement);

        AuditLog log = new AuditLog();
        log.setAction("PLACEMENT_VERIFY");
        log.setUsername(who);
        log.setDescription("Placement " + id + " -> " + placement.getStatus()
                + (comment != null && !comment.isBlank() ? " | " + comment : ""));
        log.setTimestamp(LocalDateTime.now());
        auditLogRepo.save(log);

        return "redirect:/industry/placements";
    }

    // =========================
    // Companies
    // =========================
    @GetMapping("/companies")
    public String listCompanies(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        List<Long> companyIds = placementRepo.findDistinctCompanyIdsBySupervisorUserId(me);

        List<Company> companies = (companyIds == null || companyIds.isEmpty())
                ? List.of()
                : companyRepository.findAllById(companyIds);

        // Optional: keep stable order (same as IDs order)
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < companyIds.size(); i++) order.put(companyIds.get(i), i);
        companies.sort(Comparator.comparingInt(c -> order.getOrDefault(c.getId(), 999999)));

        model.addAttribute("companies", companies);
        return "industry-companies";
    }


    @GetMapping("/companies/{companyId}")
    public String editCompany(@PathVariable Long companyId, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // ✅ access control
        if (!placementRepo.existsBySupervisorUserIdAndCompanyId(me, companyId)) {
            return "redirect:/industry/companies";
        }

        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) return "redirect:/industry/companies";

        model.addAttribute("company", company);
        return "industry-company-edit";
    }


    @PostMapping("/companies/{companyId}")
    public String updateCompany(@PathVariable Long companyId,
                                @RequestParam String name,
                                @RequestParam(required = false) String address,
                                @RequestParam(required = false) String contactName,
                                @RequestParam(required = false) String contactEmail,
                                @RequestParam(required = false) String contactPhone,
                                @RequestParam(required = false) String website,
                                HttpSession session) {

        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";
        String who = currentDisplayName(session);

        // ✅ access control
        if (!placementRepo.existsBySupervisorUserIdAndCompanyId(me, companyId)) {
            return "redirect:/industry/companies";
        }

        companyRepository.findById(companyId).ifPresent(company -> {
            company.setName(name);
            company.setAddress(address);
            company.setContactName(contactName);
            company.setContactEmail(contactEmail);
            company.setContactPhone(contactPhone);
            company.setWebsite(website);
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


    // =========================
    // End-of-internship evaluation (40%)
    // =========================
    @GetMapping("/evaluations")
    public String listEvaluations(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // ✅ Show ALL placements assigned to this supervisor (approved placements)
        List<Placement> placements = placementRepo.findBySupervisorUserIdAndStatus(me, PlacementStatus.APPROVED);
        model.addAttribute("placements", placements);

        // Build lookup maps for Student + Company names
        Set<Long> studentIds = new HashSet<>();
        Set<Long> companyIds = new HashSet<>();
        List<Long> placementIds = new ArrayList<>();

        for (Placement p : placements) {
            placementIds.add(p.getId());
            if (p.getStudentId() != null) studentIds.add(p.getStudentId());
            if (p.getCompanyId() != null) companyIds.add(p.getCompanyId());
        }

        Map<Long, User> userById = new HashMap<>();
        Map<Long, Company> companyById = new HashMap<>();

        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds).forEach(u -> userById.put(u.getId(), u));
        }
        if (!companyIds.isEmpty()) {
            companyRepository.findAllById(companyIds).forEach(c -> companyById.put(c.getId(), c));
        }

        // ✅ Find which placements already have evaluation
        Map<Long, SupervisorEvaluation> evalByPlacementId = new HashMap<>();
        if (!placementIds.isEmpty()) {
            List<SupervisorEvaluation> evals = evalRepo.findByPlacementIdIn(placementIds);
            for (SupervisorEvaluation e : evals) {
                evalByPlacementId.put(e.getPlacementId(), e);
            }
        }

        model.addAttribute("userById", userById);
        model.addAttribute("companyById", companyById);
        model.addAttribute("evalByPlacementId", evalByPlacementId);

        return "industry-evaluations";
    }


    @GetMapping("/evaluations/{placementId}")
    public String evaluationForm(@PathVariable Long placementId, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(placementId).orElse(null);
        if (placement == null) return "redirect:/industry/evaluations";

        if (placement.getSupervisorUserId() == null || !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/evaluations";
        }

        SupervisorEvaluation se = evalRepo.findByPlacementId(placementId)
                .orElseGet(() -> {
                    SupervisorEvaluation x = new SupervisorEvaluation();
                    x.setPlacementId(placementId);
                    return x;
                });

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

        model.addAttribute("form", se);
        return "eval_form";
    }

    @PostMapping("/evaluations/{placementId}")
    public String submitEvaluation(@PathVariable Long placementId,
                                   @ModelAttribute("form") @Valid SupervisorEvaluation form,
                                   HttpSession session) {

        if (notIndustry(session)) return "redirect:/login";
        String who = currentDisplayName(session);

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(placementId).orElse(null);
        if (placement == null) return "redirect:/industry/evaluations";

        if (placement.getSupervisorUserId() == null || !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/evaluations";
        }

        // Save supervisor evaluation record
        form.setPlacementId(placementId);
        form.setSubmittedAt(LocalDateTime.now());
        form.setSubmittedBy(who);
        evalRepo.save(form);

        // Sync to StudentAssessment (for Student Dashboard)
        try {
            Long studentId = placement.getStudentId();
            if (studentId != null) {
                User student = userRepository.findById(studentId).orElse(null);
                String sessionStr = (student != null && student.getSession() != null && !student.getSession().isBlank())
                        ? student.getSession().trim()
                        : fallbackSession();

                int skills20 = 0;
                if (form.getDiscipline() != null && form.getKnowledge() != null
                        && form.getInitiative() != null && form.getGrooming() != null) {
                    int avg = (int) Math.round(
                            (form.getDiscipline() + form.getKnowledge() + form.getInitiative() + form.getGrooming()) / 4.0
                    );
                    skills20 = scaleFrom5To(avg, 20);
                }

                int comm10 = (form.getCommunication() != null) ? scaleFrom5To(form.getCommunication(), 10) : 0;
                int team10 = (form.getTeamwork() != null) ? scaleFrom5To(form.getTeamwork(), 10) : 0;

                studentAssessmentService.saveIndustrySupervisorScores(
                        studentId,
                        sessionStr,
                        me,
                        BigDecimal.valueOf(skills20),
                        BigDecimal.valueOf(comm10),
                        BigDecimal.valueOf(team10),
                        true
                );
            }
        } catch (Exception ignore) {
            // don't block submission
        }

        AuditLog log = new AuditLog();
        log.setAction("EVALUATION_SUPERVISOR_SUBMIT");
        log.setUsername(who);
        log.setDescription("Submitted evaluation for placement " + placementId + " (40%)");
        log.setTimestamp(LocalDateTime.now());
        auditLogRepo.save(log);

        return "redirect:/industry/evaluations";
    }

    // Backward-compatible edit URL: /industry/companies/{id}/edit
    @GetMapping("/companies/{companyId}/edit")
    public String editCompanyEditPath(@PathVariable Long companyId, HttpSession session, Model model) {
        return editCompany(companyId, session, model); // reuse your existing method
    }

    @PostMapping("/companies/{companyId}/edit")
    public String updateCompanyEditPath(@PathVariable Long companyId,
                                        @RequestParam String name,
                                        @RequestParam(required = false) String address,
                                        @RequestParam(required = false) String contactName,
                                        @RequestParam(required = false) String contactEmail,
                                        @RequestParam(required = false) String contactPhone,
                                        @RequestParam(required = false) String website,
                                        HttpSession session) {
        return updateCompany(companyId, name, address, contactName, contactEmail, contactPhone, website, session);
    }

}
