package com.example.itsystem.controller;

import com.example.itsystem.model.Placement;
import com.example.itsystem.model.PlacementStatus;
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
    public String cancelPlacement(@PathVariable Long id,
                                  RedirectAttributes ra) {

        Placement p = placementRepository.findById(id).orElse(null);
        if (p == null) {
            ra.addFlashAttribute("error", "Placement not found.");
            return "redirect:/admin/placements";
        }

        // ✅ Soft delete (safe)
        p.setStatus(PlacementStatus.CANCELLED);

        // ⚠️ DO NOT set supervisorUserId to null (DB column is NOT NULL)
        placementRepository.save(p);

        ra.addFlashAttribute("success", "Placement cancelled successfully.");
        return "redirect:/admin/placements";
    }
}
