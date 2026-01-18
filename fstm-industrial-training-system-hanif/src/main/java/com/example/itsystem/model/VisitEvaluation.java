package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "visit_evaluation")
public class VisitEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long visitId;
    private Long lecturerId;
    private Long studentId;

    // ===== Part A: Visit Details (record, not scored) =====
    private LocalDate visitDate;

    @Column(length = 20)
    private String visitMode; // Physical / Online

    @Column(columnDefinition = "TEXT")
    private String discussionSummary; // summary of discussions with student & supervisor

    @Column(columnDefinition = "TEXT")
    private String overallObservation; // on-site observation

    // ===== Part B1: Student Reflection (Qualitative comments) =====
    @Column(columnDefinition = "TEXT")
    private String roleUnderstandingComment;

    @Column(columnDefinition = "TEXT")
    private String learningUnderstandingComment;

    // ===== Part B: Likert scores (1-5) =====
    // B1.2 Reflection Likert -> /10 (x2)
    private Integer reflectionLikert; // 1-5

    // B2 Engagement -> /10 (x2)
    private Integer engagementLikert; // 1-5

    // B3 Placement suitability -> /5
    private Integer placementSuitabilityLikert; // 1-5

    // B4 Logbook evaluation -> /5
    private Integer logbookLikert; // 1-5

    // B5 Lecturer overall evaluation -> /10 (x2)
    private Integer lecturerOverallLikert; // 1-5

    // ===== Optional (not scored) =====
    @Column(columnDefinition = "TEXT")
    private String additionalRemarks;

    // ===== Persisted total for student dashboard display =====
    private Integer totalScore40; // 0-40

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    /* ----------------- helper ----------------- */
    public void recalcTotalScore40() {
        int reflection10 = safeLikert(reflectionLikert) * 2;          // 2..10
        int engagement10 = safeLikert(engagementLikert) * 2;          // 2..10
        int suitability5 = safeLikert(placementSuitabilityLikert);    // 1..5
        int logbook5 = safeLikert(logbookLikert);                     // 1..5
        int overall10 = safeLikert(lecturerOverallLikert) * 2;        // 2..10

        this.totalScore40 = reflection10 + engagement10 + suitability5 + logbook5 + overall10;
    }

    private int safeLikert(Integer v) {
        if (v == null) return 0;
        if (v < 1) return 0;
        if (v > 5) return 5;
        return v;
    }

    /* ----------------- getters/setters ----------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }

    public Long getLecturerId() { return lecturerId; }
    public void setLecturerId(Long lecturerId) { this.lecturerId = lecturerId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public String getVisitMode() { return visitMode; }
    public void setVisitMode(String visitMode) { this.visitMode = visitMode; }

    public String getDiscussionSummary() { return discussionSummary; }
    public void setDiscussionSummary(String discussionSummary) { this.discussionSummary = discussionSummary; }

    public String getOverallObservation() { return overallObservation; }
    public void setOverallObservation(String overallObservation) { this.overallObservation = overallObservation; }

    public String getRoleUnderstandingComment() { return roleUnderstandingComment; }
    public void setRoleUnderstandingComment(String roleUnderstandingComment) { this.roleUnderstandingComment = roleUnderstandingComment; }

    public String getLearningUnderstandingComment() { return learningUnderstandingComment; }
    public void setLearningUnderstandingComment(String learningUnderstandingComment) { this.learningUnderstandingComment = learningUnderstandingComment; }

    public Integer getReflectionLikert() { return reflectionLikert; }
    public void setReflectionLikert(Integer reflectionLikert) { this.reflectionLikert = reflectionLikert; }

    public Integer getEngagementLikert() { return engagementLikert; }
    public void setEngagementLikert(Integer engagementLikert) { this.engagementLikert = engagementLikert; }

    public Integer getPlacementSuitabilityLikert() { return placementSuitabilityLikert; }
    public void setPlacementSuitabilityLikert(Integer placementSuitabilityLikert) { this.placementSuitabilityLikert = placementSuitabilityLikert; }

    public Integer getLogbookLikert() { return logbookLikert; }
    public void setLogbookLikert(Integer logbookLikert) { this.logbookLikert = logbookLikert; }

    public Integer getLecturerOverallLikert() { return lecturerOverallLikert; }
    public void setLecturerOverallLikert(Integer lecturerOverallLikert) { this.lecturerOverallLikert = lecturerOverallLikert; }

    public String getAdditionalRemarks() { return additionalRemarks; }
    public void setAdditionalRemarks(String additionalRemarks) { this.additionalRemarks = additionalRemarks; }

    public Integer getTotalScore40() { return totalScore40; }
    public void setTotalScore40(Integer totalScore40) { this.totalScore40 = totalScore40; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}