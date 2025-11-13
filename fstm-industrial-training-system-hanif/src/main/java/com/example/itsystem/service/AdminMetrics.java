package com.example.itsystem.service;

public record AdminMetrics(
        double verifiedPlacementPct,
        double evalCompletionPct,
        double avgEndorsementLagDays,
        long studentsCount,
        long companiesCount
) {}
