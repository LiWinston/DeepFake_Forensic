package com.itproject.traditional.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Error Level Analysis implementation
 * Based on the concept but improved for production use
 */
@Slf4j
@Component
public class ErrorLevelAnalysisUtil {
    
    /**
     * Perform Error Level Analysis on an image
     * 
     * @param imageStream Input image stream
     * @param quality JPEG quality (0-100)
     * @param scale Difference amplification scale
     * @return ELA analysis result
     */
    public ElaResult performELA(InputStream imageStream, int quality, int scale) {
        try {
            BufferedImage originalImage = ImageIO.read(imageStream);
            if (originalImage == null) {
                throw new IllegalArgumentException("Unable to read image from input stream");
            }
            
            // Step 1: Re-save image with specified JPEG quality
            BufferedImage resavedImage = recompressImage(originalImage, quality);
            
            // Step 2: Calculate pixel differences
            BufferedImage elaImage = createElaImage(originalImage, resavedImage, scale);
            
            // Step 3: Analyze the ELA result
            ElaMetrics metrics = analyzeElaImage(elaImage, originalImage);
            
            // Convert result image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(elaImage, "PNG", baos);
            byte[] resultImageData = baos.toByteArray();
            
            return new ElaResult(
                elaImage,
                resultImageData,
                metrics.getSuspiciousRegions(),
                metrics.getConfidenceScore(),
                metrics.getAnalysisSummary()
            );
            
        } catch (Exception e) {
            log.error("Error performing ELA analysis", e);
            throw new RuntimeException("Failed to perform ELA analysis", e);
        }
    }
    
