package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "logbook_attachment")
public class LogbookAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 你如果是 LogbookEntry，就把类型改成 LogbookEntry
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logbook_id", nullable = false)
    private LogbookEntry logbook;

    @Column(nullable = false)
    private String storagePath; // e.g. logbook/12/5/uuid.jpg

    private String originalName;
    private String contentType;
    private Long size;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    // getters/setters
    public Long getId() { return id; }
    public LogbookEntry getLogbook() { return logbook; }
    public void setLogbook(LogbookEntry logbook) { this.logbook = logbook; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}