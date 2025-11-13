package com.example.itsystem.controller;

import com.example.itsystem.model.Document;
import com.example.itsystem.repository.DocumentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/admin/documents")
public class AdminDocumentsController {

    private final DocumentRepository documentRepository;

    public AdminDocumentsController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       Model model) {
        // simple listing; you can add filtering later
        List<Document> docs = documentRepository.findAll();

        // optional: order newest first if you have uploadedAt
        docs.sort(Comparator.comparing(Document::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        model.addAttribute("docs", docs);
        model.addAttribute("q", q);
        return "admin-documents";  // matches your existing template name
    }

    // Optional preview/delete handlers if you add buttons later:

    // @PostMapping("/delete/{id}")
    // public String delete(@PathVariable Long id) {
    //     documentRepository.deleteById(id);
    //     return "redirect:/admin/documents";
    // }
}
