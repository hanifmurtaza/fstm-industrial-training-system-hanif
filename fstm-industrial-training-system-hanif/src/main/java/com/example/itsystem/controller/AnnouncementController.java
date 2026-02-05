package com.example.itsystem.controller;

import com.example.itsystem.model.Document;
import com.example.itsystem.repository.DocumentRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class AnnouncementController {

    private final DocumentRepository documentRepository;

    public AnnouncementController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Public download endpoint (used by login page + VL home).
     * Always forces download (Content-Disposition: attachment).
     */
    @GetMapping("/announcement/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws Exception {
        Document doc = documentRepository.findById(id).orElse(null);
        if (doc == null || !doc.isAnnouncement()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = toResource(doc.getFileName());
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String filename = (doc.getOriginalName() != null && !doc.getOriginalName().isBlank())
                ? doc.getOriginalName()
                : "announcement";

        String contentType = null;
        try {
            Path p = resource.getFile().toPath();
            contentType = Files.probeContentType(p);
        } catch (Exception ignore) {}

        MediaType mt = (contentType != null) ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
    }

    // Convert publicUrl like /uploads/... to file Resource.
    private Resource toResource(String publicUrl) {
        try {
            if (publicUrl == null || !publicUrl.startsWith("/uploads/")) return null;
            String relative = publicUrl.replaceFirst("^/uploads/", "");
            Path filePath = Paths.get("uploads").toAbsolutePath().normalize().resolve(relative).normalize();
            return new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
