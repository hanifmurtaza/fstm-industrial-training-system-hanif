package com.example.itsystem.repository;

import java.time.LocalDate;

public interface StudentLogbookSummary {
    Long getStudentId();
    Long getTotal();
    Long getSupPending();
    Long getLecPending();
    LocalDate getLatestWeek();
}
