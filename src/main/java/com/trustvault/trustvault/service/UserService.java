
package com.trustvault.trustvault.service;

import com.trustvault.trustvault.entity.Role;
import com.trustvault.trustvault.entity.User;
import com.trustvault.trustvault.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<String> RECOVERY_QUESTIONS = Set.of(
        "What is the name of your first school?",
        "What is your childhood nickname?",
        "What is the name of your favorite teacher?",
        "What city were you born in?",
        "What was the name of your first pet?"
    );

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Keep the original registration method for compatibility.
    public User createUser(
            String name,
            String email,
            String rawPassword,
            Role role) {

        return createUser(
            name, email, rawPassword, role,
            null, null
        );
    }

    // Registration with recovery question and answer.
    public User createUser(
            String name,
            String email,
            String rawPassword,
            Role role,
            String recoveryQuestion,
            String recoveryAnswer) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                "Email already registered");
        }

        if (recoveryQuestion == null
                || !RECOVERY_QUESTIONS.contains(recoveryQuestion)) {
            throw new IllegalArgumentException(
                "Please select a valid recovery question.");
        }

        if (recoveryAnswer == null
                || recoveryAnswer.isBlank()
                || recoveryAnswer.trim().length() > 100) {
            throw new IllegalArgumentException(
                "Recovery answer is required and must be at most 100 characters.");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setRecoveryQuestion(recoveryQuestion);
        user.setRecoveryAnswer(
            passwordEncoder.encode(normalizeAnswer(recoveryAnswer))
        );

        return userRepository.save(user);
    }

    // Questions available to the registration form.
    public Set<String> getRecoveryQuestions() {
        return RECOVERY_QUESTIONS;
    }

    private String normalizeAnswer(String answer) {
        return Normalizer.normalize(
            answer.trim(),
            Normalizer.Form.NFKC
        ).toLowerCase(Locale.ROOT);
    }

    // Retrieve the saved question for the supplied email.
    @Transactional(readOnly = true)
    public String getRecoveryQuestion(String email) {
        return userRepository.findByEmail(
            email.trim().toLowerCase(Locale.ROOT)
        ).filter(User::isEnabled)
         .map(User::getRecoveryQuestion)
         .orElse(null);
    }

    // Verify an answer against its stored hash.
    @Transactional(readOnly = true)
    public boolean verifyRecoveryAnswer(
            String email, String recoveryAnswer) {

        if (email == null || recoveryAnswer == null
                || recoveryAnswer.isBlank()) {
            return false;
        }

        return userRepository.findByEmail(
            email.trim().toLowerCase(Locale.ROOT)
        ).filter(User::isEnabled)
         .filter(user -> user.getRecoveryAnswer() != null)
         .map(user -> passwordEncoder.matches(
             normalizeAnswer(recoveryAnswer),
             user.getRecoveryAnswer()
         ))
         .orElse(false);
    }

    // Save a new password after recovery has been verified.
    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(
            email.trim().toLowerCase(Locale.ROOT)
        ).filter(User::isEnabled)
         .orElseThrow(() -> new IllegalArgumentException(
             "Unable to reset password. Please restart recovery."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
