package com.example.itsystem.repository;

import com.example.itsystem.model.SelfReflection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SelfReflectionRepository extends JpaRepository<SelfReflection, Long> {

    List<SelfReflection> findByStudentIdOrderBySubmittedAtDesc(Long studentId);

    boolean existsByStudentId(Long studentId);

    Optional<SelfReflection> findFirstByStudentIdOrderBySubmittedAtDesc(Long studentId);
}