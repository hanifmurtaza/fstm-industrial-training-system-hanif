package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class LecturerFinalReportController {

    @Autowired
    private UserRepository userRepository;

    // ========== 1) 列表页：老师查看自己学生的 Final Report ==========
    @GetMapping("/lecturer/final-report/list")
    public String lecturerFinalReportList(
            @RequestParam(value = "session", required = false) String selectedSession,
            HttpSession session,
            Model model
    ) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null) return "redirect:/login";
        if (!"teacher".equalsIgnoreCase(lecturer.getRole())) return "redirect:/login";

        // 取老师名下学生（你项目里一般是 student.setLecturer(lecturer)）
        List<User> students = userRepository.findByLecturer_Id(lecturer.getId());

        // sessions 下拉（去重、排序）
        List<String> sessions = students.stream()
                .map(User::getSession)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // 可选筛选学期
        if (selectedSession != null && !selectedSession.isBlank()) {
            students = students.stream()
                    .filter(s -> selectedSession.equals(s.getSession()))
                    .collect(Collectors.toList());
        }

        model.addAttribute("students", students);
        model.addAttribute("sessions", sessions);
        model.addAttribute("selectedSession", selectedSession);

        return "lecturer/final-report-list";
    }

    // ========== 2) 下载 PDF（带权限校验） ==========
    @GetMapping("/lecturer/final-report/download/pdf/{studentId}")
    public ResponseEntity<Resource> downloadStudentFinalReportPdf(
            @PathVariable Long studentId,
            HttpSession session
    ) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equalsIgnoreCase(lecturer.getRole())) {
            return ResponseEntity.status(403).build();
        }

        User stu = userRepository.findById(studentId).orElse(null);
        if (stu == null || stu.getLecturer() == null || !Objects.equals(stu.getLecturer().getId(), lecturer.getId())) {
            return ResponseEntity.status(403).build();
        }

        String url = stu.getFinalReportPdfPath();
        if (url == null || url.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = toUploadsResource(url);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String filename = "final_report_" + safeName(stu.getName()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ========== 3) 下载 Video（带权限校验） ==========
    @GetMapping("/lecturer/final-report/download/video/{studentId}")
    public ResponseEntity<Resource> downloadStudentFinalReportVideo(
            @PathVariable Long studentId,
            HttpSession session
    ) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equalsIgnoreCase(lecturer.getRole())) {
            return ResponseEntity.status(403).build();
        }

        User stu = userRepository.findById(studentId).orElse(null);
        if (stu == null || stu.getLecturer() == null || !Objects.equals(stu.getLecturer().getId(), lecturer.getId())) {
            return ResponseEntity.status(403).build();
        }

        String url = stu.getFinalReportVideoPath();
        if (url == null || url.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = toUploadsResource(url);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String filename = "final_report_video_" + safeName(stu.getName()) + ".mp4";
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ========== 工具方法：把 /uploads/... 转成文件系统 Resource ==========
    private Resource toUploadsResource(String publicUrl) {
        // publicUrl 形如：/uploads/final-report/{id}/report/xxx.pdf
        if (publicUrl == null || !publicUrl.startsWith("/uploads/")) return null;

        String relative = publicUrl.replaceFirst("^/uploads/", ""); // final-report/...
        Path filePath = Paths.get("uploads").toAbsolutePath().normalize().resolve(relative).normalize();

        try {
            return new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private String safeName(String name) {
        if (name == null) return "student";
        return name.replaceAll("[^a-zA-Z0-9_\\-]+", "_");
    }
}