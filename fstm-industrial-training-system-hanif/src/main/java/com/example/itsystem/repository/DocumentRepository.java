package com.example.itsystem.repository;

import com.example.itsystem.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByAudience(Document.Audience audience);
    List<Document> findByFileType(Document.FileType fileType);
    List<Document> findByUploaderId(Long uploaderId);

    // Announcements
    List<Document> findByAnnouncementTrueAndAudienceOrderByUploadedAtDesc(Document.Audience audience);
}
