package com.example.itsystem.config;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.util.Set;

@Component
public class AccessWindowInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    // Paths that should NOT be blocked
    private static final Set<String> WHITELIST_PREFIX = Set.of(
            "/login", "/logout", "/css", "/js", "/images", "/uploads", "/public", "/error"
    );

    public AccessWindowInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();

        // allow whitelist
        for (String p : WHITELIST_PREFIX) {
            if (uri.startsWith(p)) return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) return true;

        Object sessionUser = session.getAttribute("user");
        if (!(sessionUser instanceof User u)) return true;

        // Re-fetch from DB to ensure latest enabled/window applied
        User user = userRepository.findById(u.getId()).orElse(null);
        if (user == null) return true;

        // ✅ 1) Enforce enabled only when explicitly FALSE
        Boolean enabled = user.getEnabled();
        if (enabled != null && !enabled) {
            session.invalidate();
            response.sendRedirect("/login?disabled=true");
            return false;
        }

        // ✅ 2) Enforce access window ONLY for students
        String role = user.getRole();
        boolean isStudent = role != null && role.equalsIgnoreCase("student");

        if (isStudent) {
            LocalDate today = LocalDate.now();
            LocalDate start = user.getAccessStart();
            LocalDate end = user.getAccessEnd();

            if (start != null && today.isBefore(start)) {
                session.invalidate();
                response.sendRedirect("/login?notstarted=true");
                return false;
            }

            if (end != null && today.isAfter(end)) {
                session.invalidate();
                response.sendRedirect("/login?expired=true");
                return false;
            }
        }

        return true;
    }
}
