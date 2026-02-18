package com.example.itsystem.repository;

import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;
import com.example.itsystem.model.Department;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;


public interface PlacementRepository extends JpaRepository<Placement, Long> {

    boolean existsBySupervisorUserIdAndCompanyId(Long supervisorUserId, Long companyId);

    boolean existsByStudentIdAndStatusNot(Long studentId, PlacementStatus status);

    // ✅ Treat only these as "active" placements (avoid mismatches with REJECTED records)
    boolean existsByStudentIdAndStatusIn(Long studentId, List<PlacementStatus> statuses);



    // existing
    Page<Placement> findByStatus(PlacementStatus status, Pageable pageable);
    long countByStatus(PlacementStatus status);

    // Dashboard sector-filter helpers
    @Query("""
        select count(distinct p.studentId)
        from Placement p
        join Company c on c.id = p.companyId
        where p.status in :statuses
          and c.sector = :sector
    """)
    long countDistinctStudentsByCompanySectorAndStatusIn(@Param("sector") String sector,
                                                         @Param("statuses") List<PlacementStatus> statuses);

    @Query("""
        select count(p)
        from Placement p
        join Company c on c.id = p.companyId
        where p.status = :status
          and c.sector = :sector
    """)
    long countByStatusAndCompanySector(@Param("status") PlacementStatus status,
                                       @Param("sector") String sector);

    Page<Placement> findByStatusNot(PlacementStatus status, Pageable pageable);

    // --- Admin list with optional search + session (session is derived from the student's User.session) ---
    @Query("""
        select p from Placement p
        left join User s on s.id = p.studentId
        left join User sup on sup.id = p.supervisorUserId
        left join Company c on c.id = p.companyId
        where p.status <> com.example.itsystem.model.PlacementStatus.CANCELLED
          and (:status is null or p.status = :status)
          and (:session is null or s.session = :session)
          and (:department is null or s.department = :department)
          and (
               :q is null
               or lower(coalesce(s.name,'')) like lower(concat('%',:q,'%'))
               or lower(coalesce(s.username,'')) like lower(concat('%',:q,'%'))
               or lower(coalesce(s.studentId,'')) like lower(concat('%',:q,'%'))
               or lower(coalesce(sup.name,'')) like lower(concat('%',:q,'%'))
               or lower(coalesce(c.name,'')) like lower(concat('%',:q,'%'))
          )
        order by p.id desc
    """)
    Page<Placement> searchAdminPlacements(@Param("status") PlacementStatus status,
                                          @Param("q") String q,
                                          @Param("session") String session,
                                          @Param("department") Department department,
                                          Pageable pageable);


    // --- NEW: list placements for a specific supervisor (paged) ---
    Page<Placement> findBySupervisorUserIdAndStatus(Long supervisorUserId,
                                                    PlacementStatus status,
                                                    Pageable pageable);

    // Optional convenience if you need "All statuses" for a supervisor:
    Page<Placement> findBySupervisorUserId(Long supervisorUserId, Pageable pageable);

    Optional<Placement> findFirstByCompanyInfoId(Long companyInfoId);

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
    @Query("""
   select p.studentId from Placement p
   where p.supervisorUserId = :supervisorUserId
     and p.status in (
       com.example.itsystem.model.PlacementStatus.AWAITING_SUPERVISOR,
       com.example.itsystem.model.PlacementStatus.AWAITING_ADMIN,
       com.example.itsystem.model.PlacementStatus.APPROVED
     )
""")
    List<Long> findStudentIdsBySupervisor(@Param("supervisorUserId") Long supervisorUserId);


    // Check whether a given student is supervised by this industry user
    @Query("""
   select (count(p) > 0) from Placement p
   where p.supervisorUserId = :supervisorUserId
     and p.studentId = :studentId
     and p.status in (
       com.example.itsystem.model.PlacementStatus.AWAITING_SUPERVISOR,
       com.example.itsystem.model.PlacementStatus.AWAITING_ADMIN,
       com.example.itsystem.model.PlacementStatus.APPROVED
     )
""")
    boolean existsByStudentIdAndSupervisorUserId(@Param("studentId") Long studentId,
                                                 @Param("supervisorUserId") Long supervisorUserId);


    @Query("""
   select distinct p.companyId from Placement p
   where p.supervisorUserId = :supervisorUserId
     and p.companyId is not null
     and p.status in (
       com.example.itsystem.model.PlacementStatus.AWAITING_SUPERVISOR,
       com.example.itsystem.model.PlacementStatus.AWAITING_ADMIN,
       com.example.itsystem.model.PlacementStatus.APPROVED
     )
""")
    List<Long> findDistinctCompanyIdsBySupervisorUserId(@Param("supervisorUserId") Long supervisorUserId);


    List<Placement> findBySupervisorUserIdAndStatus(Long supervisorUserId, PlacementStatus status);

    Optional<Placement> findTopByStudentIdOrderByIdDesc(Long studentId);

    Optional<Placement> findTopByStudentIdAndStatusOrderByIdDesc(Long studentId, PlacementStatus status);

    @Query("select p.studentId from Placement p where p.companyId = :companyId and p.status = :status")
    List<Long> findStudentIdsByCompanyAndStatus(@Param("companyId") Long companyId,
                                                @Param("status") PlacementStatus status);




}
