package com.example.itsystem.service.impl;

import com.example.itsystem.model.User;
import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.repository.LogbookEntryRepository;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.repository.VisitEvaluationRepository;
import com.example.itsystem.repository.VisitScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LecturerDashboardService {

    private final UserRepository userRepo;
    private final LogbookEntryRepository logbookRepo;
    private final VisitScheduleRepository scheduleRepo;
    private final VisitEvaluationRepository evaluationRepo;

    public LecturerDashboardService(UserRepository userRepo,
                                    LogbookEntryRepository logbookRepo,
                                    VisitScheduleRepository scheduleRepo,
                                    VisitEvaluationRepository evaluationRepo) {
        this.userRepo = userRepo;
        this.logbookRepo = logbookRepo;
        this.scheduleRepo = scheduleRepo;
        this.evaluationRepo = evaluationRepo;
    }

    /** 4 个 KPI：待批 logbook / 学生数 / 未拜访日程 / 待 evaluation */
    public Map<String, Integer> kpis(Long lecturerId) {
        Map<String, Integer> out = new LinkedHashMap<>();

        // 讲师名下学生（用 lecturer.id 这个派生属性最稳）
        List<User> students = userRepo.findByLecturer_Id(lecturerId)
                .stream()
                .filter(u -> "student".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());
        List<Long> studentIds = students.stream().map(User::getId).toList();

        // 1) 待批 logbook
        long pendingLog = studentIds.isEmpty()
                ? 0
                : logbookRepo.countByStudentIdInAndEndorsedFalse(studentIds);

        // 如果你的 logbook 表里有 lecturer_id，也可直接用：
        // long pendingLog = logbookRepo.countByLecturerIdAndEndorsedFalse(lecturerId);

        // 2) 学生总数
        int totalStudents = students.size();

        // 3) 未拜访日程：状态 Pending/Confirmed，日期 >= 今天
        List<String> active = Arrays.asList("Pending", "Confirmed");
        long unvisited = scheduleRepo.countByLecturerIdAndStatusInAndVisitDateGreaterThanEqual(
                lecturerId, active, LocalDate.now());

        // 4) 待 evaluation：在“确认”的日程里，没有评价记录的
        List<VisitSchedule> conf = scheduleRepo.findByLecturerIdAndVisitDateBetween(
                lecturerId, LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        long pendingEval = conf.stream()
                .filter(v -> "Confirmed".equalsIgnoreCase(v.getStatus()))
                .filter(v -> !evaluationRepo.existsByVisitId(v.getId()))
                .count();

        out.put("pendingLogbook", (int) pendingLog);
        out.put("students", totalStudents);
        out.put("unvisitedSchedules", (int) unvisited);
        out.put("pendingEvaluations", (int) pendingEval);
        return out;
    }

    /** 本周/下周日程的按天数量 */
    public Map<String, Object> weekSummary(Long lecturerId, boolean nextWeek) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(DayOfWeek.MONDAY);
        if (nextWeek) start = start.plusWeeks(1);
        LocalDate end = start.plusDays(6);

        List<VisitSchedule> list = scheduleRepo.findByLecturerIdAndVisitDateBetween(
                lecturerId, start, end);

        Map<DayOfWeek, Long> grouped = list.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getVisitDate().getDayOfWeek(),
                        Collectors.counting()
                ));

        String[] labels = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        List<Integer> counts = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            DayOfWeek dow = DayOfWeek.of(i + 1);
            counts.add(grouped.getOrDefault(dow, 0L).intValue());
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Object> dto = new HashMap<>();
        dto.put("labels", labels);
        dto.put("counts", counts);
        dto.put("range", fmt.format(start) + " ~ " + fmt.format(end));
        return dto;
    }
}
