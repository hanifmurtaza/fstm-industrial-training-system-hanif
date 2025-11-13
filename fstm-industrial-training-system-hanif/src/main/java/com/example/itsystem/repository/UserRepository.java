package com.example.itsystem.repository;

import com.example.itsystem.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByRole(String role);

    List<User> findByLecturer(User lecturer);

    List<User> findByLecturer_Id(Long lecturerId);

    Optional<User> findTopByUsernameAndRoleOrderByIdDesc(String username, String role);

    // (kept for backward compatibility, but prefer searchStudents below)
    Page<User> findByRoleAndNameContainingOrRoleAndStudentIdContaining(
            String role1, String search1, String role2, String search2, Pageable pageable);

    Page<User> findAllByRole(String role, Pageable pageable);

    List<User> findByLecturerAndSession(User lecturer, String session);
    @Query("select distinct u.session from User u where u.lecturer = :lecturer and u.session is not null order by u.session desc")
    List<String> findDistinctSessionsByLecturer(@Param("lecturer") User lecturer);

    Page<User> findAllByRoleAndSession(String role, String session, Pageable pageable);

    @Query("""
   SELECT u FROM User u
   WHERE u.role = :role AND u.session = :session AND (
     LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR
     LOWER(u.studentId) LIKE LOWER(CONCAT('%', :q, '%')) OR
     LOWER(COALESCE(u.company,'')) LIKE LOWER(CONCAT('%', :q, '%'))
   )
""")
    Page<User> searchStudentsBySession(@Param("role") String role,
                                       @Param("session") String session,
                                       @Param("q") String keyword,
                                       Pageable pageable);

    boolean existsByStudentId(String studentId);
    Optional<User> findByUsername(String username);


    // ✅ New: safe/clear search with explicit parentheses
    @Query("""
      SELECT u FROM User u
      WHERE u.role = :role
        AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%'))
          OR LOWER(u.studentId) LIKE LOWER(CONCAT('%', :term, '%')))
    """)
    Page<User> searchStudents(@Param("role") String role,
                              @Param("term") String term,
                              Pageable pageable);

    long countByRoleIgnoreCase(String role);
    long countByRole(String role);

}
