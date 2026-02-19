package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "supervisor_evaluations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_supervisor_eval_placement", columnNames = "placementId")
        },
        indexes = {
                @Index(name = "idx_sup_eval_placement", columnList = "placementId")
        }
)
public class SupervisorEvaluation {

    public enum InternshipNature { STRUCTURED, FLEXIBLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long placementId;

    // =========================
    // OFFICIAL RUBRIC (2026)
    // Section A (record only): Nature of internship
    // Section B: 6 attributes x 5 marks = 30
    // Section C: overall mark /10 + free comment
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InternshipNature internshipNature;

    // Section B (0–5 each)
    @Column private Integer disciplineAttendance;      // Disiplin & Kehadiran
    @Column private Integer knowledgeSkills;           // Pengetahuan & Kemahiran
    @Column private Integer attitudeInitiative;        // Sikap & Inisiatif
    @Column private Integer interestResponsibility;    // Minat & Tanggungjawab
    @Column private Integer communicationCooperation;  // Komunikasi & Kerjasama
    @Column private Integer professionalAppearance;    // Penampilan Profesional

    // Section C
    @Column private Integer overallMark10;             // /10

    @Lob
    private String overallComment;

    // -------------------------
    // Legacy fields (kept to avoid breaking old DB rows; no longer used in UI)
    // -------------------------
    @Deprecated @Column private Integer discipline;
    @Deprecated @Column private Integer knowledge;
    @Deprecated @Column private Integer communication;
    @Deprecated @Column private Integer initiative;
    @Deprecated @Column private Integer grooming;
    @Deprecated @Column private Integer teamwork;
    @Deprecated @Column private Integer overallScore;
    @Deprecated @Column private Boolean hireRecommendation = false;

    @Deprecated @Lob
    private String comments;

    // Audit
    @Column(nullable = false) private LocalDateTime submittedAt = LocalDateTime.now();
    @Column(nullable = false, length = 120) private String submittedBy;

    @Column(columnDefinition = "TEXT")
    private String sectionAAnswer1; // Q1

    @Column(columnDefinition = "TEXT")
    private String sectionAAnswer2; // Q2

    @Enumerated(EnumType.STRING)
    private CareerOpportunityStatus careerOpportunityStatus;

    @Column(columnDefinition = "TEXT")
    private String careerOpportunityRemark;




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

    public Integer getTeamwork() { return teamwork; }
    public void setTeamwork(Integer teamwork) { this.teamwork = teamwork; }

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

    public InternshipNature getInternshipNature() { return internshipNature; }
    public void setInternshipNature(InternshipNature internshipNature) { this.internshipNature = internshipNature; }

    public Integer getDisciplineAttendance() { return disciplineAttendance; }
    public void setDisciplineAttendance(Integer disciplineAttendance) { this.disciplineAttendance = disciplineAttendance; }

    public Integer getKnowledgeSkills() { return knowledgeSkills; }
    public void setKnowledgeSkills(Integer knowledgeSkills) { this.knowledgeSkills = knowledgeSkills; }

    public Integer getAttitudeInitiative() { return attitudeInitiative; }
    public void setAttitudeInitiative(Integer attitudeInitiative) { this.attitudeInitiative = attitudeInitiative; }

    public Integer getInterestResponsibility() { return interestResponsibility; }
    public void setInterestResponsibility(Integer interestResponsibility) { this.interestResponsibility = interestResponsibility; }

    public Integer getCommunicationCooperation() { return communicationCooperation; }
    public void setCommunicationCooperation(Integer communicationCooperation) { this.communicationCooperation = communicationCooperation; }

    public Integer getProfessionalAppearance() { return professionalAppearance; }
    public void setProfessionalAppearance(Integer professionalAppearance) { this.professionalAppearance = professionalAppearance; }

    public Integer getOverallMark10() { return overallMark10; }
    public void setOverallMark10(Integer overallMark10) { this.overallMark10 = overallMark10; }

    public String getOverallComment() { return overallComment; }
    public void setOverallComment(String overallComment) { this.overallComment = overallComment; }

    public String getSectionAAnswer1() {
        return sectionAAnswer1;
    }

    public void setSectionAAnswer1(String sectionAAnswer1) {
        this.sectionAAnswer1 = sectionAAnswer1;
    }

    public String getSectionAAnswer2() {
        return sectionAAnswer2;
    }

    public void setSectionAAnswer2(String sectionAAnswer2) {
        this.sectionAAnswer2 = sectionAAnswer2;
    }

    public CareerOpportunityStatus getCareerOpportunityStatus() {
        return careerOpportunityStatus;
    }

    public void setCareerOpportunityStatus(CareerOpportunityStatus careerOpportunityStatus) {
        this.careerOpportunityStatus = careerOpportunityStatus;
    }

    public String getCareerOpportunityRemark() {
        return careerOpportunityRemark;
    }

    public void setCareerOpportunityRemark(String careerOpportunityRemark) {
        this.careerOpportunityRemark = careerOpportunityRemark;
    }



}
