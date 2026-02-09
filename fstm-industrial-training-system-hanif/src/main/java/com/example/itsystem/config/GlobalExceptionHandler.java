package com.example.itsystem.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleAny(Exception ex, RedirectAttributes ra) {
        ra.addFlashAttribute("toast", "Something went wrong. Please try again.");
        return "redirect:/login";
    }
}
