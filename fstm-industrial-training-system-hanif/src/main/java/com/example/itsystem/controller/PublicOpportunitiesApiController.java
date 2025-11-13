package com.example.itsystem.controller;

import com.example.itsystem.model.OpportunityPromo;
import com.example.itsystem.model.PromoStatus;
import com.example.itsystem.repository.OpportunityPromoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/opportunities")
public class PublicOpportunitiesApiController {

    private final OpportunityPromoRepository promoRepo;

    public PublicOpportunitiesApiController(OpportunityPromoRepository promoRepo) {
        this.promoRepo = promoRepo;
    }

    // GET /api/opportunities?page=0&size=6
    @GetMapping
    public Page<OpportunityPromo> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "6") int size) {
        return promoRepo.findByStatus(
                PromoStatus.PUBLISHED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    // GET /api/opportunities/top5
    @GetMapping("/top5")
    public List<OpportunityPromo> top5() {
        // Either derived query if you created it:
        // return promoRepo.findTop5ByStatusOrderByCreatedAtDesc(PromoStatus.PUBLISHED);
        return promoRepo.findByStatus(
                PromoStatus.PUBLISHED,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }
}
