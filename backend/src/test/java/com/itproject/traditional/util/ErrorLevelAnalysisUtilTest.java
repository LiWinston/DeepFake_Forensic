package com.itproject.traditional.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ErrorLevelAnalysisUtilTest {

    private ErrorLevelAnalysisUtil errorLevelAnalysisUtil;

    @BeforeEach
    void setUp() {
        errorLevelAnalysisUtil = new ErrorLevelAnalysisUtil();
    }

    @Test
    void testElaResultCreation() {
        // Create a simple test image
        BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        byte[] testData = new byte[]{1, 2, 3, 4, 5};
        
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            testImage, testData, 5, 75.5, "Test analysis"
        );
        
        assertEquals(testImage, result.getResultImage());
        assertEquals(testData, result.getResultImageData());
        assertEquals(5, result.getSuspiciousRegions());
        assertEquals(75.5, result.getConfidenceScore());
        assertEquals("Test analysis", result.getAnalysisSummary());
    }

    @Test
    void testElaResultSettersAndGetters() {
        BufferedImage testImage = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        byte[] testData = new byte[]{1, 2, 3};
        
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            null, null, 0, 0.0, null
        );
        
        // Test setters
        result.setResultImage(testImage);
        result.setResultImageData(testData);
        result.setSuspiciousRegions(3);
        result.setConfidenceScore(85.2);
        result.setAnalysisSummary("Updated analysis");
        
        // Test getters
        assertEquals(testImage, result.getResultImage());
        assertEquals(testData, result.getResultImageData());
        assertEquals(3, result.getSuspiciousRegions());
        assertEquals(85.2, result.getConfidenceScore());
        assertEquals("Updated analysis", result.getAnalysisSummary());
    }

    @Test
    void testCreateSimpleTestImage() throws IOException {
        // Create a simple 10x10 test image
        BufferedImage testImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = testImage.createGraphics();
        
        // Fill with a simple pattern
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 5, 5);
        g2d.setColor(Color.BLUE);
        g2d.fillRect(5, 5, 5, 5);
        g2d.dispose();
        
        // Convert to input stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImage, "PNG", baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        
        // This should not throw an exception for a valid image
        assertNotNull(bais);
        assertTrue(baos.size() > 0);
    }

    @Test
    void testInvalidImageStream() {
        // Test with null input stream
        assertThrows(Exception.class, () -> {
            errorLevelAnalysisUtil.performELA(null, 80, 2);
        });
    }

    @Test
    void testInvalidImageData() {
        // Test with invalid image data
        ByteArrayInputStream invalidStream = new ByteArrayInputStream("not an image".getBytes());
        
        assertThrows(Exception.class, () -> {
            errorLevelAnalysisUtil.performELA(invalidStream, 80, 2);
        });
    }

    @Test
    void testElaResultWithNullValues() {
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            null, null, 0, 0.0, null
        );
        
        assertNull(result.getResultImage());
        assertNull(result.getResultImageData());
        assertEquals(0, result.getSuspiciousRegions());
        assertEquals(0.0, result.getConfidenceScore());
        assertNull(result.getAnalysisSummary());
    }

    @Test
    void testElaResultWithEdgeValues() {
        BufferedImage testImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        byte[] testData = new byte[0];
        
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            testImage, testData, Integer.MAX_VALUE, Double.MAX_VALUE, "Max values test"
        );
        
        assertEquals(testImage, result.getResultImage());
        assertEquals(testData, result.getResultImageData());
        assertEquals(Integer.MAX_VALUE, result.getSuspiciousRegions());
        assertEquals(Double.MAX_VALUE, result.getConfidenceScore());
        assertEquals("Max values test", result.getAnalysisSummary());
    }

    @Test
    void testElaResultWithNegativeValues() {
        BufferedImage testImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        byte[] testData = new byte[]{-1, -2};
        
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            testImage, testData, -1, -50.5, "Negative values test"
        );
        
        assertEquals(testImage, result.getResultImage());
        assertEquals(testData, result.getResultImageData());
        assertEquals(-1, result.getSuspiciousRegions());
        assertEquals(-50.5, result.getConfidenceScore());
        assertEquals("Negative values test", result.getAnalysisSummary());
    }

    @Test
    void testElaResultWithEmptyData() {
        BufferedImage testImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        byte[] emptyData = new byte[0];
        
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            testImage, emptyData, 0, 0.0, ""
        );
        
        assertEquals(testImage, result.getResultImage());
        assertEquals(0, result.getResultImageData().length);
        assertEquals(0, result.getSuspiciousRegions());
        assertEquals(0.0, result.getConfidenceScore());
        assertEquals("", result.getAnalysisSummary());
    }

    @Test
    void testElaResultToString() {
        BufferedImage testImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        byte[] testData = new byte[]{1, 2, 3};
        
        ErrorLevelAnalysisUtil.ElaResult result = new ErrorLevelAnalysisUtil.ElaResult(
            testImage, testData, 42, 67.8, "Test summary"
        );
        
        // Test that toString doesn't throw exception (Lombok @Data generates it)
        String resultString = result.toString();
        assertNotNull(resultString);
        assertTrue(resultString.contains("ErrorLevelAnalysisUtil"));
    }

    @Test
    void testElaResultEqualsAndHashCode() {
        BufferedImage testImage1 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        byte[] testData = new byte[]{1, 2, 3};
        
        ErrorLevelAnalysisUtil.ElaResult result1 = new ErrorLevelAnalysisUtil.ElaResult(
            testImage1, testData, 5, 75.5, "Test analysis"
        );
        
        ErrorLevelAnalysisUtil.ElaResult result2 = new ErrorLevelAnalysisUtil.ElaResult(
            testImage1, testData, 5, 75.5, "Test analysis" // Same image reference
        );
        
        // Test equals (should be equal if all fields match)
        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        
        // Test not equal
        ErrorLevelAnalysisUtil.ElaResult result3 = new ErrorLevelAnalysisUtil.ElaResult(
            testImage1, testData, 6, 75.5, "Test analysis" // Different suspicious regions
        );
        
        assertNotEquals(result1, result3);
    }
}
