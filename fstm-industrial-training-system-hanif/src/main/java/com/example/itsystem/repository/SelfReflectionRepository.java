package com.example.itsystem.repository;

import com.example.itsystem.model.SelfReflection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SelfReflectionRepository extends JpaRepository<SelfReflection, Long> {
    List<SelfReflection> findByStudentIdOrderBySubmittedAtDesc(Long studentId);
}
