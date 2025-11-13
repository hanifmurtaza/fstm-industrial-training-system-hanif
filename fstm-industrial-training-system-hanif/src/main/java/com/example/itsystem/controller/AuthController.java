package com.example.itsystem.controller;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Root -> login
    @GetMapping("/")
    public String root() { return "redirect:/login"; }

    // ---- Login view ----
    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        Object auth = session.getAttribute("auth");
        if (auth instanceof Map<?, ?> m) {
            String role = String.valueOf(m.get("role")).toLowerCase();
            return switch (role) {
                case "student"  -> "redirect:/student-dashboard";   // your StudentController
                case "teacher"  -> "redirect:/lecturer/home";                // your teacher landing
                case "admin"    -> "redirect:/admin/dashboard";     // your AdminController
                case "industry" -> "redirect:/industry/dashboard";  // your industry page
                default         -> "login";
            };
        }
        return "login"; // templates/login.html
    }

    // ---- Login submit ----
    // ---- Login submit ----
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session) {

        String u = username.trim();

        // 1) Find user by username
        User user = userRepository.findByUsername(u).orElse(null);
        if (user == null) {
            return "redirect:/login?error=true";
        }

        // 2) Password: accept hashed *or* legacy plain text, then upgrade
        String stored = user.getPassword();
        boolean ok = false;

        if (stored != null) {
            if (passwordEncoder.matches(password, stored)) {
                ok = true; // already hashed and correct
            } else if (stored.equals(password)) {
                ok = true; // legacy plain text matched
                // auto-upgrade: hash it and save
                user.setPassword(passwordEncoder.encode(password));
                userRepository.save(user);
            }
        }

        if (!ok) {
            return "redirect:/login?error=true";
        }

        // 3) Get role from DB (NOT from form)
        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";

        // 4) Put both 'auth' (new) and 'user' (legacy) into session
        session.setAttribute("auth", Map.of(
                "id", user.getId(),
                "role", role,
                "username", user.getUsername()
        ));
        session.setAttribute("user", user);

        // 5) Redirect by role from DB
        return switch (role) {
            case "student"  -> "redirect:/student-dashboard";
            case "teacher"  -> "redirect:/lecturer/home";
            case "admin"    -> "redirect:/admin/dashboard";
            case "industry" -> "redirect:/industry/dashboard";
            default         -> "redirect:/login?error=true";
        };
    }




    // Accept both GET and POST so links and forms work
    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }


    private static boolean isBcrypt(String s) {
        return s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$");
    }
}
