package com.example.itsystem.repository;

import com.example.itsystem.model.LogbookAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LogbookAttachmentRepository extends JpaRepository<LogbookAttachment, Long> {
    List<LogbookAttachment> findByLogbookId(Long logbookId);
}