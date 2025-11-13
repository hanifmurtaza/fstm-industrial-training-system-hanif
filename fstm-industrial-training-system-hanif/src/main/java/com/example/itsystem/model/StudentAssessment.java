package com.example.itsystem.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "student_assessment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_user_id","session"}))
public class StudentAssessment {

    public enum Status { DRAFT, SUBMITTED, VERIFIED, FINALIZED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="student_user_id", nullable = false)
    private Long studentUserId;

    @Column(nullable = false, length = 20)
    private String session;

    @Column(name="visiting_lecturer_id")
    private Long visitingLecturerId;

    @Column(name="industry_supervisor_id")
    private Long industrySupervisorId;

    // Visiting Lecturer (满分 60)
    @Column(name="vl_evaluation_10")   private BigDecimal vlEvaluation10  = BigDecimal.ZERO;
    @Column(name="vl_attendance_5")    private BigDecimal vlAttendance5   = BigDecimal.ZERO;
    @Column(name="vl_logbook_5")       private BigDecimal vlLogbook5      = BigDecimal.ZERO;
    @Column(name="vl_final_report_40") private BigDecimal vlFinalReport40 = BigDecimal.ZERO;

    // Industry Supervisor (满分 40) —— 先保留，后续会用
    @Column(name="is_skills_20")         private BigDecimal isSkills20        = BigDecimal.ZERO;
    @Column(name="is_communication_10")  private BigDecimal isCommunication10 = BigDecimal.ZERO;
    @Column(name="is_teamwork_10")       private BigDecimal isTeamwork10      = BigDecimal.ZERO;

    @Column(name="total_100", insertable = false, updatable = false)
    private BigDecimal total100;

    private String grade;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    @Column(name="created_at", insertable=false, updatable=false,
            columnDefinition="timestamp default current_timestamp")
    private Instant createdAt;

    @Column(name="updated_at", insertable=false,
            columnDefinition="timestamp default current_timestamp on update current_timestamp")
    private Instant updatedAt;

    // ===== Getters / Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentUserId() { return studentUserId; }
    public void setStudentUserId(Long studentUserId) { this.studentUserId = studentUserId; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }

    public Long getVisitingLecturerId() { return visitingLecturerId; }
    public void setVisitingLecturerId(Long visitingLecturerId) { this.visitingLecturerId = visitingLecturerId; }

    public Long getIndustrySupervisorId() { return industrySupervisorId; }
    public void setIndustrySupervisorId(Long industrySupervisorId) { this.industrySupervisorId = industrySupervisorId; }

    public BigDecimal getVlEvaluation10() { return vlEvaluation10; }
    public void setVlEvaluation10(BigDecimal vlEvaluation10) { this.vlEvaluation10 = vlEvaluation10; }

    public BigDecimal getVlAttendance5() { return vlAttendance5; }
    public void setVlAttendance5(BigDecimal vlAttendance5) { this.vlAttendance5 = vlAttendance5; }

    public BigDecimal getVlLogbook5() { return vlLogbook5; }
    public void setVlLogbook5(BigDecimal vlLogbook5) { this.vlLogbook5 = vlLogbook5; }

    public BigDecimal getVlFinalReport40() { return vlFinalReport40; }
    public void setVlFinalReport40(BigDecimal vlFinalReport40) { this.vlFinalReport40 = vlFinalReport40; }

    public BigDecimal getIsSkills20() { return isSkills20; }
    public void setIsSkills20(BigDecimal isSkills20) { this.isSkills20 = isSkills20; }

    public BigDecimal getIsCommunication10() { return isCommunication10; }
    public void setIsCommunication10(BigDecimal isCommunication10) { this.isCommunication10 = isCommunication10; }

    public BigDecimal getIsTeamwork10() { return isTeamwork10; }
    public void setIsTeamwork10(BigDecimal isTeamwork10) { this.isTeamwork10 = isTeamwork10; }

    public BigDecimal getTotal100() { return total100; }
    public void setTotal100(BigDecimal total100) { this.total100 = total100; } // 生成列，实际不会用到 setter

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
