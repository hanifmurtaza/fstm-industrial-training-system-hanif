package com.example.itsystem.repository;

import com.example.itsystem.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.itsystem.model.PlacementStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Used by Admin list search (/admin/company-master?q=...)
    Page<Company> findByNameContainingIgnoreCase(String q, Pageable pageable);

    // Used by "Promote to Master" to dedupe on name
    Optional<Company> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
   select c.id as companyId,
          c.name as companyName,
          count(distinct p.studentId) as totalStudents
   from Company c
   left join Placement p
          on p.companyId = c.id
         and p.status = :status
   group by c.id, c.name
   order by c.name asc
""")
    List<CompanyStudentCountRow> findCompanyStudentCounts(@Param("status") PlacementStatus status);

}
