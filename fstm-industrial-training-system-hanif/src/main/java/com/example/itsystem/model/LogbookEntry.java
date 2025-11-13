package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;

@Entity
@Table(
        name = "logbook_entries",
        indexes = {
                @Index(name = "idx_logbook_student", columnList = "studentId"),
                @Index(name = "idx_logbook_status", columnList = "status"),
                @Index(name = "idx_logbook_weekstart", columnList = "weekStartDate")
        }
)
public class LogbookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    private LocalDate weekStartDate;

    private Long lecturerId;

    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    // Optional if you already use weekStartDate
    private String week;

    // Full weekly content
    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean endorsed;
    private boolean endorsedByLecturer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String internalNote; // visible to admin only

    // ----- 5 core contents -----
    @Lob
    @Column(columnDefinition = "TEXT")
    private String mainTask;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String skills;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String challenges;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(length = 512) // allow longer relative/absolute paths
    private String photoPath;

    // ----- Supervisor review fields -----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(length = 1000)
    private String reviewComment;

    @Column(length = 255)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    /* ---------- lifecycle ---------- */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Timestamp.from(java.time.Instant.now());
        }
        if (status == null) {
            status = ReviewStatus.PENDING;
        }
    }

    /* ---------- getters & setters ---------- */
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

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }
    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public Long getLecturerId() {
        return lecturerId;
    }
    public void setLecturerId(Long lecturerId) {
        this.lecturerId = lecturerId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getWeek() {
        return week;
    }
    public void setWeek(String week) {
        this.week = week;
    }

    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public boolean isEndorsed() {
        return endorsed;
    }
    public void setEndorsed(boolean endorsed) {
        this.endorsed = endorsed;
    }

    public boolean isEndorsedByLecturer() {
        return endorsedByLecturer;
    }
    public void setEndorsedByLecturer(boolean endorsedByLecturer) {
        this.endorsedByLecturer = endorsedByLecturer;
    }

    public String getInternalNote() {
        return internalNote;
    }
    public void setInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }

    public String getMainTask() {
        return mainTask;
    }
    public void setMainTask(String mainTask) {
        this.mainTask = mainTask;
    }

    public String getSkills() {
        return skills;
    }
    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getChallenges() {
        return challenges;
    }
    public void setChallenges(String challenges) {
        this.challenges = challenges;
    }

    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }

    public String getPhotoPath() {
        return photoPath;
    }
    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public ReviewStatus getStatus() {
        return status;
    }
    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public String getReviewComment() {
        return reviewComment;
    }
    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }
    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }
    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Boolean getEndorsed (){
        return endorsed;
    }
}
