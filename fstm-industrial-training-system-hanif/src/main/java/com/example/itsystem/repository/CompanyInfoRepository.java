package com.example.itsystem.repository;

import com.example.itsystem.model.CompanyInfo;
import com.example.itsystem.model.CompanyInfoStatus;
import com.example.itsystem.model.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyInfoRepository extends JpaRepository<CompanyInfo, Long> {
    Optional<CompanyInfo> findByStudentId(Long studentId);

    // For Admin list/filter: /admin/company-info?status=...
    Page<CompanyInfo> findByStatus(CompanyInfoStatus status, Pageable pageable);

    // Session-aware admin list (defaults to CURRENT_SESSION)
    Page<CompanyInfo> findBySession(String session, Pageable pageable);
    Page<CompanyInfo> findByStatusAndSession(CompanyInfoStatus status, String session, Pageable pageable);

    // Admin list with optional status + session + department (department is derived from student's User.department)
    @Query("""
        select ci from CompanyInfo ci
        left join User s on s.id = ci.studentId
        where (:status is null or ci.status = :status)
          and (:session is null or ci.session = :session)
          and (:department is null or s.department = :department)
        order by ci.id desc
    """)
    Page<CompanyInfo> searchAdminCompanyInfo(@Param("status") CompanyInfoStatus status,
                                            @Param("session") String session,
                                            @Param("department") Department department,
                                            Pageable pageable);

    // For dashboard cards: pending count, etc.
    long countByStatus(CompanyInfoStatus status);

    Optional<CompanyInfo> findFirstByStudentIdOrderByIdDesc(Long studentId);
}
