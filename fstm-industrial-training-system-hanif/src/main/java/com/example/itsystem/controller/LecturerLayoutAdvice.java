package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.VisitScheduleRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class LecturerLayoutAdvice {

    private final VisitScheduleRepository visitScheduleRepository;

    public LecturerLayoutAdvice(VisitScheduleRepository visitScheduleRepository) {
        this.visitScheduleRepository = visitScheduleRepository;
    }

    @ModelAttribute
    public void addRescheduleAlert(Model model, HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) return;
        if (!"teacher".equals(user.getRole())) return;

        long pending = visitScheduleRepository
                .countByLecturerIdAndRescheduleRequestedTrue(user.getId());

        model.addAttribute("hasRescheduleAlert", pending > 0);
    }
}