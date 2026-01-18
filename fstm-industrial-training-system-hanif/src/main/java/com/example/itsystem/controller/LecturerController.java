package com.example.itsystem.controller;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.repository.LogbookEntryRepository;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.VisitScheduleRepository;
import com.example.itsystem.service.UserService;
import com.example.itsystem.service.VisitScheduleService;
import com.example.itsystem.service.impl.LecturerDashboardService;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.itsystem.model.VisitEvaluation;
import com.example.itsystem.repository.VisitEvaluationRepository;
import java.time.LocalDateTime;

import java.util.*;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {

    @Autowired private UserService userService;
    @Autowired private VisitScheduleService visitScheduleService;
    @Autowired private UserRepository userRepository;
    @Autowired private LogbookEntryRepository logbookEntryRepository;
    @Autowired private VisitEvaluationRepository visitEvaluationRepository;

    // ★ 仪表服务（KPI & 周统计）
    @Autowired private LecturerDashboardService dashboardService;
    @Autowired private VisitScheduleRepository visitScheduleRepository;


    /* ========== 新增：VL 首页（前端：templates/lecturer/home.html） ========== */
    @GetMapping("/home")
    public String lecturerHome(HttpSession session, Model model) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }
        // ✅ 用 userName 这个 key，跟页面保持一致
        model.addAttribute("userName", lecturer.getName());
        // 选中导航高亮（可选）
        model.addAttribute("activePage", "home");
        // 你的 home.html 放在 templates 根目录就返回 "home"
        return "home";
    }

    @GetMapping("/lecturer/schedule")
    public String scheduleVisit(HttpSession session, Model model) {

        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        long pendingReschedule =
                visitScheduleRepository.countByLecturerIdAndRescheduleRequestedTrue(lecturer.getId());

        model.addAttribute("hasRescheduleAlert", pendingReschedule > 0);
        model.addAttribute("activePage", "schedule");

        return "lecturer/schedule-visit";
    }

    /* ========== 新增：KPI 统计接口（前端 fetch 用） ========== */
    @GetMapping("/dashboard/kpis")
    @ResponseBody
    public Map<String, Integer> kpis(HttpSession session) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return Map.of("pendingLogbook", 0, "students", 0, "unvisitedSchedules", 0, "pendingEvaluations", 0);
        }
        return dashboardService.kpis(lecturer.getId());
    }

    /* ========== 新增：周图数据（type=this|next） ========== */
    @GetMapping("/dashboard/weeks")
    @ResponseBody
    public Map<String, Object> weeks(@RequestParam(defaultValue = "this") String type,
                                     HttpSession session) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return Map.of("labels", List.of(), "counts", List.of(), "range", "");
        }
        boolean next = "next".equalsIgnoreCase(type);
        return dashboardService.weekSummary(lecturer.getId(), next);
    }

    // ===================== 下面保留你原有的方法（未改动） =====================

    @GetMapping("/schedule")
    public String showSchedulePage(HttpSession session, Model model) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }
        List<User> students = userService.getStudentsAssignedToLecturer(lecturer.getId());
        model.addAttribute("students", students);
        model.addAttribute("visitSchedule", new VisitSchedule());
        return "lecturer/schedule";
    }

    @PostMapping("/schedule")
    public String submitSchedule(@ModelAttribute VisitSchedule visitSchedule,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }
        visitSchedule.setLecturerId(lecturer.getId());
        visitSchedule.setStatus("Pending");
        visitScheduleService.saveSchedule(visitSchedule);
        redirectAttributes.addFlashAttribute("successMessage", "Visit scheduled successfully.");
        return "redirect:/lecturer/schedule";
    }

    @GetMapping("/schedule/list")
    public String viewVisitList(HttpSession session, Model model) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }
        List<VisitSchedule> visitList = visitScheduleService.findByLecturerId(lecturer.getId());
        List<User> students = userService.getStudentsAssignedToLecturer(lecturer.getId());
        Map<Long, String> studentNameMap = new HashMap<>();
        for (User s : students) {
            studentNameMap.put(s.getId(), s.getName());
        }
        model.addAttribute("visitList", visitList);
        model.addAttribute("studentNameMap", studentNameMap);
        return "lecturer/schedule-list";
    }

    @GetMapping("/schedule/edit/{id}")
    public String editVisit(@PathVariable Long id, Model model, HttpSession session) {
        User lecturer = (User) session.getAttribute("user");
        VisitSchedule visit = visitScheduleService.findById(id);
        if (!visit.getLecturerId().equals(lecturer.getId())) return "redirect:/lecturer/schedule/list";

        List<User> students = userService.getStudentsAssignedToLecturer(lecturer.getId());
        model.addAttribute("students", students);
        model.addAttribute("visitSchedule", visit);
        return "lecturer/schedule-edit";
    }

    @PostMapping("/schedule/update")
    public String updateVisit(@ModelAttribute VisitSchedule visitSchedule,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User lecturer = (User) session.getAttribute("user");
        visitSchedule.setLecturerId(lecturer.getId());
        visitSchedule.setStatus("Pending");
        visitScheduleService.saveSchedule(visitSchedule);
        redirectAttributes.addFlashAttribute("successMessage", "Visit updated.");
        return "redirect:/lecturer/schedule/list";
    }

    @GetMapping("/schedule/delete/{id}")
    public String deleteVisit(@PathVariable Long id,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User lecturer = (User) session.getAttribute("user");
        VisitSchedule visit = visitScheduleService.findById(id);
        if (visit != null && visit.getLecturerId().equals(lecturer.getId())) {
            visitScheduleService.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Visit deleted.");
        }
        return "redirect:/lecturer/schedule/list";
    }

    @GetMapping("/students")
    public String viewMyStudents(
            @RequestParam(name = "session", required = false) String sessionFilter,
            HttpSession httpSession,
            Model model) {

        User lecturer = (User) httpSession.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        model.addAttribute("lecturer", lecturer.getName());
        model.addAttribute("activePage", "students"); // ★ 高亮菜单

        List<String> sessions = userRepository.findDistinctSessionsByLecturer(lecturer);
        model.addAttribute("sessions", sessions);

        List<User> students = (sessionFilter != null && !sessionFilter.isBlank())
                ? userRepository.findByLecturerAndSession(lecturer, sessionFilter)
                : userRepository.findByLecturer(lecturer);

        model.addAttribute("students", students);
        model.addAttribute("selectedSession", sessionFilter == null ? "" : sessionFilter);

        return "lecturer/students";
    }




    @GetMapping("/logbook/{studentId}")
    public String viewStudentLogbooks(@PathVariable Long studentId, Model model, HttpSession session) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }
        User student = userRepository.findById(studentId).orElse(null);
        if (student == null || student.getLecturer() == null || !student.getLecturer().getId().equals(lecturer.getId())) {
            return "redirect:/lecturer/students";
        }
        List<LogbookEntry> logbooks = logbookEntryRepository.findByStudentId(studentId);
        model.addAttribute("student", student);
        model.addAttribute("logbooks", logbooks);
        return "lecturer/lecturer-logbook";
    }



    @GetMapping("/logbook/text/{id}")
    @ResponseBody
    public String getLogbookText(@PathVariable Long id) {
        Optional<LogbookEntry> optionalEntry = logbookEntryRepository.findById(id);
        if (optionalEntry.isEmpty()) return "";
        LogbookEntry entry = optionalEntry.get();
        StringBuilder text = new StringBuilder();
        if (entry.getChallenges() != null) text.append(entry.getChallenges()).append(". ");
        if (entry.getMainTask() != null) text.append(entry.getMainTask()).append(". ");
        if (entry.getResult() != null) text.append(entry.getResult()).append(". ");
        if (entry.getSkills() != null) text.append(entry.getSkills());
        return text.toString().trim();
    }

    @GetMapping("/logbook/endorsement-stats")
    @ResponseBody
    public Map<String, Integer> getEndorsementStats(HttpSession session) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return Map.of("Endorsed", 0, "Pending", 0);
        }
        List<LogbookEntry> entries = logbookEntryRepository.findByLecturerId(lecturer.getId());
        int endorsed = 0, pending = 0;
        for (LogbookEntry entry : entries) {
            if (entry.isEndorsedByLecturer()) {
                endorsed++;
            } else {
                pending++;
            }
        }
        Map<String, Integer> stats = new HashMap<>();
        stats.put("Endorsed", endorsed);
        stats.put("Pending", pending);
        return stats;
    }
    @GetMapping("/evaluation/form")
    public String evaluationForm(@RequestParam Long visitId,
                                 @RequestParam Long studentId,
                                 @RequestParam(required = false) String session,
                                 HttpSession httpSession,
                                 Model model) {

        User lecturer = (User) httpSession.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        VisitEvaluation evaluation = visitEvaluationRepository
                .findFirstByVisitId(visitId)
                .orElseGet(() -> {
                    VisitEvaluation ev = new VisitEvaluation();
                    ev.setVisitId(visitId);
                    ev.setStudentId(studentId);
                    ev.setLecturerId(lecturer.getId());
                    return ev;
                });

        model.addAttribute("evaluation", evaluation);
        model.addAttribute("selectedSession", session == null ? "" : session);

        return "lecturer/evaluation-form";
    }



}