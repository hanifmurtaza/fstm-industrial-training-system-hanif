package com.example.itsystem.service.impl;

import com.example.itsystem.model.User;
import com.example.itsystem.repository.UserRepository;
import com.example.itsystem.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<User> getStudentsAssignedToLecturer(Long lecturerId) {
        return userRepository.findByLecturer_Id(lecturerId); // ✅ 关键点
    }
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    @Override
    public User getById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}
