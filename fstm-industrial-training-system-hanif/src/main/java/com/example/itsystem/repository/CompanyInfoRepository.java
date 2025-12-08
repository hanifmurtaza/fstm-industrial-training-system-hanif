package com.example.itsystem.repository;

import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.model.CompanyInfoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyInfoRepository extends JpaRepository<CompanyInfo, Long> {
    Optional<CompanyInfo> findByStudentId(Long studentId);

    // For Admin list/filter: /admin/company-info?status=...
    Page<CompanyInfo> findByStatus(CompanyInfoStatus status, Pageable pageable);

    // For dashboard cards: pending count, etc.
    long countByStatus(CompanyInfoStatus status);

    Optional<CompanyInfo> findFirstByStudentIdOrderByIdDesc(Long studentId);
}
