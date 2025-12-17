package com.example.itsystem.repository;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.model.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;



@Repository
public interface LogbookEntryRepository extends JpaRepository<LogbookEntry, Long> {

    // --- basic finders (common in both versions) ---
    List<LogbookEntry> findByLecturerId(Long lecturerId);
    List<LogbookEntry> findByStudentId(Long studentId);
    List<LogbookEntry> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    // --- paging helpers (from first version) ---
    Page<LogbookEntry> findByStatus(ReviewStatus status, Pageable pageable);
    Page<LogbookEntry> findByStudentId(Long studentId, Pageable pageable);
    Page<LogbookEntry> findByStudentIdAndStatus(Long studentId, ReviewStatus status, Pageable pageable);

    long countByStatus(ReviewStatus status);

    // --- queues for review flow (from first version) ---
    Page<LogbookEntry> findByStatusAndEndorsedFalse(ReviewStatus status, Pageable pageable);
    Page<LogbookEntry> findByEndorsedTrueAndEndorsedByLecturerFalse(Pageable pageable);

    long countByStatusAndEndorsedFalse(ReviewStatus status);
    long countByEndorsedTrueAndEndorsedByLecturerFalse();

    // --- extra counters (from second version) ---
    // count un-endorsed logs for a list of students
    long countByStudentIdInAndEndorsedFalse(List<Long> studentIds);

    // count un-endorsed logs for a specific lecturer
    long countByLecturerIdAndEndorsedFalse(Long lecturerId);

    @Query("""
   select l from LogbookEntry l
   where l.studentId in :studentIds
     and (:status is null or l.status = :status)
     and (:studentIdFilter is null or l.studentId = :studentIdFilter)
   order by l.weekStartDate desc, l.createdAt desc
""")
    Page<LogbookEntry> findForSupervisor(@Param("studentIds") List<Long> studentIds,
                                         @Param("status") ReviewStatus status,
                                         @Param("studentIdFilter") Long studentIdFilter,
                                         Pageable pageable);


    @Query("""
   select
     l.studentId as studentId,
     count(l.id) as total,
     sum(case when l.endorsed = false then 1 else 0 end) as supPending,
     sum(case when l.endorsed = true and l.endorsedByLecturer = false then 1 else 0 end) as lecPending,
     max(l.weekStartDate) as latestWeek
   from LogbookEntry l
   where l.studentId in :studentIds
   group by l.studentId
""")
    List<StudentLogbookSummary> summarizeByStudentIds(@Param("studentIds") List<Long> studentIds);


}
