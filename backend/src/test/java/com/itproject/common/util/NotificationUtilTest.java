package com.itproject.common.util;

import com.itproject.common.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationUtilTest {

    @Mock
    private EmailService emailService;

    private NotificationUtil notificationUtil;

    @BeforeEach
    void setUp() {
        notificationUtil = new NotificationUtil(emailService);
    }

    @Test
    void testSendAnalysisCompleteNotification_Success() {
        // Arrange
        String userEmail = "test@example.com";
        String username = "testuser";
        String projectName = "Test Project";
        String fileName = "test.jpg";
        String analysisStatus = "COMPLETED";
        String analysisResult = "AUTHENTIC";
        String duration = "2.5 minutes";
        String summary = "No anomalies detected";
        String viewResultUrl = "http://example.com/results/123";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(true);
        when(emailService.sendAnalysisCompleteEmail(eq(userEmail), any(Map.class)))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendAnalysisCompleteNotification(
                userEmail, username, projectName, fileName, analysisStatus,
                analysisResult, duration, summary, viewResultUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.join());
        verify(emailService, times(1)).sendAnalysisCompleteEmail(eq(userEmail), any(Map.class));
    }

    @Test
    void testSendAnalysisCompleteNotification_EmailServiceFailure() {
        // Arrange
        String userEmail = "test@example.com";
        String username = "testuser";
        String projectName = "Test Project";
        String fileName = "test.jpg";
        String analysisStatus = "COMPLETED";
        String analysisResult = "AUTHENTIC";
        String duration = "2.5 minutes";
        String summary = "No anomalies detected";
        String viewResultUrl = "http://example.com/results/123";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(false);
        when(emailService.sendAnalysisCompleteEmail(eq(userEmail), any(Map.class)))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendAnalysisCompleteNotification(
                userEmail, username, projectName, fileName, analysisStatus,
                analysisResult, duration, summary, viewResultUrl);

        // Assert
        assertNotNull(result);
        assertFalse(result.join());
        verify(emailService, times(1)).sendAnalysisCompleteEmail(eq(userEmail), any(Map.class));
    }

    @Test
    void testSendAnalysisCompleteNotification_VerifyTemplateVariables() {
        // Arrange
        String userEmail = "test@example.com";
        String username = "testuser";
        String projectName = "Test Project";
        String fileName = "test.jpg";
        String analysisStatus = "COMPLETED";
        String analysisResult = "SUSPICIOUS";
        String duration = "3.2 minutes";
        String summary = "Multiple anomalies detected";
        String viewResultUrl = "http://example.com/results/456";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(true);
        when(emailService.sendAnalysisCompleteEmail(eq(userEmail), any(Map.class)))
                .thenReturn(mockFuture);

        // Act
        notificationUtil.sendAnalysisCompleteNotification(
                userEmail, username, projectName, fileName, analysisStatus,
                analysisResult, duration, summary, viewResultUrl);

        // Assert
        verify(emailService).sendAnalysisCompleteEmail(eq(userEmail), argThat(variables -> {
            Map<String, Object> vars = (Map<String, Object>) variables;
            return vars.get("username").equals(username) &&
                   vars.get("projectName").equals(projectName) &&
                   vars.get("fileName").equals(fileName) &&
                   vars.get("analysisStatus").equals(analysisStatus) &&
                   vars.get("analysisResult").equals(analysisResult) &&
                   vars.get("duration").equals(duration) &&
                   vars.get("summary").equals(summary) &&
                   vars.get("viewResultUrl").equals(viewResultUrl);
        }));
    }

    @Test
    void testSendNotification_Success() {
        // Arrange
        String userEmail = "test@example.com";
        String subject = "Test Subject";
        String content = "Test content";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(true);
        when(emailService.sendNotificationEmail(userEmail, subject, content))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendNotification(userEmail, subject, content);

        // Assert
        assertNotNull(result);
        assertTrue(result.join());
        verify(emailService, times(1)).sendNotificationEmail(userEmail, subject, content);
    }

    @Test
    void testSendNotification_EmailServiceFailure() {
        // Arrange
        String userEmail = "test@example.com";
        String subject = "Test Subject";
        String content = "Test content";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(false);
        when(emailService.sendNotificationEmail(userEmail, subject, content))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendNotification(userEmail, subject, content);

        // Assert
        assertNotNull(result);
        assertFalse(result.join());
        verify(emailService, times(1)).sendNotificationEmail(userEmail, subject, content);
    }

    @Test
    void testSendAnalysisCompleteNotification_WithNullValues() {
        // Arrange
        String userEmail = "test@example.com";
        String username = "testuser";
        String projectName = "Test Project";
        String fileName = "test.jpg";
        String analysisStatus = "COMPLETED";
        String analysisResult = null; // null value
        String duration = "2.5 minutes";
        String summary = null; // null value
        String viewResultUrl = "http://example.com/results/123";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(true);
        when(emailService.sendAnalysisCompleteEmail(eq(userEmail), any(Map.class)))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendAnalysisCompleteNotification(
                userEmail, username, projectName, fileName, analysisStatus,
                analysisResult, duration, summary, viewResultUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.join());
        verify(emailService, times(1)).sendAnalysisCompleteEmail(eq(userEmail), any(Map.class));
    }

    @Test
    void testSendNotification_WithEmptyStrings() {
        // Arrange
        String userEmail = "test@example.com";
        String subject = ""; // empty string
        String content = ""; // empty string

        CompletableFuture<Boolean> mockFuture = CompletableFuture.completedFuture(true);
        when(emailService.sendNotificationEmail(userEmail, subject, content))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendNotification(userEmail, subject, content);

        // Assert
        assertNotNull(result);
        assertTrue(result.join());
        verify(emailService, times(1)).sendNotificationEmail(userEmail, subject, content);
    }

    @Test
    void testSendAnalysisCompleteNotification_EmailServiceThrowsException() {
        // Arrange
        String userEmail = "test@example.com";
        String username = "testuser";
        String projectName = "Test Project";
        String fileName = "test.jpg";
        String analysisStatus = "COMPLETED";
        String analysisResult = "AUTHENTIC";
        String duration = "2.5 minutes";
        String summary = "No anomalies detected";
        String viewResultUrl = "http://example.com/results/123";

        CompletableFuture<Boolean> mockFuture = CompletableFuture.failedFuture(new RuntimeException("Email service error"));
        when(emailService.sendAnalysisCompleteEmail(eq(userEmail), any(Map.class)))
                .thenReturn(mockFuture);

        // Act
        CompletableFuture<Boolean> result = notificationUtil.sendAnalysisCompleteNotification(
                userEmail, username, projectName, fileName, analysisStatus,
                analysisResult, duration, summary, viewResultUrl);

        // Assert
        assertNotNull(result);
        assertThrows(RuntimeException.class, () -> result.join());
        verify(emailService, times(1)).sendAnalysisCompleteEmail(eq(userEmail), any(Map.class));
    }
}
