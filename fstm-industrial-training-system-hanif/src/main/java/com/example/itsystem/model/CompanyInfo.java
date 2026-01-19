package com.example.itsystem.model;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.LocalDate;

@Entity
@Table(
        name = "company_info",
        indexes = {
                @Index(name = "idx_companyinfo_student", columnList = "studentId"),
                @Index(name = "idx_companyinfo_status", columnList = "status"),
                @Index(name = "idx_companyinfo_sector", columnList = "sector"),
                @Index(name = "idx_companyinfo_company", columnList = "companyName"),
                @Index(name = "idx_companyinfo_linked_company", columnList = "linkedCompanyId")
        }
)
public class CompanyInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Who submitted this record (student user id) */
    @Column(nullable = false)
    private Long studentId;

    /** Company details as submitted */
    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(length = 500)
    private String address;

    @Column(length = 120)
    private String supervisorName;

    @Column(length = 120)
    private String supervisorEmail;

    @Column(length = 40)
    private String supervisorPhone;

    /** Academic session (e.g., 2024/25-2) */
    @Column(length = 40)
    private String session;

    /** ✅ Internship start/end date */
    private LocalDate internshipStartDate;
    private LocalDate internshipEndDate;

    /** Admin workflow status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyInfoStatus status = CompanyInfoStatus.PENDING;

    /** When Admin promotes to master catalog, link the Company id here */
    private Long linkedCompanyId;

    /** Created timestamp */
    @Column(nullable = false, updatable = false)
    private Timestamp createdAt;

    // ---------- Industry supervisor inputs (optional) ----------
    @Column(length = 1000)
    private String jobScope;

    @Column(length = 120)
    private String department;

    @Column(length = 60)
    private String allowance;     // e.g., "RM500/month"

    @Column(length = 60)
    private String accommodation; // e.g., "Provided" / "Not Provided"

    private Boolean isPublicListing; // null -> treat as false in UI

    @Enumerated(EnumType.STRING)
    @Column(name = "sector")
    private CompanySector sector;

    // ---------- lifecycle ----------
    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = new Timestamp(System.currentTimeMillis());
        }
        if (status == null) {
            status = CompanyInfoStatus.PENDING;
        }
    }

    // ---------- getters & setters ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getSupervisorName() { return supervisorName; }
    public void setSupervisorName(String supervisorName) { this.supervisorName = supervisorName; }

    public String getSupervisorEmail() { return supervisorEmail; }
    public void setSupervisorEmail(String supervisorEmail) { this.supervisorEmail = supervisorEmail; }

    public String getSupervisorPhone() { return supervisorPhone; }
    public void setSupervisorPhone(String supervisorPhone) { this.supervisorPhone = supervisorPhone; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }

    public LocalDate getInternshipStartDate() { return internshipStartDate; }
    public void setInternshipStartDate(LocalDate internshipStartDate) { this.internshipStartDate = internshipStartDate; }

    public LocalDate getInternshipEndDate() { return internshipEndDate; }
    public void setInternshipEndDate(LocalDate internshipEndDate) { this.internshipEndDate = internshipEndDate; }

    public CompanyInfoStatus getStatus() { return status; }
    public void setStatus(CompanyInfoStatus status) { this.status = status; }

    public Long getLinkedCompanyId() { return linkedCompanyId; }
    public void setLinkedCompanyId(Long linkedCompanyId) { this.linkedCompanyId = linkedCompanyId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getJobScope() { return jobScope; }
    public void setJobScope(String jobScope) { this.jobScope = jobScope; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getAllowance() { return allowance; }
    public void setAllowance(String allowance) { this.allowance = allowance; }

    public String getAccommodation() { return accommodation; }
    public void setAccommodation(String accommodation) { this.accommodation = accommodation; }

    public Boolean getIsPublicListing() { return isPublicListing; }
    public void setIsPublicListing(Boolean isPublicListing) { this.isPublicListing = isPublicListing; }

    public CompanySector getSector() { return sector; }
    public void setSector(CompanySector sector) { this.sector = sector; }
}