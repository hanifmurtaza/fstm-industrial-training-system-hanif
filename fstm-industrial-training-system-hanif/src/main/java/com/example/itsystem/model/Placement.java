package com.example.itsystem.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "placements",
        indexes = {
                @Index(name = "idx_place_student", columnList = "studentId"),
                @Index(name = "idx_place_company", columnList = "companyId"),
                @Index(name = "idx_place_status", columnList = "status"),
                @Index(name = "idx_place_companyinfo", columnList = "companyInfoId"),
                // NEW: fast lists by supervisor
                @Index(name = "idx_place_supervisor", columnList = "supervisorUserId")
        }
)
public class Placement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = true)
    private Long supervisorUserId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = true)
    private Long companyInfoId;

    @Lob
    @Column
    private String adminNotes;   // NEW: admin notes/instructions for supervisor


    private LocalDate startDate;
    private LocalDate endDate;

    // ---------- Supervisor-verifiable fields (NEW) ----------
    @Column(length = 120)
    private String department;          // NEW

    @Lob
    @Column
    private String jobScope;            // NEW (textarea)

    @Column(length = 80)
    private String allowance;           // NEW (keep String for flexibility)

    @Column(nullable = false)
    private boolean accommodation = false; // NEW
    // --------------------------------------------------------

    private LocalDate reportDutyDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlacementStatus status = PlacementStatus.AWAITING_SUPERVISOR;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // -------- Getters & Setters --------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getSupervisorUserId() { return supervisorUserId; }
    public void setSupervisorUserId(Long supervisorUserId) { this.supervisorUserId = supervisorUserId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getCompanyInfoId() { return companyInfoId; }
    public void setCompanyInfoId(Long companyInfoId) { this.companyInfoId = companyInfoId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getDepartment() { return department; }              // NEW
    public void setDepartment(String department) { this.department = department; }

    public String getJobScope() { return jobScope; }                  // NEW
    public void setJobScope(String jobScope) { this.jobScope = jobScope; }

    public String getAllowance() { return allowance; }                // NEW
    public void setAllowance(String allowance) { this.allowance = allowance; }

    public boolean isAccommodation() { return accommodation; }        // NEW
    public void setAccommodation(boolean accommodation) { this.accommodation = accommodation; }

    public LocalDate getReportDutyDate() { return reportDutyDate; }
    public void setReportDutyDate(LocalDate reportDutyDate) { this.reportDutyDate = reportDutyDate; }

    public PlacementStatus getStatus() { return status; }
    public void setStatus(PlacementStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

}
