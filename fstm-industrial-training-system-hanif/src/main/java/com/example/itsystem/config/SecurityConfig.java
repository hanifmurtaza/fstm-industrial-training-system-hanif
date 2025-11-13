package com.example.itsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    // public endpoints for login-page feed + images
                    .requestMatchers("/api/opportunities", "/api/opportunities/**").permitAll()
                    .requestMatchers("/uploads/**").permitAll()
                    // keep the rest open for now (you gate via session checks in controllers)
                    .anyRequest().permitAll()
            )
            .formLogin(login -> login.disable())
            .httpBasic(basic -> basic.disable());

    return http.build();
  }
}
