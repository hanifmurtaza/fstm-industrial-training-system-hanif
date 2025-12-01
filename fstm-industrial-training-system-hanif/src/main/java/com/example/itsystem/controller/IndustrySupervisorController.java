package com.example.itsystem.controller;

import com.example.itsystem.model.AuditLog;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    // --- Dashboard ---
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

    // --- Logbooks ---
    @GetMapping("/logbooks")
    public String listLogbooks(@RequestParam(value = "status", required = false) ReviewStatus status,
                               @RequestParam(value = "studentId", required = false) Long studentId,
                               @RequestParam(value = "page", defaultValue = "0") int page,
                               @RequestParam(value = "size", defaultValue = "10") int size,
                               HttpSession session,
                               Model model) {

        if (notIndustry(session)) return "redirect:/login";

        Pageable pageable = PageRequest.of(page, size);
        Page<LogbookEntry> entries;

        if (studentId != null && status != null) {
            entries = logbookRepo.findByStudentIdAndStatus(studentId, status, pageable);
        } else if (studentId != null) {
            entries = logbookRepo.findByStudentId(studentId, pageable);
        } else if (status != null) {
            entries = logbookRepo.findByStatus(status, pageable);
        } else {
            entries = logbookRepo.findAll(pageable);
        }

        model.addAttribute("entries", entries);
        model.addAttribute("status", status);
        model.addAttribute("studentId", studentId);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", entries.getTotalPages());
        model.addAttribute("statuses", ReviewStatus.values());
        return "industry-logbooks";
    }

    @GetMapping("/logbooks/{id}")
    public String viewLogbook(@PathVariable Long id, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Optional<LogbookEntry> opt = logbookRepo.findById(id);
        if (opt.isEmpty()) return "redirect:/industry/logbooks";
        model.addAttribute("entry", opt.get());
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

        logbookRepo.findById(id).ifPresent(e -> {
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

    // --- Placements (verify by supervisor) ---
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
        PlacementStatus st = (status != null) ? status : PlacementStatus.AWAITING_SUPERVISOR;

        Page<Placement> items = placementRepo.findBySupervisorUserIdAndStatus(me, st, pageable);

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
                            (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername()));
        }
        if (!companyIds.isEmpty()) {
            companyRepository.findAllById(companyIds).forEach(c -> companyNames.put(c.getId(), c.getName()));
        }

        model.addAttribute("items", items);
        model.addAttribute("status", st);
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

    // --- End-of-internship evaluation (40%) ---
    @GetMapping("/evaluations")
    public String listEvaluations(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        List<Placement> toEval = placementRepo.findReadyForSupervisorEvaluation(me);
        model.addAttribute("placements", toEval);
        return "industry-evaluations";
    }

    @GetMapping("/evaluations/{placementId}")
    public String evaluationForm(@PathVariable Long placementId, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        var se = evalRepo.findByPlacementId(placementId)
                .orElseGet(() -> { var x = new SupervisorEvaluation(); x.setPlacementId(placementId); return x; });
        model.addAttribute("form", se);
        return "eval_form";
    }


    @PostMapping("/evaluations/{placementId}")
    public String submitEvaluation(@PathVariable Long placementId,
                                   @ModelAttribute("form") @Valid SupervisorEvaluation form,
                                   HttpSession session) {
        if (notIndustry(session)) return "redirect:/login";
        String who = currentDisplayName(session);

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
