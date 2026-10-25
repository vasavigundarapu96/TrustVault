
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.entity.Role;
import com.trustvault.trustvault.service.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
public class AuthController {

    private final UserService userService;

    private static final int MAX_RECOVERY_ATTEMPTS = 5;

    private static final String RECOVERY_EMAIL = "recoveryEmail";
    private static final String RECOVERY_ATTEMPTS = "recoveryAttempts";
    private static final String RESET_AUTHORIZED = "resetAuthorizedEmail";

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Display login page
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    // Display registration page
    @GetMapping("/register")
    public String showRegistrationPage(Model model) {
        model.addAttribute(
            "recoveryQuestions",
            userService.getRecoveryQuestions()
        );
        return "register";
    }

    // Process registration
    @PostMapping("/register")
    public String registerUser(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String recoveryQuestion,
            @RequestParam String recoveryAnswer,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (name == null || name.isBlank()
                || name.trim().length() > 100) {
            model.addAttribute("error",
                "Name is required and must be at most 100 characters.");
            model.addAttribute("recoveryQuestions",
                userService.getRecoveryQuestions());
            return "register";
        }

        if (email == null) {
            model.addAttribute("error", "Enter a valid email address.");
            model.addAttribute("recoveryQuestions",
                userService.getRecoveryQuestions());
            return "register";
        }

        String normalizedEmail =
            email.trim().toLowerCase(Locale.ROOT);

        if (normalizedEmail.length() > 150
                || !normalizedEmail.matches(
                    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            model.addAttribute("error",
                "Enter a valid email address.");
            model.addAttribute("recoveryQuestions",
                userService.getRecoveryQuestions());
            return "register";
        }

        if (password == null || !password.matches(
                "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8}$")) {
            model.addAttribute("error",
                "Password must be exactly 8 characters and contain at least one letter, one digit, and one special character.");
            model.addAttribute("recoveryQuestions",
                userService.getRecoveryQuestions());
            return "register";
        }

        if (recoveryAnswer == null
                || recoveryAnswer.isBlank()
                || recoveryAnswer.trim().length() > 100) {
            model.addAttribute("error",
                "Please enter a recovery answer of at most 100 characters.");
            model.addAttribute("recoveryQuestions",
                userService.getRecoveryQuestions());
            return "register";
        }

        try {
            userService.createUser(
                name.trim(),
                normalizedEmail,
                password,
                Role.VIEWER,
                recoveryQuestion,
                recoveryAnswer
            );

            redirectAttributes.addFlashAttribute(
                "registeredMessage",
                "Registration successful! Please log in."
            );

            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("recoveryQuestions",
                userService.getRecoveryQuestions());
            return "register";
        }
    }

    // Display forgot-password page
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    // Find recovery question for submitted email
    @PostMapping("/forgot-password")
    public String beginRecovery(
            @RequestParam String email,
            HttpSession session,
            Model model) {

        // Clear any previous recovery attempt in this session.
        session.removeAttribute(RECOVERY_EMAIL);
        session.removeAttribute(RECOVERY_ATTEMPTS);
        session.removeAttribute(RESET_AUTHORIZED);

        if (email == null || email.isBlank()
                || email.trim().length() > 150) {
            model.addAttribute("error",
                "Please enter a valid email address.");
            return "forgot-password";
        }

        String normalizedEmail =
            email.trim().toLowerCase(Locale.ROOT);

        String question =
            userService.getRecoveryQuestion(normalizedEmail);

        if (question == null) {
            // Do not reveal whether an account exists.
            model.addAttribute("message",
                "If an eligible account exists, recovery can continue. Check your details and try again.");
            return "forgot-password";
        }

        session.setAttribute(RECOVERY_EMAIL, normalizedEmail);
        session.setAttribute(RECOVERY_ATTEMPTS, 0);

        model.addAttribute("recoveryQuestion", question);
        return "verify-recovery";
    }

    // Verify recovery answer
    @PostMapping("/verify-recovery")
    public String verifyRecovery(
            @RequestParam String recoveryAnswer,
            HttpSession session,
            Model model) {

        String email =
            (String) session.getAttribute(RECOVERY_EMAIL);

        Integer attempts =
            (Integer) session.getAttribute(RECOVERY_ATTEMPTS);

        if (email == null || attempts == null) {
            return "redirect:/forgot-password";
        }

        if (attempts >= MAX_RECOVERY_ATTEMPTS) {
            clearRecoverySession(session);
            model.addAttribute("error",
                "Too many attempts. Please start recovery again.");
            return "forgot-password";
        }

        boolean valid = userService.verifyRecoveryAnswer(
            email, recoveryAnswer
        );

        if (!valid) {
            attempts++;
            session.setAttribute(RECOVERY_ATTEMPTS, attempts);

            if (attempts >= MAX_RECOVERY_ATTEMPTS) {
                clearRecoverySession(session);
                model.addAttribute("error",
                    "Too many incorrect answers. Please start recovery again.");
                return "forgot-password";
            }

            String question =
                userService.getRecoveryQuestion(email);

            if (question == null) {
                clearRecoverySession(session);
                return "redirect:/forgot-password";
            }

            model.addAttribute("recoveryQuestion", question);
            model.addAttribute("error",
                "Incorrect answer. Attempts remaining: "
                    + (MAX_RECOVERY_ATTEMPTS - attempts));

            return "verify-recovery";
        }

        // Authorize password reset only in this server-side session.
        session.setAttribute(RESET_AUTHORIZED, email);
        session.removeAttribute(RECOVERY_EMAIL);
        session.removeAttribute(RECOVERY_ATTEMPTS);

        return "redirect:/reset-password";
    }

    // Display password-reset page only after successful verification
    @GetMapping("/reset-password")
    public String showResetPasswordPage(HttpSession session) {
        if (session.getAttribute(RESET_AUTHORIZED) == null) {
            return "redirect:/forgot-password";
        }

        return "reset-password";
    }

    // Save new password
    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        String email =
            (String) session.getAttribute(RESET_AUTHORIZED);

        if (email == null) {
            return "redirect:/forgot-password";
        }

        if (password == null || !password.matches(
                "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8}$")) {
            model.addAttribute("error",
                "Password must be exactly 8 characters and contain at least one letter, one digit, and one special character.");
            return "reset-password";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error",
                "Passwords do not match.");
            return "reset-password";
        }

        try {
            userService.resetPassword(email, password);

            // A reset authorization must be used only once.
            clearRecoverySession(session);

            redirectAttributes.addFlashAttribute(
                "registeredMessage",
                "Password reset successful. Please log in."
            );

            return "redirect:/login";

        } catch (IllegalArgumentException e) {
            clearRecoverySession(session);
            model.addAttribute("error",
                "Unable to reset password. Please start recovery again.");
            return "forgot-password";
        }
    }

    private void clearRecoverySession(HttpSession session) {
        session.removeAttribute(RECOVERY_EMAIL);
        session.removeAttribute(RECOVERY_ATTEMPTS);
        session.removeAttribute(RESET_AUTHORIZED);
    }
}
