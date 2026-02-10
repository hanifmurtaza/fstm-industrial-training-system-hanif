package com.example.itsystem.controller;

import com.example.itsystem.model.Document;
import com.example.itsystem.repository.DocumentRepository;
import com.example.itsystem.service.FileStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
                         RedirectAttributes ra) {
        try {
            if (title == null || title.isBlank()) {
                ra.addFlashAttribute("toast", "Title is required.");
                return "redirect:/admin/announcements";
            }
            if (file == null || file.isEmpty()) {
                ra.addFlashAttribute("toast", "File is required.");
                return "redirect:/admin/announcements";
            }

            Document doc = new Document();
            doc.setTitle(title.trim());
            doc.setAnnouncement(true);

            // Allow: STUDENT, LECTURER (VL-only) or ALL
            Document.Audience a;
            if ("LECTURER".equalsIgnoreCase(audience)) {
                a = Document.Audience.LECTURER;
            } else if ("STUDENT".equalsIgnoreCase(audience)) {
                a = Document.Audience.STUDENT;
            } else {
                a = Document.Audience.ALL;
            }
            doc.setAudience(a);

            String originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "announcement";
            doc.setOriginalName(originalName);

            // Detect type and store file using FileStorageService
            doc.setFileType(detectFileType(originalName));
            String storedPath = fileStorageService.storeAnnouncementFile(file);
            // storedPath should be something like "/uploads/announcements/xxx.pdf"
            doc.setFileName(storedPath);

            doc.setFileSize(file.getSize());

            // Optional uploaderId (if you store it in session)
            Object userId = session.getAttribute("userId");
            if (userId instanceof Long) {
                doc.setUploaderId((Long) userId);
            }

            doc.setUploadedAt(LocalDateTime.now());
            documentRepository.save(doc);

            ra.addFlashAttribute("toast", "Announcement uploaded.");
            return "redirect:/admin/announcements";
        } catch (Exception e) {
            ra.addFlashAttribute("toast", "Upload failed. Please try again.");
            return "redirect:/admin/announcements";
        }
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
