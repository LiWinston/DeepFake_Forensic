package com.itproject.auth.event;

import com.itproject.auth.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener for user registration events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationEventListener {
    
    private final UserAccountService userAccountService;
    
    @EventListener
    @Async
    public void handleUserRegistration(UserRegistrationEvent event) {
        try {
            userAccountService.sendEmailVerification(event.getUser().getEmail());
            log.info("Email verification sent for new user: {}", event.getUser().getUsername());
        } catch (Exception e) {
            log.error("Failed to send email verification for user: {}", event.getUser().getUsername(), e);
        }
    }
}
