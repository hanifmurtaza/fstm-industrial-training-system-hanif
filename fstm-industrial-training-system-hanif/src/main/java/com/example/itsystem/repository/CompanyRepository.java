package com.example.itsystem.repository;

import com.example.itsystem.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Used by Admin list search (/admin/company-master?q=...)
    Page<Company> findByNameContainingIgnoreCase(String q, Pageable pageable);

    // Used by "Promote to Master" to dedupe on name
    Optional<Company> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
