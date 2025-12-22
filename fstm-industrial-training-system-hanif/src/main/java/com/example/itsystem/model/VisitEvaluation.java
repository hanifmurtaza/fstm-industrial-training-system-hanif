package com.example.itsystem.model;

import jakarta.persistence.*;
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

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String interviewOutcome;

    @Column(columnDefinition = "TEXT")
    private String logbookReview;

    @Column(columnDefinition = "TEXT")
    private String workplaceSuitability;

    @Column(columnDefinition = "TEXT")
    private String careerPotential;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ================== 👇 Part C（临时字段，不入库） ==================
    @Transient
    private Integer vlEvaluation10;

    @Transient
    private Integer vlAttendance5;

    @Transient
    private Integer vlLogbook5;

    @Transient
    private Integer vlFinalReport40;

    @Transient
    private String session;
    // ================== 👆 新增结束 ==================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }

    public Long getLecturerId() { return lecturerId; }
    public void setLecturerId(Long lecturerId) { this.lecturerId = lecturerId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getInterviewOutcome() { return interviewOutcome; }
    public void setInterviewOutcome(String interviewOutcome) { this.interviewOutcome = interviewOutcome; }

    public String getLogbookReview() { return logbookReview; }
    public void setLogbookReview(String logbookReview) { this.logbookReview = logbookReview; }

    public String getWorkplaceSuitability() { return workplaceSuitability; }
    public void setWorkplaceSuitability(String workplaceSuitability) { this.workplaceSuitability = workplaceSuitability; }

    public String getCareerPotential() { return careerPotential; }
    public void setCareerPotential(String careerPotential) { this.careerPotential = careerPotential; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getVlEvaluation10() { return vlEvaluation10; }
    public void setVlEvaluation10(Integer vlEvaluation10) { this.vlEvaluation10 = vlEvaluation10; }

    public Integer getVlAttendance5() { return vlAttendance5; }
    public void setVlAttendance5(Integer vlAttendance5) { this.vlAttendance5 = vlAttendance5; }

    public Integer getVlLogbook5() { return vlLogbook5; }
    public void setVlLogbook5(Integer vlLogbook5) { this.vlLogbook5 = vlLogbook5; }

    public Integer getVlFinalReport40() { return vlFinalReport40; }
    public void setVlFinalReport40(Integer vlFinalReport40) { this.vlFinalReport40 = vlFinalReport40; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }
}
