package com.example.itsystem.service;

import com.example.itsystem.model.User;
import java.util.List;

public interface UserService {
    List<User> getStudentsAssignedToLecturer(Long lecturerId);
    User findByUsername(String username);
    User getById(Long id);
}


