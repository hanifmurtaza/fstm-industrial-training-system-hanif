package com.example.itsystem.repository;

import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlacementRepository extends JpaRepository<Placement, Long> {

    // existing
    Page<Placement> findByStatus(PlacementStatus status, Pageable pageable);
    long countByStatus(PlacementStatus status);

    // --- NEW: list placements for a specific supervisor (paged) ---
    Page<Placement> findBySupervisorUserIdAndStatus(Long supervisorUserId,
                                                    PlacementStatus status,
                                                    Pageable pageable);

    // Optional convenience if you need "All statuses" for a supervisor:
    Page<Placement> findBySupervisorUserId(Long supervisorUserId, Pageable pageable);

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
}
