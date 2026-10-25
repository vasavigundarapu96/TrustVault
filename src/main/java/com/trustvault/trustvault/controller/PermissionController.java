
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.service.PermissionService;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/documents")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    // Revoke a user's permission to access a document
    @PostMapping("/{id}/revoke/{userId}")
    public String revokePermission(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long userId,
            RedirectAttributes redirectAttributes) {

        try {
            permissionService.revokePermission(
                    authentication.getName(),
                    id,
                    userId);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Permission revoked successfully!");

        } catch (IllegalArgumentException | SecurityException e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage());
        }

        return "redirect:/documents";
    }
}
