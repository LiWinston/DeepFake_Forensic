package com.itproject.common.util;

import com.itproject.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for sending analysis completion notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationUtil {
    
    private final EmailService emailService;
    
    /**
     * Send analysis completion notification
     */
    public CompletableFuture<Boolean> sendAnalysisCompleteNotification(
            String userEmail, 
            String username,
            String projectName,
            String fileName,
            String analysisStatus,
            String analysisResult,
            String duration,
            String summary,
            String viewResultUrl) {
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("username", username);
        variables.put("projectName", projectName);
        variables.put("fileName", fileName);
        variables.put("analysisStatus", analysisStatus);
        variables.put("analysisResult", analysisResult);
        variables.put("duration", duration);
        variables.put("summary", summary);
        variables.put("viewResultUrl", viewResultUrl);
        
        return emailService.sendAnalysisCompleteEmail(userEmail, variables);
    }
    
    /**
     * Send generic notification
     */
    public CompletableFuture<Boolean> sendNotification(String userEmail, String subject, String content) {
        return emailService.sendNotificationEmail(userEmail, subject, content);
    }
}
