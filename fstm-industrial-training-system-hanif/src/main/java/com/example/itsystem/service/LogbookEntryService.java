package com.example.itsystem.service;

import com.example.itsystem.model.LogbookEntry;
import com.example.itsystem.repository.LogbookEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

@Service
public class LogbookEntryService {

    @Autowired

    private LogbookEntryRepository repository;

    public List<LogbookEntry> getByStudentId(Long studentId) {
        return repository.findByStudentId(studentId);
    }

    public LogbookEntry save(LogbookEntry entry) {
        return repository.save(entry);
    }

    public LogbookEntry getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }


}
