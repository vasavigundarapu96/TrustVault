
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.User;
import com.trustvault.trustvault.repository.UserRepository;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileController {

    private final UserRepository userRepository;

    public ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public String showProfile(Principal principal, Model model) {

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() ->
                    new IllegalArgumentException("User not found"));

        model.addAttribute("user", user);

        return "profile";
    }
}
