package com.example.itsystem.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    // Root: <project>/uploads (absolute, normalized)
    private final Path uploadsRoot;

    private final Path opportunitiesRoot;
    private final Path logbookRoot;
    private final Path finalReportRoot;

    public FileStorageService() {
        try {
            this.uploadsRoot = Paths.get("uploads").toAbsolutePath().normalize();

            this.opportunitiesRoot = uploadsRoot.resolve("opportunities");
            this.logbookRoot = uploadsRoot.resolve("logbook");
            this.finalReportRoot = uploadsRoot.resolve("final-report");

            Files.createDirectories(uploadsRoot);
            Files.createDirectories(opportunitiesRoot);
            Files.createDirectories(logbookRoot);
            Files.createDirectories(finalReportRoot);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload folder", e);
        }
    }

    // =========================================================
    // (1) Opportunities image upload  -> /uploads/opportunities/{file}
    // =========================================================
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        String ext = getExt(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().replace("-", "") + (ext != null ? "." + ext : "");

        try {
            Files.copy(file.getInputStream(), opportunitiesRoot.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store opportunity image", e);
        }

        return "/uploads/opportunities/" + name;
    }

    // =========================================================
    // (2) Logbook file upload (image/pdf)
    // -> /uploads/logbook/{studentId}/{logbookId}/{file}
    // =========================================================
    public String storeLogbookFile(MultipartFile file, Long studentId, Long logbookId) {
        if (file == null || file.isEmpty()) return null;

        String contentType = file.getContentType();

        // Allow image + PDF
        Set<String> allowed = Set.of(
                "image/jpeg", "image/png", "image/webp",
                "application/pdf"
        );

        // Some browsers may send null contentType; we’ll validate by extension fallback
        String ext = getExt(file.getOriginalFilename());
        boolean extOk = ext != null && (
                ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg") ||
                        ext.equalsIgnoreCase("png") || ext.equalsIgnoreCase("webp") ||
                        ext.equalsIgnoreCase("pdf")
        );

        if (contentType != null && !allowed.contains(contentType) && !extOk) {
            throw new RuntimeException("Only JPG/PNG/WEBP images or PDF are allowed for logbook.");
        }

        String name = UUID.randomUUID().toString().replace("-", "") + (ext != null ? "." + ext : "");

        Path dir = logbookRoot
                .resolve(String.valueOf(studentId))
                .resolve(String.valueOf(logbookId));

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store logbook file", e);
        }

        return "/uploads/logbook/" + studentId + "/" + logbookId + "/" + name;
    }

    // =========================================================
    // (3) Final Report PDF
    // -> /uploads/final-report/{studentId}/report/{file}.pdf
    // =========================================================
    public String storeFinalReportPdf(MultipartFile file, Long studentId) {
        if (file == null || file.isEmpty()) return null;

        String contentType = file.getContentType();
        String ext = getExt(file.getOriginalFilename());

        // Strict: prefer PDF
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType) || "pdf".equalsIgnoreCase(ext);
        if (!isPdf) {
            throw new RuntimeException("Only PDF is allowed for Final Report.");
        }

        String name = UUID.randomUUID().toString().replace("-", "") + ".pdf";

        Path dir = finalReportRoot
                .resolve(String.valueOf(studentId))
                .resolve("report");

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store final report PDF", e);
        }

        return "/uploads/final-report/" + studentId + "/report/" + name;
    }

    // =========================================================
    // (4) Final Report Video (MP4)
    // -> /uploads/final-report/{studentId}/video/{file}.mp4
    // =========================================================
    public String storeFinalReportVideo(MultipartFile file, Long studentId) {
        if (file == null || file.isEmpty()) return null;

        String contentType = file.getContentType();
        String ext = getExt(file.getOriginalFilename());

        // Browsers can send video/mp4 or application/octet-stream, so allow MP4 extension as fallback.
        boolean isMp4 = "mp4".equalsIgnoreCase(ext) || "video/mp4".equalsIgnoreCase(contentType);
        if (!isMp4) {
            throw new RuntimeException("Only MP4 is allowed for Final Report video.");
        }

        String name = UUID.randomUUID().toString().replace("-", "") + ".mp4";

        Path dir = finalReportRoot
                .resolve(String.valueOf(studentId))
                .resolve("video");

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store final report video", e);
        }

        return "/uploads/final-report/" + studentId + "/video/" + name;
    }

    private String getExt(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : null;
    }

    // =========================================================
// (3b) Final Report Word (DOC/DOCX)
// -> /uploads/final-report/{studentId}/report/{file}.docx OR .doc
// =========================================================
    public String storeFinalReportWord(MultipartFile file, Long studentId) {
        if (file == null || file.isEmpty()) return null;

        String contentType = file.getContentType();
        String ext = getExt(file.getOriginalFilename());

        // Allow Word (doc/docx) - validate by ext (best) + contentType (optional)
        boolean isDoc = "doc".equalsIgnoreCase(ext);
        boolean isDocx = "docx".equalsIgnoreCase(ext);

        boolean isWordByType =
                "application/msword".equalsIgnoreCase(contentType) ||
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType);

        // Some browsers may send application/octet-stream, so extension is the main guard
        if (!(isDoc || isDocx) && !isWordByType) {
            throw new RuntimeException("Only Word (.doc, .docx) is allowed for Final Report.");
        }

        String safeExt = (isDoc ? "doc" : "docx"); // default to docx if uncertain
        String name = UUID.randomUUID().toString().replace("-", "") + "." + safeExt;

        Path dir = finalReportRoot
                .resolve(String.valueOf(studentId))
                .resolve("report");

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store final report Word file", e);
        }

        return "/uploads/final-report/" + studentId + "/report/" + name;
    }


}