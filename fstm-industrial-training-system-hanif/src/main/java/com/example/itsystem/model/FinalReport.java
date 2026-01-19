package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class FinalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;

    private String reportFilename;

    @Column(length = 500)
    private String videoLink;  // ✅ NEW: store URL instead of file

    private LocalDateTime submittedAt = LocalDateTime.now();

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getReportFilename() { return reportFilename; }
    public void setReportFilename(String reportFilename) { this.reportFilename = reportFilename; }

    public String getVideoLink() { return videoLink; }
    public void setVideoLink(String videoLink) { this.videoLink = videoLink; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}