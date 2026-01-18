package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Timestamp;

@Entity
@Table(name = "visit_schedule")
public class VisitSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================
    //        Foreign Keys
    // ==========================
    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long lecturerId;

    // ==========================
    //        Visit Details
    // ==========================
    private LocalDate visitDate;

    private LocalTime visitTime;

    /**
     * Visit mode:
     * - Physical
     * - Virtual
     */
    private String mode;

    /**
     * Visit status:
     * - Pending
     * - Confirmed
     * - RescheduleRequested
     */
    private String status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ==========================
    //     Reschedule Reminder
    // ==========================
    /**
     * Whether student has requested to reschedule
     * and Visiting Lecturer has NOT handled it yet.
     *
     * Used to trigger 🔴 red dot reminder on VL UI.
     */
    @Column(name = "reschedule_requested", nullable = false)
    private boolean rescheduleRequested = false;

    // ==========================
    //        Audit Fields
    // ==========================
    private Timestamp createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // ==========================
    //      Getters & Setters
    // ==========================
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getLecturerId() {
        return lecturerId;
    }

    public void setLecturerId(Long lecturerId) {
        this.lecturerId = lecturerId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public LocalTime getVisitTime() {
        return visitTime;
    }

    public void setVisitTime(LocalTime visitTime) {
        this.visitTime = visitTime;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isRescheduleRequested() {
        return rescheduleRequested;
    }

    public void setRescheduleRequested(boolean rescheduleRequested) {
        this.rescheduleRequested = rescheduleRequested;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}