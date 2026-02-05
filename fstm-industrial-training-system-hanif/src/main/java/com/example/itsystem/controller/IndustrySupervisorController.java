package com.example.itsystem.controller;

import com.example.itsystem.model.*;
import com.example.itsystem.repository.*;
import com.example.itsystem.service.StudentAssessmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


import static org.springframework.format.annotation.DateTimeFormat.ISO;

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
    private final CompanyInfoRepository companyInfoRepo;


    public IndustrySupervisorController(LogbookEntryRepository logbookRepo,
                                        AuditLogRepository auditLogRepo,
                                        PlacementRepository placementRepo,
                                        SupervisorEvaluationRepository evalRepo,
                                        UserRepository userRepository,
                                        CompanyRepository companyRepository,
                                        StudentAssessmentService studentAssessmentService,
                                        CompanyInfoRepository companyInfoRepo) {
        this.logbookRepo = logbookRepo;
        this.auditLogRepo = auditLogRepo;
        this.placementRepo = placementRepo;
        this.evalRepo = evalRepo;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.studentAssessmentService = studentAssessmentService;
        this.companyInfoRepo = companyInfoRepo;
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

    private String safeDisplayName(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return "Industry Supervisor";
        String name = auth.getName();
        if (name == null || name.trim().isEmpty() || "anonymousUser".equalsIgnoreCase(name)) {
            return "Industry Supervisor";
        }
        return name;
    }

    // =========================
    // Dashboard
    // =========================
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        // ✅ display name + company name for UI
        model.addAttribute("displayName",
                (user.getName() != null && !user.getName().isBlank())
                        ? user.getName()
                        : (user.getUsername() != null ? user.getUsername() : "Industry Supervisor"));

        model.addAttribute("companyName",
                (user.getCompany() != null && !user.getCompany().isBlank())
                        ? user.getCompany()
                        : "—");

        // 1) Find company by supervisor's company name
        Company company = null;
        if (user.getCompany() != null && !user.getCompany().isBlank()) {
            company = companyRepository.findByNameIgnoreCase(user.getCompany().trim()).orElse(null);
        }

        // If no company -> all 0
        if (company == null) {
            model.addAttribute("pending", 0);
            model.addAttribute("approved", 0);
            model.addAttribute("rejected", 0);
            return "industry-dashboard";
        }

        // 2) Get studentIds under this company (APPROVED placements)
        List<Long> studentIds = placementRepo
                .findStudentIdsByCompanyAndStatus(company.getId(), PlacementStatus.APPROVED);

        // If no students -> all 0
        if (studentIds == null || studentIds.isEmpty()) {
            model.addAttribute("pending", 0);
            model.addAttribute("approved", 0);
            model.addAttribute("rejected", 0);
            return "industry-dashboard";
        }

        // 3) Count logbooks only for those students
        long pending  = logbookRepo.countByStudentIdInAndStatus(studentIds, ReviewStatus.PENDING);
        long approved = logbookRepo.countByStudentIdInAndStatus(studentIds, ReviewStatus.APPROVED);
        long rejected = logbookRepo.countByStudentIdInAndStatus(studentIds, ReviewStatus.REJECTED);

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

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";


        String whoName = currentDisplayName(session);

        User meUser = userRepository.findById(me).orElse(null);
        if (meUser != null) {
            String real = (meUser.getName() != null && !meUser.getName().isBlank())
                    ? meUser.getName()
                    : meUser.getUsername();
            if (real != null && !real.isBlank()) whoName = real;
        }

        final String who = whoName; // ✅ important: makes it work inside lambda


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
            e.setEndorsed(newStatus == ReviewStatus.APPROVED);
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
    public String listPlacements(@RequestParam(value = "status", required = false) String status,
                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                 @RequestParam(value = "size", defaultValue = "10") int size,
                                 HttpSession session,
                                 Model model) {

        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        // Safe parse: null/""/"ALL"/invalid -> show ALL
        PlacementStatus st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            try { st = PlacementStatus.valueOf(status.trim()); }
            catch (Exception ignore) { st = null; }
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Placement> pageObj = (st == null)
                ? placementRepo.findBySupervisorUserId(me, pageable)
                : placementRepo.findBySupervisorUserIdAndStatus(me, st, pageable);

        List<Placement> items = pageObj.getContent();

        // Resolve names
        Set<Long> studentIds = new HashSet<>();
        Set<Long> companyIds = new HashSet<>();
        for (Placement p : items) {
            if (p.getStudentId() != null) studentIds.add(p.getStudentId());
            if (p.getCompanyId() != null) companyIds.add(p.getCompanyId());
        }

        Map<Long, String> studentNames = new HashMap<>();
        Map<Long, String> companyNames = new HashMap<>();

        if (!studentIds.isEmpty()) {
            userRepository.findAllById(studentIds).forEach(u -> {
                String nm = (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getUsername();
                studentNames.put(u.getId(), nm);
            });
        }
        if (!companyIds.isEmpty()) {
            companyRepository.findAllById(companyIds).forEach(c -> companyNames.put(c.getId(), c.getName()));
        }

        model.addAttribute("placements", pageObj); // keep if your template uses it
        model.addAttribute("items", items);
        model.addAttribute("studentNames", studentNames);
        model.addAttribute("companyNames", companyNames);

        model.addAttribute("status", (st == null ? "ALL" : st.name()));
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageObj.getTotalPages());
        model.addAttribute("statuses", PlacementStatus.values());

        return "industry-placements";
    }

    @GetMapping("/placements/{id}")
    public String placementEdit(@PathVariable Long id, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(id).orElse(null);
        if (placement == null) return "redirect:/industry/placements";

        // security: must belong to this supervisor
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


        CompanyInfo companyInfo = null;
        if (placement.getCompanyInfoId() != null) {
            companyInfo = companyInfoRepo.findById(placement.getCompanyInfoId()).orElse(null);
        }
        model.addAttribute("companyInfo", companyInfo);


        model.addAttribute("sectorOptions", CompanySector.values());


        return "industry-placement-edit";
    }

    @PostMapping("/placements/{id}/verify")
    @Transactional
    public String verifyPlacement(@PathVariable Long id,
                                  @RequestParam String department,
                                  @RequestParam String jobScope,
                                  @RequestParam(required = false) String allowance,
                                  @RequestParam(defaultValue = "false") boolean accommodation,
                                  @RequestParam(defaultValue = "false") boolean followJobScopeAgreement,
                                  @RequestParam(required = false) String workingHours,
                                  @RequestParam(required = false) String benefits,
                                  @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate reportDutyDate,
                                  @RequestParam(required = false) String sector,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes ra,
                                  HttpSession session) {

        if (notIndustry(session)) return "redirect:/login";

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        String who = currentDisplayName(session);

        Placement p = placementRepo.findById(id).orElse(null);
        if (p == null) return "redirect:/industry/placements";

        // security: must belong to this supervisor
        if (p.getSupervisorUserId() == null || !p.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/placements";
        }

        // Faculty requirement: supervisor must explicitly agree to follow the job scope declared in the system.
        if (!followJobScopeAgreement) {
            ra.addFlashAttribute("error",
                    "Please tick the agreement to confirm you will provide tasks aligned with the job scope for the duration of the placement.");
            return "redirect:/industry/placements/" + id;
        }

        p.setDepartment(department);
        p.setJobScope(jobScope);
        p.setAllowance(allowance);
        p.setAccommodation(accommodation);

        // new fields
        p.setFollowJobScopeAgreement(true);
        p.setWorkingHours(workingHours);
        p.setBenefits(benefits);

        p.setReportDutyDate(reportDutyDate);

        // ✅ main behavior: verified -> go to admin
        p.setStatus(PlacementStatus.AWAITING_ADMIN);
        placementRepo.save(p);

        AuditLog log = new AuditLog();
        log.setAction("PLACEMENT_VERIFY");
        log.setUsername(who);
        log.setDescription("Verified placement " + id + " and set status to AWAITING_ADMIN");
        log.setTimestamp(LocalDateTime.now());
        auditLogRepo.save(log);

        if (p.getCompanyInfoId() != null && sector != null && !sector.isBlank()) {
            CompanyInfo info = companyInfoRepo.findById(p.getCompanyInfoId()).orElse(null);
            if (info != null) {
                try {
                    info.setSector(CompanySector.valueOf(sector.trim()));
                    companyInfoRepo.save(info);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return "redirect:/industry/placements";
    }


    // =========================
    // Companies
    // =========================
    @GetMapping("/companies")
    public String listCompanies(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        // ✅ Primary design: company linked to this supervisor account
        List<Company> companies = new ArrayList<>();

// 1) Prefer companyId link (strongest)
        if (user.getCompanyId() != null) {
            companyRepository.findById(user.getCompanyId()).ifPresent(companies::add);
        }

// 2) Legacy: fallback to company name string
        if (companies.isEmpty() && user.getCompany() != null && !user.getCompany().isBlank()) {
            Company own = companyRepository.findByNameIgnoreCase(user.getCompany().trim()).orElse(null);
            if (own != null) companies.add(own);
        }

// 3) fallback (optional): if not linked, use placements-derived
        if (companies.isEmpty()) {
            Long me = currentUserId(session);
            List<Long> companyIds = placementRepo.findDistinctCompanyIdsBySupervisorUserId(me);
            if (companyIds != null && !companyIds.isEmpty()) {
                companies.addAll(companyRepository.findAllById(companyIds));
            }
        }

        model.addAttribute("companies", companies);
        return "industry-companies";

    }


    @GetMapping("/companies/{companyId}/edit")
    public String editCompany(@PathVariable Long companyId, HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";

        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        boolean allowed = (user.getCompanyId() != null && user.getCompanyId().equals(companyId));

// Legacy: allow if matched by name string
        if (!allowed && user.getCompany() != null && !user.getCompany().isBlank()) {
            Company own = companyRepository.findByNameIgnoreCase(user.getCompany().trim()).orElse(null);
            allowed = (own != null && own.getId().equals(companyId));
        }

// fallback allow if linked via placements (optional)
        if (!allowed) {
            Long me = currentUserId(session);
            allowed = placementRepo.existsBySupervisorUserIdAndCompanyId(me, companyId);
        }

        if (!allowed) return "redirect:/industry/companies";


        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) return "redirect:/industry/companies";

        model.addAttribute("company", company);
        return "industry-company-edit";
    }


    @PostMapping("/companies/{companyId}/edit")
    public String updateCompany(@PathVariable Long companyId,
                                @RequestParam String name,
                                @RequestParam(required = false) String sector,
                                @RequestParam(required = false) String address,
                                @RequestParam(required = false) String defaultJobScope,
                                @RequestParam(required = false) BigDecimal typicalAllowance,
                                @RequestParam(required = false) Boolean accommodation,
                                @RequestParam(required = false) String contactName,
                                @RequestParam(required = false) String contactEmail,
                                @RequestParam(required = false) String contactPhone,
                                @RequestParam(required = false) String website,
                                HttpSession session,
                                RedirectAttributes ra) {

        if (notIndustry(session)) return "redirect:/login";

        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        boolean allowed = (user.getCompanyId() != null && user.getCompanyId().equals(companyId));

// Legacy: allow if matched by name string
        if (!allowed && user.getCompany() != null && !user.getCompany().isBlank()) {
            Company own = companyRepository.findByNameIgnoreCase(user.getCompany().trim()).orElse(null);
            allowed = (own != null && own.getId().equals(companyId));
        }

// fallback allow if linked via placements (optional)
        if (!allowed) {
            Long me = currentUserId(session);
            allowed = placementRepo.existsBySupervisorUserIdAndCompanyId(me, companyId);
        }

        if (!allowed) return "redirect:/industry/companies";


        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            ra.addFlashAttribute("error", "Company not found.");
            return "redirect:/industry/companies";
        }

        company.setName(name);
        company.setAddress(address);
        company.setDefaultJobScope(defaultJobScope);
        company.setTypicalAllowance(typicalAllowance);
        company.setAccommodation(accommodation);
        company.setContactName(contactName);
        company.setContactEmail(contactEmail);
        company.setContactPhone(contactPhone);
        company.setWebsite(website);

        // keep enum string normalized
        if (sector == null || sector.isBlank()) company.setSector(null);
        else {
            try { company.setSector(CompanySector.valueOf(sector.trim()).name()); }
            catch (Exception ex) { company.setSector(CompanySector.OTHERS.name()); }
        }

        Company saved = companyRepository.save(company);

        user.setCompany(saved.getName());
        session.setAttribute("user", user);

        // ✅ keep User.company string in sync for supervisors linked by companyId
        List<User> linked = userRepository.findByRole("industry").stream()
                .filter(u -> u.getCompanyId() != null && u.getCompanyId().equals(saved.getId()))
                .toList(); // if Java < 16, use Collectors.toList()

        for (User u : linked) {
            u.setCompany(saved.getName());
        }
        userRepository.saveAll(linked);

        ra.addFlashAttribute("success", "Company profile updated.");
        return "redirect:/industry/companies";
    }




    // =========================
    // Evaluations (40%)
    // =========================
    @GetMapping("/evaluations")
    public String listEvaluations(HttpSession session, Model model) {
        if (notIndustry(session)) return "redirect:/login";
        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        List<Placement> placements = placementRepo.findBySupervisorUserIdAndStatus(me, PlacementStatus.APPROVED);
        model.addAttribute("placements", placements);

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

        // HARD LOCK FLAG: submitted already?
        boolean alreadySubmitted = evalRepo.findByPlacementId(placementId).isPresent();
        // (or: boolean alreadySubmitted = evalRepo.existsByPlacementId(placementId); if you add that method)

        model.addAttribute("placement", placement);
        model.addAttribute("alreadySubmitted", alreadySubmitted);

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
                                   HttpSession session,
                                   RedirectAttributes ra) {

        if (notIndustry(session)) return "redirect:/login";
        String who = currentDisplayName(session);

        Long me = currentUserId(session);
        if (me == null) return "redirect:/login";

        Placement placement = placementRepo.findById(placementId).orElse(null);
        if (placement == null) return "redirect:/industry/evaluations";

        if (placement.getSupervisorUserId() == null || !placement.getSupervisorUserId().equals(me)) {
            return "redirect:/industry/evaluations";
        }

        // ✅ HARD LOCK: if already submitted, do NOT allow re-submit
        if (evalRepo.findByPlacementId(placementId).isPresent()) {
            ra.addFlashAttribute("toast", "Evaluation already submitted. Editing is not allowed.");
            return "redirect:/industry/evaluations/" + placementId;
        }

        // Persist (first submission only)
        form.setPlacementId(placementId);
        form.setSubmittedAt(LocalDateTime.now());
        form.setSubmittedBy(who);
        evalRepo.save(form);

        // ✅ Sync to StudentAssessment using OFFICIAL rubric:
        try {
            Long studentId = placement.getStudentId();
            if (studentId != null) {
                User student = userRepository.findById(studentId).orElse(null);
                String sessionStr = (student != null && student.getSession() != null && !student.getSession().isBlank())
                        ? student.getSession().trim()
                        : fallbackSession();

                int b30 = 0;
                b30 += clamp(Optional.ofNullable(form.getDisciplineAttendance()).orElse(1), 1, 5);
                b30 += clamp(Optional.ofNullable(form.getKnowledgeSkills()).orElse(1), 1, 5);
                b30 += clamp(Optional.ofNullable(form.getAttitudeInitiative()).orElse(1), 1, 5);
                b30 += clamp(Optional.ofNullable(form.getInterestResponsibility()).orElse(1), 1, 5);
                b30 += clamp(Optional.ofNullable(form.getCommunicationCooperation()).orElse(1), 1, 5);
                b30 += clamp(Optional.ofNullable(form.getProfessionalAppearance()).orElse(1), 1, 5);

                int c10 = clamp(Optional.ofNullable(form.getOverallMark10()).orElse(0), 0, 10);

                studentAssessmentService.saveIndustrySupervisorOfficialScores(
                        studentId,
                        sessionStr,
                        me,
                        BigDecimal.valueOf(b30),
                        BigDecimal.valueOf(c10),
                        true
                );
            }
        } catch (Exception ignore) {}

        AuditLog log = new AuditLog();
        log.setAction("EVALUATION_SUPERVISOR_SUBMIT");
        log.setUsername(who);
        log.setDescription("Submitted evaluation for placement " + placementId + " (40%)");
        log.setTimestamp(LocalDateTime.now());
        auditLogRepo.save(log);

        ra.addFlashAttribute("toast", "Evaluation submitted successfully.");
        return "redirect:/industry/evaluations";
    }

}
