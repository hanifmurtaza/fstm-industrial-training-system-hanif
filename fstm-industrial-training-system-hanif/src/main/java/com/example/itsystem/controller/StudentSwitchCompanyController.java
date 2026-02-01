package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/student")
public class StudentSwitchCompanyController {

    // ✅ 跟你系统一致：session 里存的是 "user"
    private boolean notStudent(HttpSession session) {
        User u = (User) session.getAttribute("user");
        if (u == null) return true;

        // ⚠️ 这里按你项目真实字段调整：
        // 如果你的 User 有 getRole() 且返回 "STUDENT" 就不用改
        return !"STUDENT".equalsIgnoreCase(u.getRole());

        // 如果你项目不是 getRole()，常见替代：
        // return !"STUDENT".equalsIgnoreCase(u.getUserType());
        // return u.getRole() != Role.STUDENT;
    }

    @GetMapping("/switch-company")
    public String page(HttpSession session, Model model) {
        if (notStudent(session)) return "redirect:/login";

        model.addAttribute("showContactModal", false);
        return "student/switch-company";
    }

    @PostMapping("/switch-company")
    public String submit(HttpSession session,
                         @RequestParam(value = "agree", required = false) String agree,
                         Model model) {

        if (notStudent(session)) return "redirect:/login";

        if (agree == null) {
            model.addAttribute("agreeError", "Please tick the agreement checkbox before submitting.");
            model.addAttribute("showContactModal", false);
            return "student/switch-company";
        }

        // ✅ 勾选同意后：弹出联系方式（占位符***）
        model.addAttribute("showContactModal", true);
        return "student/switch-company";
    }
}