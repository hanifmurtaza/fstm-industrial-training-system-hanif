package com.example.itsystem.repository;

import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;


public interface PlacementRepository extends JpaRepository<Placement, Long> {

    boolean existsBySupervisorUserIdAndCompanyId(Long supervisorUserId, Long companyId);


    // existing
    Page<Placement> findByStatus(PlacementStatus status, Pageable pageable);
    long countByStatus(PlacementStatus status);

    // --- NEW: list placements for a specific supervisor (paged) ---
    Page<Placement> findBySupervisorUserIdAndStatus(Long supervisorUserId,
                                                    PlacementStatus status,
                                                    Pageable pageable);

    // Optional convenience if you need "All statuses" for a supervisor:
    Page<Placement> findBySupervisorUserId(Long supervisorUserId, Pageable pageable);

    java.util.Optional<Placement> findFirstByCompanyInfoId(Long companyInfoId);

    // --- NEW: items that are approved and still missing a supervisor evaluation ---
    @Query("""
       select p from Placement p
       where p.supervisorUserId = :supervisorId
         and p.status = com.example.itsystem.model.PlacementStatus.APPROVED
         and not exists (
               select 1 from SupervisorEvaluation e
               where e.placementId = p.id
         )
       order by p.reportDutyDate desc
    """)
    List<Placement> findReadyForSupervisorEvaluation(@Param("supervisorId") Long supervisorId);

    // Get all student IDs supervised by this industry user
    @Query("select p.studentId from Placement p where p.supervisorUserId = :supervisorUserId")
    List<Long> findStudentIdsBySupervisor(@Param("supervisorUserId") Long supervisorUserId);

    // Check whether a given student is supervised by this industry user
    @Query("select (count(p) > 0) from Placement p " +
            "where p.supervisorUserId = :supervisorUserId and p.studentId = :studentId")
    boolean existsByStudentIdAndSupervisorUserId(@Param("studentId") Long studentId,
                                                 @Param("supervisorUserId") Long supervisorUserId);

    @Query("select distinct p.companyId from Placement p where p.supervisorUserId = :supervisorUserId and p.companyId is not null")
    List<Long> findDistinctCompanyIdsBySupervisorUserId(@Param("supervisorUserId") Long supervisorUserId);


    List<Placement> findBySupervisorUserIdAndStatus(Long supervisorUserId, PlacementStatus status);

    Optional<Placement> findTopByStudentIdOrderByIdDesc(Long studentId);




}
