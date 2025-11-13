package com.example.itsystem.controller;

import com.example.itsystem.model.SelfReflection;
import com.example.itsystem.model.User;
import com.example.itsystem.repository.SelfReflectionRepository;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/lecturer/reflection")
public class LecturerReflectionController {

    @Autowired
    private SelfReflectionRepository reflectionRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/history/{studentId}")
    public String viewStudentReflections(@PathVariable Long studentId, HttpSession session, Model model) {
        User lecturer = (User) session.getAttribute("user");
        if (lecturer == null || !"teacher".equals(lecturer.getRole())) {
            return "redirect:/login";
        }

        List<SelfReflection> reflections = reflectionRepository.findByStudentIdOrderBySubmittedAtDesc(studentId);
        User student = userRepository.findById(studentId).orElse(null);

        model.addAttribute("reflections", reflections);
        model.addAttribute("student", student);
        return "lecturer/reflection-history";
    }
}
