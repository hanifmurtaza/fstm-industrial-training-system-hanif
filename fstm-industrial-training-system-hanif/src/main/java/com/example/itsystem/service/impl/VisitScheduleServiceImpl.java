package com.example.itsystem.service.impl;

import com.example.itsystem.model.VisitSchedule;
import com.example.itsystem.repository.VisitScheduleRepository;
import com.example.itsystem.service.VisitScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VisitScheduleServiceImpl implements VisitScheduleService {

    @Autowired
    private VisitScheduleRepository visitScheduleRepository;

    @Override
    public void saveSchedule(VisitSchedule schedule) {
        visitScheduleRepository.save(schedule);
    }
    @Override
    public List<VisitSchedule> findByLecturerId(Long lecturerId) {
        return visitScheduleRepository.findByLecturerId(lecturerId);
    }

    @Override
    public VisitSchedule findUpcomingForStudent(Long studentId) {
        // 始终拿该学生最新创建的一条预约记录
        return visitScheduleRepository.findTopByStudentIdOrderByIdDesc(studentId);
    }


    @Override
    public VisitSchedule findById(Long id) {
        return visitScheduleRepository.findById(id).orElse(null);
    }

    @Override
    public void deleteById(Long id) {
        visitScheduleRepository.deleteById(id);
    }


}