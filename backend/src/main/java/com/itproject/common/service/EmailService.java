package com.itproject.common.service;

import com.itproject.common.config.MailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Email service for sending various types of emails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties mailProperties;
    
    /**
     * Send password reset email
     */
    public CompletableFuture<Boolean> sendPasswordResetEmail(String to, String username, String resetUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Context context = new Context();
                context.setVariable("username", username);
                context.setVariable("resetUrl", resetUrl);
                
                String html = templateEngine.process("email/password-reset", context);
                
                sendHtmlEmail(to, "Password Reset Request - DeepFake Forensic", html);
                log.info("Password reset email sent successfully to: {}", to);
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send password reset email to: {}", to, e);
                return false;
            }
        });
    }
    
    /**
     * Send email verification email
     */
    public CompletableFuture<Boolean> sendEmailVerificationEmail(String to, String username, String verificationUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Context context = new Context();
                context.setVariable("username", username);
                context.setVariable("verificationUrl", verificationUrl);
                
                String html = templateEngine.process("email/email-verification", context);
                
                sendHtmlEmail(to, "Email Verification - DeepFake Forensic", html);
                log.info("Email verification email sent successfully to: {}", to);
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send email verification email to: {}", to, e);
                return false;
            }
        });
    }
    
    /**
     * Send analysis completion notification email
     */
    public CompletableFuture<Boolean> sendAnalysisCompleteEmail(String to, Map<String, Object> variables) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Context context = new Context();
                variables.forEach(context::setVariable);
                
                String html = templateEngine.process("email/analysis-complete", context);
                
                sendHtmlEmail(to, "Analysis Complete - DeepFake Forensic", html);
                log.info("Analysis complete email sent successfully to: {}", to);
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send analysis complete email to: {}", to, e);
                return false;
            }
        });
    }
    
    /**
     * Send generic notification email
     */
    public CompletableFuture<Boolean> sendNotificationEmail(String to, String subject, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendHtmlEmail(to, subject, content);
                log.info("Notification email sent successfully to: {}", to);
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send notification email to: {}", to, e);
                return false;
            }
        });
    }
    
    /**
     * Send HTML email
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(mailProperties.getFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }
}
