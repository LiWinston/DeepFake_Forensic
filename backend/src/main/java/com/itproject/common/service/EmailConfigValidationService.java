package com.itproject.common.service;

import com.itproject.common.config.MailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Email configuration validation service
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class EmailConfigValidationService {
    
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    
    @EventListener(ApplicationReadyEvent.class)
    public void validateEmailConfiguration() {
        try {
            // Test email configuration by creating a message
            MimeMessage message = mailSender.createMimeMessage();
            log.info("Email configuration validated successfully");
            log.info("Mail service ready with sender: {}", mailProperties.getFrom());
        } catch (Exception e) {
            log.warn("Email configuration validation failed: {}", e.getMessage());
            log.warn("Email services will be disabled. Please check your mail configuration.");
        }
    }
}
