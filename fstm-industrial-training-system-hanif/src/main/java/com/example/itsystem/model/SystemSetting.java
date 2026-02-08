package com.example.itsystem.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "system_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_system_settings_key", columnNames = "setting_key")
)
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 120)
    private String key;

    @Column(name = "setting_value", length = 2000)
    private String value;

    public SystemSetting() {}

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
