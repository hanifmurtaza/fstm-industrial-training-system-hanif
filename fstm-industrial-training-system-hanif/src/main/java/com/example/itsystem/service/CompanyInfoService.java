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
        return repo.findByStudentId(studentId);
    }

    public CompanyInfo saveOrUpdate(CompanyInfo info) {
        return repo.save(info);
    }
}
