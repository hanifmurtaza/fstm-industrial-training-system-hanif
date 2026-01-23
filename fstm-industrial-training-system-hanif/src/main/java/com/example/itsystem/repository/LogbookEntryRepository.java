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


    @Query(value = """
SELECT
  student_id AS studentId,
  COUNT(id) AS total,
  SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS supPending,
  SUM(CASE WHEN status <> 'REJECTED'
            AND (endorsed_by_lecturer = b'0' OR endorsed_by_lecturer IS NULL)
       THEN 1 ELSE 0 END) AS lecPending,
  MAX(week_start_date) AS latestWeek
FROM logbook_entries
WHERE student_id IN (:studentIds)
GROUP BY student_id
""", nativeQuery = true)
    List<StudentLogbookSummaryNative> summarizeNativeByStudentIds(@Param("studentIds") List<Long> studentIds);



    @Query(value = """
SELECT COUNT(*)
FROM logbook_entries
WHERE status <> 'REJECTED'
  AND (endorsed_by_lecturer = b'0' OR endorsed_by_lecturer IS NULL)
""", nativeQuery = true)
    long countAwaitingLecturer();

    long countByStudentIdInAndStatus(List<Long> studentIds, ReviewStatus status);



}
