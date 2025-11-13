package com.example.itsystem.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Minimal public card payload for the login-page feed */
public record PromoCardDTO(
        Long id,
        String title,
        String companyName,
        String location,
        String roleType,
        String workMode,
        String imageUrl,
        String description,
        String externalLink,
        String contactName,
        String contactEmail,
        String contactPhone,
        LocalDate deadline,
        Instant createdAt
) {}
