package com.example.itsystem.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String sector;              // e.g., Food Processing, QA Lab, etc.
    private String defaultJobScope;     // default scope text shown to students

    @Column(precision = 12, scale = 2)
    private BigDecimal typicalAllowance; // monthly allowance guideline

    private Boolean accommodation;       // accommodation provided?

    // Aggregated feedback (from reflections/evaluations)
    private Double ratingAvg;
    private Integer ratingCount;

    // -------- Getters & Setters --------

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
}
