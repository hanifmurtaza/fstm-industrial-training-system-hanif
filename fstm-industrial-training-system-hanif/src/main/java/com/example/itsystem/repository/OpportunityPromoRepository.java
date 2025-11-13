package com.example.itsystem.repository;

import com.example.itsystem.model.OpportunityPromo;
import com.example.itsystem.model.PromoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityPromoRepository extends JpaRepository<OpportunityPromo, Long> {

    Page<OpportunityPromo> findBySupervisorUserId(Long supervisorUserId, Pageable pageable);

    Page<OpportunityPromo> findBySupervisorUserIdAndStatus(
            Long supervisorUserId, PromoStatus status, Pageable pageable);

    Page<OpportunityPromo> findByStatus(PromoStatus status, Pageable pageable);

    List<OpportunityPromo> findTop5ByStatusOrderByCreatedAtDesc(PromoStatus status);
}
