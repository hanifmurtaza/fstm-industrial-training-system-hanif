package com.example.itsystem.repository;

import com.example.itsystem.model.FinalReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinalReportRepository extends JpaRepository<FinalReport, Long> {
    List<FinalReport> findByStudentId(Long studentId);
}
