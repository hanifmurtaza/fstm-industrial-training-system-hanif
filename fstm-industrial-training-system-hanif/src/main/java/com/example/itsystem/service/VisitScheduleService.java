package com.example.itsystem.service;

import com.example.itsystem.model.VisitSchedule;
import java.util.List;

public interface VisitScheduleService {
    void saveSchedule(VisitSchedule schedule);
    List<VisitSchedule> findByLecturerId(Long lecturerId);

    VisitSchedule findUpcomingForStudent(Long studentId);
    VisitSchedule findById(Long id);
    void deleteById(Long id);
}
