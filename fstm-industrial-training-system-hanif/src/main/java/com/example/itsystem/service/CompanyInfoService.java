package com.example.itsystem.service;

import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.repository.CompanyInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CompanyInfoService {

    @Autowired
    private CompanyInfoRepository repo;

    public Optional<CompanyInfo> findByStudentId(Long studentId) {
        // IMPORTANT: a student may have multiple submissions over time (e.g., change company mid-internship).
        // Always return the latest submission to avoid updating/reading an older record by accident.
        return repo.findFirstByStudentIdOrderByIdDesc(studentId);
    }

    public CompanyInfo saveOrUpdate(CompanyInfo info) {
        return repo.save(info);
    }
}
