package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

    public enum FileType { PDF, VIDEO, IMAGE, DOC, XLS, OTHER }
    public enum Audience { STUDENT, LECTURER, INDUSTRY, ADMIN, ALL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Server-side stored filename or path (e.g. /uploads/docs/uuid.pdf) */
    @Column(nullable = false, length = 500)
    private String fileName;

    /** Original filename from the user’s computer */
    @Column(length = 255)
    private String originalName;

    /** Keep old string fields for compatibility if you already use them in views */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileType fileType = FileType.PDF;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Audience audience = Audience.ALL;

    /** Optional: who uploaded */
    private Long uploaderId;

    /** Optional: bytes */
    private Long fileSize;

    /** Mark this document as an Announcement item (shown on login / VL home). */
    @Column(nullable = false)
    private boolean announcement = false;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }

    // Getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    public Audience getAudience() { return audience; }
    public void setAudience(Audience audience) { this.audience = audience; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public boolean isAnnouncement() { return announcement; }
    public void setAnnouncement(boolean announcement) { this.announcement = announcement; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
