package com.example.itsystem.service;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AuthUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // your DB role values: admin / industry / teacher / student
        String role = "ROLE_" + u.getRole();

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getUsername())
                .password(u.getPassword()) // already BCrypt
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .disabled(u.getEnabled() != null && !u.getEnabled())
                .build();
    }
}
