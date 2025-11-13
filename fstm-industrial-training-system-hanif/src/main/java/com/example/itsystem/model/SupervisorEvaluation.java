package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "supervisor_evaluations",
    uniqueConstraints = {
        // one evaluation per placement (supervisor side)
        @UniqueConstraint(name = "uq_supervisor_eval_placement", columnNames = "placementId")
    },
    indexes = {
        @Index(name = "idx_sup_eval_placement", columnList = "placementId")
    }
)
public class SupervisorEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Simple FK by id (keeps your current pattern)
    @Column(nullable = false)
    private Long placementId;

    // --- Rubric fields (example 1–5) ---
    @Column(nullable = false) private Integer discipline;
    @Column(nullable = false) private Integer knowledge;
    @Column(nullable = false) private Integer communication;
    @Column(nullable = false) private Integer initiative;
    @Column(nullable = false) private Integer grooming;

    // Overall numeric (e.g., 0–100)
    @Column(nullable = false) private Integer overallScore;

    // Hire recommendation
    @Column(nullable = false) private Boolean hireRecommendation = false;

    @Lob
    private String comments;

    // Audit
    @Column(nullable = false) private LocalDateTime submittedAt = LocalDateTime.now();
    @Column(nullable = false, length = 120) private String submittedBy;

    // ---- getters/setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlacementId() { return placementId; }
    public void setPlacementId(Long placementId) { this.placementId = placementId; }

    public Integer getDiscipline() { return discipline; }
    public void setDiscipline(Integer discipline) { this.discipline = discipline; }

    public Integer getKnowledge() { return knowledge; }
    public void setKnowledge(Integer knowledge) { this.knowledge = knowledge; }

    public Integer getCommunication() { return communication; }
    public void setCommunication(Integer communication) { this.communication = communication; }

    public Integer getInitiative() { return initiative; }
    public void setInitiative(Integer initiative) { this.initiative = initiative; }

    public Integer getGrooming() { return grooming; }
    public void setGrooming(Integer grooming) { this.grooming = grooming; }

    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }

    public Boolean getHireRecommendation() { return hireRecommendation; }
    public void setHireRecommendation(Boolean hireRecommendation) { this.hireRecommendation = hireRecommendation; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
}
