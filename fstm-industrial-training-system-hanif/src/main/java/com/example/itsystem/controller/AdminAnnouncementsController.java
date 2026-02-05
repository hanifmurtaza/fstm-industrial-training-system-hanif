package com.example.itsystem.controller;

import com.example.itsystem.model.Document;
import com.example.itsystem.repository.DocumentRepository;
import com.example.itsystem.service.FileStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/admin/announcements")
public class AdminAnnouncementsController {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    public AdminAnnouncementsController(DocumentRepository documentRepository,
                                       FileStorageService fileStorageService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String page(Model model) {
        List<Document> all = documentRepository.findAll();
        all.removeIf(d -> d == null || !d.isAnnouncement());
        all.sort(Comparator.comparing(Document::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        model.addAttribute("announcements", all);
        model.addAttribute("audiences", Document.Audience.values());
        return "admin-announcements";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("title") String title,
                         @RequestParam(value = "audience", defaultValue = "ALL") String audience,
                         @RequestParam("file") MultipartFile file,
                         HttpSession session,
                         Model model) {
        try {
            if (title == null || title.isBlank()) throw new RuntimeException("Title is required");
            if (file == null || file.isEmpty()) throw new RuntimeException("File is required");

            Document doc = new Document();
            doc.setTitle(title.trim());
            doc.setAnnouncement(true);

            // Only allow: LECTURER (VL-only) or ALL (public login) as requested.
            Document.Audience a = "LECTURER".equalsIgnoreCase(audience)
                    ? Document.Audience.LECTURER
                    : Document.Audience.ALL;
            doc.setAudience(a);

            doc.setOriginalName(file.getOriginalFilename());
            doc.setFileSize(file.getSize());
            doc.setFileType(detectFileType(file.getOriginalFilename()));

            String publicUrl = fileStorageService.storeAnnouncementFile(file);
            doc.setFileName(publicUrl);

            Object userObj = session.getAttribute("user");
            if (userObj instanceof com.example.itsystem.model.User u) {
                doc.setUploaderId(u.getId());
            }

            documentRepository.save(doc);
            return "redirect:/admin/announcements?success";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            return page(model);
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        Document doc = documentRepository.findById(id).orElse(null);
        if (doc != null && doc.isAnnouncement()) {
            // best-effort delete file from disk
            deleteFileQuietly(doc.getFileName());
            documentRepository.deleteById(id);
        }
        return "redirect:/admin/announcements";
    }

    private Document.FileType detectFileType(String originalName) {
        if (originalName == null) return Document.FileType.OTHER;
        String lower = originalName.toLowerCase();
        if (lower.endsWith(".pdf")) return Document.FileType.PDF;
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return Document.FileType.DOC;
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return Document.FileType.XLS;
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) return Document.FileType.IMAGE;
        if (lower.endsWith(".mp4")) return Document.FileType.VIDEO;
        return Document.FileType.OTHER;
    }

    private void deleteFileQuietly(String publicUrl) {
        try {
            if (publicUrl == null || !publicUrl.startsWith("/uploads/")) return;
            String relative = publicUrl.replaceFirst("^/uploads/", "");
            Path filePath = Paths.get("uploads").toAbsolutePath().normalize().resolve(relative).normalize();
            Files.deleteIfExists(filePath);
        } catch (Exception ignore) {}
    }
}
