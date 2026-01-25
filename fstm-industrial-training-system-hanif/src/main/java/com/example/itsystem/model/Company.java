package com.example.itsystem.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // BASIC INFO
    @Column(nullable = false)
    private String name;

    private String address;
    private String sector;              // e.g., Food Processing, QA Lab, etc.
    private String defaultJobScope;     // default scope text shown to students

    @Column(precision = 12, scale = 2)
    private BigDecimal typicalAllowance; // monthly allowance guideline

    private Boolean accommodation;       // accommodation provided?

    // NEW — PROFESSIONAL COMPANY INFO
    private String contactName;          // Person in charge / supervisor name
    private String contactEmail;
    private String contactPhone;

    private String website;

    @Column(length = 2000)
    private String notes;               // internal admin notes

    // EXISTING — AGGREGATED FEEDBACK
    private Double ratingAvg;
    private Integer ratingCount;

    // ==============================
    // Getters & Setters
    // ==============================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getDefaultJobScope() {
        return defaultJobScope;
    }

    public void setDefaultJobScope(String defaultJobScope) {
        this.defaultJobScope = defaultJobScope;
    }

    public BigDecimal getTypicalAllowance() {
        return typicalAllowance;
    }

    public void setTypicalAllowance(BigDecimal typicalAllowance) {
        this.typicalAllowance = typicalAllowance;
    }

    public Boolean getAccommodation() {
        return accommodation;
    }

    public void setAccommodation(Boolean accommodation) {
        this.accommodation = accommodation;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getRatingAvg() {
        return ratingAvg;
    }

    public void setRatingAvg(Double ratingAvg) {
        this.ratingAvg = ratingAvg;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount) {
        this.ratingCount = ratingCount;
    }

    // ==============================
// Derived helpers (no DB impact)
// ==============================

    @Transient
    public CompanySector getSectorEnum() {
        if (sector == null || sector.isBlank()) return null;
        try {
            return CompanySector.valueOf(sector.trim());
        } catch (Exception e) {
            return CompanySector.OTHERS;
        }
    }

    @Transient
    public String getSectorDisplay() {
        CompanySector s = getSectorEnum();
        if (s == null) return "-";
        // Title-case: FOOD_MANUFACTURING_PROCESSING -> Food Manufacturing Processing
        String raw = s.name().toLowerCase().replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            out.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.length() > 1 ? p.substring(1) : "")
                    .append(' ');
        }
        return out.toString().trim();
    }

}
