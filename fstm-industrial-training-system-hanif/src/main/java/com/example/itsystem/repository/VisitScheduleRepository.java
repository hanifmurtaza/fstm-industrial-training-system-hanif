package com.example.itsystem.repository;

import com.example.itsystem.model.VisitSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface VisitScheduleRepository extends JpaRepository<VisitSchedule, Long> {

    List<VisitSchedule> findByLecturerId(Long lecturerId);

    VisitSchedule findFirstByStudentIdOrderByVisitDateAscVisitTimeAsc(Long studentId);

    Optional<VisitSchedule> findTopByStudentIdOrderByIdDesc(Long studentId);


    // 统计：未拜访的日程（状态 in，且日期>=today）
    long countByLecturerIdAndStatusInAndVisitDateGreaterThanEqual(
            Long lecturerId, Collection<String> status, LocalDate date
    );

    // 区间内的日程（用于周图表）
    List<VisitSchedule> findByLecturerIdAndVisitDateBetween(
            Long lecturerId, LocalDate start, LocalDate end
    );
}