package com.example.itsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import com.example.itsystem.model.Department;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_role", columnList = "role"),
                @Index(name = "idx_users_lecturer", columnList = "lecturer_id"),
                @Index(name = "idx_users_studentId", columnList = "studentId"),
                @Index(name = "idx_users_enabled", columnList = "enabled")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = {"username"})
                // ,@UniqueConstraint(name = "uk_users_studentId", columnNames = {"studentId"})
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 30)
    private String role; // "student" | "teacher" | "admin" | "industry"

    @Column(length = 120)
    private String name;

    @Column(length = 40)
    private String studentId;

    @Column(length = 40)
    private String session;

    @Column(length = 120)
    private String company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private User lecturer;

    // NEW: account status + access window
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    private LocalDate accessStart;   // nullable until Admin sets it
    private LocalDate accessEnd;     // nullable until Admin sets it

    @Enumerated(EnumType.STRING)
    @Column(length = 80)
    private Department department;



    // ---- getters & setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public User getLecturer() { return lecturer; }
    public void setLecturer(User lecturer) { this.lecturer = lecturer; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDate getAccessStart() { return accessStart; }
    public void setAccessStart(LocalDate accessStart) { this.accessStart = accessStart; }

    public LocalDate getAccessEnd() { return accessEnd; }
    public void setAccessEnd(LocalDate accessEnd) { this.accessEnd = accessEnd; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

}
