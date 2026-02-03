package com.example.itsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  private final LoginSuccessHandler loginSuccessHandler;

  public SecurityConfig(LoginSuccessHandler loginSuccessHandler) {
    this.loginSuccessHandler = loginSuccessHandler;
  }


  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
            // CSRF is ENABLED by default. We keep it enabled.
            // Do NOT disable it if you want proper form security.

            .authorizeHttpRequests(auth -> auth
                    // public/static
                    .requestMatchers("/login", "/login2", "/error").permitAll()
                    .requestMatchers("/css/**", "/js/**", "/images/**", "/uploads/**", "/app.css").permitAll()

                    // public api
                    .requestMatchers("/api/opportunities/**").permitAll()

                    // role-based access (match your current URL structure)
                    .requestMatchers("/admin/**").hasRole("admin")
                    .requestMatchers("/industry/**").hasRole("industry")
                    .requestMatchers("/lecturer/**").hasRole("teacher")

                    // your student routes
                    .requestMatchers("/student/**", "/student-dashboard").hasRole("student")

                    // anything else must be authenticated
                    .anyRequest().authenticated()
            )
            .formLogin(form -> form
                    .loginPage("/login")           // GET page
                    .loginProcessingUrl("/login")  // POST handled by Spring Security
                    .successHandler(loginSuccessHandler)
                    .failureUrl("/login?error=true")
                    .permitAll()
            )
            .logout(logout -> logout
                    .logoutUrl("/logout")          // POST /logout
                    .logoutSuccessUrl("/login?logout=true")
                    .permitAll()
            )
            .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}
