package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Enumeration;

@Controller
public class PasswordController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ====== 统一从 session 找当前登录用户 ======
    private User getCurrentUser(HttpSession session) {
        Object u1 = session.getAttribute("user");
        if (u1 instanceof User) return (User) u1;

        Object u2 = session.getAttribute("loggedInUser");
        if (u2 instanceof User) return (User) u2;

        Object u3 = session.getAttribute("currentUser");
        if (u3 instanceof User) return (User) u3;

        Object idObj = session.getAttribute("userId");
        if (idObj instanceof Long) return userRepository.findById((Long) idObj).orElse(null);
        if (idObj instanceof Integer) return userRepository.findById(((Integer) idObj).longValue()).orElse(null);

        Object unObj = session.getAttribute("username");
        if (unObj instanceof String s && !s.isBlank()) {
            return userRepository.findByUsername(s).orElse(null);
        }

        Enumeration<String> names = session.getAttributeNames();
        while (names.hasMoreElements()) {
            String key = names.nextElement();
            Object val = session.getAttribute(key);

            if (val instanceof User) return (User) val;

            if (val instanceof String s) {
                if (key.toLowerCase().contains("user") || key.toLowerCase().contains("name")) {
                    User found = userRepository.findByUsername(s).orElse(null);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    // =========================
    // GET: Student
    // =========================
    @GetMapping("/student/change-password")
    public String studentChangePasswordPage(HttpSession session, Model model) {
        User user = getCurrentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("activePage", "changePassword");
        return "student/change-password";
    }

    // =========================
    // GET: Visiting Lecturer
    // =========================
    @GetMapping("/lecturer/change-password")
    public String lecturerChangePasswordPage(HttpSession session, Model model) {
        User user = getCurrentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("activePage", "changePassword");
        return "lecturer/change-password";
    }

    // =========================
    // GET: Industry Supervisor
    // =========================
    @GetMapping("/industry/change-password")
    public String industryChangePasswordPage(HttpSession session, Model model) {
        User user = getCurrentUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("activePage", "changePassword");
        return "industry/change-password";
    }

    // =========================
    // POST: Student
    // =========================
    @PostMapping("/student/change-password")
    public String doStudentChangePassword(HttpSession session,
                                          @RequestParam("currentPassword") String currentPassword,
                                          @RequestParam("newPassword") String newPassword,
                                          @RequestParam("confirmPassword") String confirmPassword,
                                          RedirectAttributes ra) {
        return handleChangePassword(session, currentPassword, newPassword, confirmPassword, ra, "STUDENT");
    }

    // =========================
    // POST: Visiting Lecturer
    // =========================
    @PostMapping("/lecturer/change-password")
    public String doLecturerChangePassword(HttpSession session,
                                           @RequestParam("currentPassword") String currentPassword,
                                           @RequestParam("newPassword") String newPassword,
                                           @RequestParam("confirmPassword") String confirmPassword,
                                           RedirectAttributes ra) {
        return handleChangePassword(session, currentPassword, newPassword, confirmPassword, ra, "LECTURER");
    }

    // =========================
    // POST: Industry Supervisor
    // =========================
    @PostMapping("/industry/change-password")
    public String doIndustryChangePassword(HttpSession session,
                                           @RequestParam("currentPassword") String currentPassword,
                                           @RequestParam("newPassword") String newPassword,
                                           @RequestParam("confirmPassword") String confirmPassword,
                                           RedirectAttributes ra) {
        return handleChangePassword(session, currentPassword, newPassword, confirmPassword, ra, "INDUSTRY");
    }

    // =========================
    // 共用逻辑
    // =========================
    private String handleChangePassword(HttpSession session,
                                        String currentPassword,
                                        String newPassword,
                                        String confirmPassword,
                                        RedirectAttributes ra,
                                        String roleKey) {

        User user = getCurrentUser(session);
        if (user == null) return "redirect:/login";

        String redirectUrl = switch (roleKey) {
            case "STUDENT" -> "redirect:/student/change-password";
            case "INDUSTRY" -> "redirect:/industry/change-password";
            default -> "redirect:/lecturer/change-password";
        };

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            ra.addFlashAttribute("errorMessage", "Current password is incorrect.");
            return redirectUrl;
        }

        if (newPassword == null || newPassword.length() < 8) {
            ra.addFlashAttribute("errorMessage", "New password must be at least 8 characters.");
            return redirectUrl;
        }

        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "New password and confirm password do not match.");
            return redirectUrl;
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            ra.addFlashAttribute("errorMessage", "New password cannot be the same as current password.");
            return redirectUrl;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        ra.addFlashAttribute("successMessage", "Password updated successfully.");
        return redirectUrl;
    }
}