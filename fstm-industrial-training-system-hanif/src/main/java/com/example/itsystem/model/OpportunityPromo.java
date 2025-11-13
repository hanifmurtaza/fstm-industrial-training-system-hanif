package com.example.itsystem.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "opportunity_promos",
        indexes = {
                @Index(name = "idx_promo_status", columnList = "status"),
                @Index(name = "idx_promo_company", columnList = "companyId"),
                @Index(name = "idx_promo_created", columnList = "createdAt")
        }
)
public class OpportunityPromo {

    // ----- Identity -----
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ----- Ownership / Company -----
    /** Optional link to a Company master record (can be null). */
    @Column(nullable = true)
    private Long companyId;

    /** Displayed name even if no Company record is linked. */
    @NotBlank(message = "{promo.companyName.required}")
    @Size(max = 120, message = "{promo.companyName.size}")
    @Column(nullable = false, length = 120)
    private String companyName;

    /** The industry user who created/owns this promo. */
    @Column(nullable = false)
    private Long supervisorUserId;

    // ----- Content -----
    @NotBlank(message = "{promo.title.required}")
    @Size(max = 140, message = "{promo.title.size}")
    @Column(nullable = false, length = 140)
    private String title;

    @NotBlank(message = "{promo.location.required}")
    @Size(max = 80, message = "{promo.location.size}")
    @Column(nullable = false, length = 80)
    private String location; // e.g., "Serdang, Selangor" or "Remote"

    @Size(max = 80)
    @Column(nullable = true, length = 80)
    private String workMode; // Onsite / Hybrid / Remote

    @Size(max = 60)
    @Column(nullable = true, length = 60)
    private String roleType; // Internship / Part-time / Full-time

    /** Comma-separated tags for quick filtering (e.g., "Bakery,Catering"). */
    @Size(max = 80)
    @Column(nullable = true, length = 80)
    private String sectorTags;

    @NotBlank(message = "{promo.description.required}")
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    /** External link for details / apply (optional). */
    @Size(max = 120)
    @Column(nullable = true, length = 120)
    private String externalLink;

    /** Optional image (stored path or URL). */
    @Size(max = 200)
    @Column(nullable = true, length = 200)
    private String imageUrl;   // e.g. /uploads/opportunities/abc123.jpg

    // ----- Contact -----
    @NotBlank(message = "{promo.contactName.required}")
    @Size(max = 80, message = "{promo.contactName.size}")
    @Column(nullable = false, length = 80)
    private String contactName;

    @NotBlank(message = "{promo.contactEmail.required}")
    @Email(message = "{promo.contactEmail.invalid}")
    @Size(max = 80, message = "{promo.contactEmail.size}")
    @Column(nullable = false, length = 80)
    private String contactEmail;

    @Size(max = 30)
    @Column(nullable = true, length = 30)
    private String contactPhone;

    // ----- Lifecycle -----
    @Column(nullable = true)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoStatus status = PromoStatus.DRAFT;

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

    // ----- Getters & Setters -----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public Long getSupervisorUserId() { return supervisorUserId; }
    public void setSupervisorUserId(Long supervisorUserId) { this.supervisorUserId = supervisorUserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }

    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }

    public String getSectorTags() { return sectorTags; }
    public void setSectorTags(String sectorTags) { this.sectorTags = sectorTags; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExternalLink() { return externalLink; }
    public void setExternalLink(String externalLink) { this.externalLink = externalLink; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public PromoStatus getStatus() { return status; }
    public void setStatus(PromoStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // ----- equals / hashCode (by id) -----
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpportunityPromo that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "OpportunityPromo{" +
                "id=" + id +
                ", companyName='" + companyName + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}
