package com.example.itsystem.config;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public LoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        String username = authentication.getName();

        User user = userRepository.findByUsernameIgnoreCase(username).orElse(null);

        // keep your legacy session attributes so existing controllers keep working
        HttpSession session = request.getSession(true);
        if (user != null) {
            session.setAttribute("user", user);

            Map<String, Object> auth = new HashMap<>();
            auth.put("id", user.getId());
            auth.put("role", user.getRole());
            auth.put("username", user.getUsername());
            session.setAttribute("auth", auth);

            // redirect based on role using your existing routes
            String role = user.getRole() == null ? "" : user.getRole().toLowerCase();

            switch (role) {
                case "admin" -> response.sendRedirect("/admin/dashboard");
                case "industry" -> response.sendRedirect("/industry/dashboard");
                case "teacher" -> response.sendRedirect("/lecturer/dashboard");
                case "student" -> response.sendRedirect("/student-dashboard");
                default -> response.sendRedirect("/login?error=true");
            }
            return;
        }

        response.sendRedirect("/login?error=true");
    }
}