    /**
     * Recompress image with specified JPEG quality
     */
    private BufferedImage recompressImage(BufferedImage image, int quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Get JPEG writer
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / 100.0f);
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
        }
        
        // Read back the compressed image
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        return ImageIO.read(bais);
    }
    
    /**
     * Create ELA difference image
     */
    private BufferedImage createElaImage(BufferedImage original, BufferedImage resaved, int scale) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage elaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color originalColor = new Color(original.getRGB(x, y));
                Color resavedColor = new Color(resaved.getRGB(x, y));
                
                // Calculate absolute differences
                int diffRed = Math.abs(originalColor.getRed() - resavedColor.getRed());
                int diffGreen = Math.abs(originalColor.getGreen() - resavedColor.getGreen());
                int diffBlue = Math.abs(originalColor.getBlue() - resavedColor.getBlue());
                
                // Amplify differences
                int amplifiedRed = Math.min(255, diffRed * scale);
                int amplifiedGreen = Math.min(255, diffGreen * scale);
                int amplifiedBlue = Math.min(255, diffBlue * scale);
                
                elaImage.setRGB(x, y, new Color(amplifiedRed, amplifiedGreen, amplifiedBlue).getRGB());
            }
        }
        
        return elaImage;
    }
    
    /**
     * Analyze ELA image to extract metrics
     */
    private ElaMetrics analyzeElaImage(BufferedImage elaImage, BufferedImage originalImage) {
        int width = elaImage.getWidth();
        int height = elaImage.getHeight();
        
        int suspiciousPixels = 0;
        int totalPixels = width * height;
        double totalBrightness = 0;
        
        // Define threshold for suspicious pixels (bright areas in ELA)
        int brightnessThreshold = 30;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color elaColor = new Color(elaImage.getRGB(x, y));
                
                // Calculate pixel brightness (luminance)
                double brightness = (elaColor.getRed() * 0.299 + 
                                   elaColor.getGreen() * 0.587 + 
                                   elaColor.getBlue() * 0.114);
                
                totalBrightness += brightness;
                
                if (brightness > brightnessThreshold) {
                    suspiciousPixels++;
                }
            }
        }
        
        double avgBrightness = totalBrightness / totalPixels;
        double suspiciousRatio = (double) suspiciousPixels / totalPixels;
        
        // Calculate confidence score (0-100)
        double confidenceScore = Math.min(100, suspiciousRatio * 100 * 2); // Scale up for visibility
        
        // Count suspicious regions (connected components of suspicious pixels)
        int suspiciousRegions = countSuspiciousRegions(elaImage, brightnessThreshold);
        
        String analysisSummary = generateElaAnalysisSummary(
            suspiciousPixels, totalPixels, avgBrightness, confidenceScore, suspiciousRegions);
        
        return new ElaMetrics(suspiciousRegions, confidenceScore, analysisSummary);
    }
    
    /**
     * Count suspicious regions using connected component analysis
     */
    private int countSuspiciousRegions(BufferedImage elaImage, int threshold) {
        int width = elaImage.getWidth();
        int height = elaImage.getHeight();
        boolean[][] visited = new boolean[width][height];
        int regions = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[x][y] && isSuspiciousPixel(elaImage, x, y, threshold)) {
                    // Found new suspicious region, perform flood fill
                    floodFill(elaImage, visited, x, y, threshold);
                    regions++;
                }
            }
        }
        
        return regions;
    }
    
    /**
     * Check if pixel is suspicious based on brightness
     */
    private boolean isSuspiciousPixel(BufferedImage image, int x, int y, int threshold) {
        Color color = new Color(image.getRGB(x, y));
        double brightness = (color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114);
        return brightness > threshold;
    }
    
    /**
     * Flood fill algorithm to mark connected suspicious pixels (iterative implementation)
     */
    private void floodFill(BufferedImage image, boolean[][] visited, int startX, int startY, int threshold) {
        // Use iterative implementation to avoid stack overflow for large connected regions
        java.util.Stack<int[]> stack = new java.util.Stack<>();
        stack.push(new int[]{startX, startY});
        
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
        
        int maxRegionSize = 50000; // Limit region size to prevent excessive processing
        int processedPixels = 0;
        
        while (!stack.isEmpty() && processedPixels < maxRegionSize) {
            int[] current = stack.pop();
            int x = current[0];
            int y = current[1];
            
            // Check bounds and if already visited or not suspicious
            if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight() ||
                visited[x][y] || !isSuspiciousPixel(image, x, y, threshold)) {
                continue;
            }
            
            visited[x][y] = true;
            processedPixels++;
            
            // Add 8-connected neighbors to stack
            for (int i = 0; i < 8; i++) {
                int newX = x + dx[i];
                int newY = y + dy[i];
                
                // Only add to stack if it's within bounds, hasn't been visited, and is suspicious
                if (newX >= 0 && newX < image.getWidth() && 
                    newY >= 0 && newY < image.getHeight() && 
                    !visited[newX][newY] && 
                    isSuspiciousPixel(image, newX, newY, threshold)) {
                    stack.push(new int[]{newX, newY});
                }
            }
        }
        
        if (processedPixels >= maxRegionSize) {
            log.debug("Flood fill reached maximum region size limit: {}", maxRegionSize);
        }
    }
    
    /**
     * Generate human-readable analysis summary
     */
    private String generateElaAnalysisSummary(int suspiciousPixels, int totalPixels, 
                                            double avgBrightness, double confidenceScore, int regions) {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("ELA Analysis Results:\n"));
        summary.append(String.format("- Suspicious pixels: %d out of %d (%.2f%%)\n", 
                                    suspiciousPixels, totalPixels, (double)suspiciousPixels/totalPixels*100));
        summary.append(String.format("- Average ELA brightness: %.2f\n", avgBrightness));
        summary.append(String.format("- Suspicious regions detected: %d\n", regions));
        summary.append(String.format("- Confidence score: %.2f/100\n", confidenceScore));
        
        if (confidenceScore < 20) {
            summary.append("- Assessment: Image appears authentic with minimal compression artifacts");
        } else if (confidenceScore < 50) {
            summary.append("- Assessment: Some suspicious areas detected, may indicate minor editing");
        } else if (confidenceScore < 80) {
            summary.append("- Assessment: Significant suspicious areas detected, likely edited");
        } else {
            summary.append("- Assessment: High probability of manipulation detected");
        }
        
        return summary.toString();
    }
    
    @Data
    @AllArgsConstructor
    public static class ElaResult {
        private BufferedImage resultImage;
        private byte[] resultImageData;
        private int suspiciousRegions;
        private double confidenceScore;
        private String analysisSummary;
    }
    
    @Data
    @AllArgsConstructor
    private static class ElaMetrics {
        private int suspiciousRegions;
        private double confidenceScore;
        private String analysisSummary;
    }
}
