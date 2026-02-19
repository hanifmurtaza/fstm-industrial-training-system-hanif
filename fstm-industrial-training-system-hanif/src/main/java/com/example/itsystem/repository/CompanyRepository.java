package com.example.itsystem.repository;

import com.example.itsystem.model.Company;
import com.example.itsystem.model.MalaysiaState;
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

    // Sector filter for Company Master page
    Page<Company> findBySector(String sector, Pageable pageable);

    Page<Company> findByNameContainingIgnoreCaseAndSector(String q, String sector, Pageable pageable);

    // Unified search for Company Master page (optional q/sector/state)
    @Query("""
            select c from Company c
            where (:q is null or trim(:q) = '' or lower(c.name) like lower(concat('%', :q, '%')))
              and (:sector is null or trim(:sector) = '' or c.sector = :sector)
              and (:state is null or c.state = :state)
            """)
    Page<Company> searchCompanyMaster(@Param("q") String q,
                                     @Param("sector") String sector,
                                     @Param("state") MalaysiaState state,
                                     Pageable pageable);

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
