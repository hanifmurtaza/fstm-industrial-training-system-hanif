package com.example.itsystem.controller;

import com.example.itsystem.repository.PlacementRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminPlacementDeleteController {

    private final PlacementRepository placementRepository;

    public AdminPlacementDeleteController(PlacementRepository placementRepository) {
        this.placementRepository = placementRepository;
    }

    @PostMapping("/admin/placements/{id}/delete")
    public String deletePlacement(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {

        if (placementRepository.existsById(id)) {
            placementRepository.deleteById(id);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Placement deleted successfully."
            );
        } else {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Placement not found."
            );
        }

        return "redirect:/admin/placements";
    }
}
