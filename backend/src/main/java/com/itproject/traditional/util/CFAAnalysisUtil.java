package com.itproject.traditional.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Enhanced Color Filter Array (CFA) Analysis implementation
 * Detects interpolation artifacts that may indicate image manipulation
 */
@Slf4j
@Component
public class CFAAnalysisUtil {
    
    /**
     * Perform CFA analysis on an image
     * 
     * @param imageStream Input image stream
     * @param method Analysis method (LAPLACIAN, GRADIENT)
     * @return CFA analysis result
     */
    public CfaResult performCFA(InputStream imageStream, String method) {
        try {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new IllegalArgumentException("Unable to read image from input stream");
            }
            
            BufferedImage heatmap;
            CfaMetrics metrics;
            
            switch (method.toUpperCase()) {
                case "GRADIENT":
                    heatmap = createGradientBasedHeatmap(image);
                    break;
                case "LAPLACIAN":
                default:
                    heatmap = createLaplacianBasedHeatmap(image);
                    break;
            }
            
            metrics = analyzeCfaHeatmap(heatmap, image);
            
            // Convert heatmap to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(heatmap, "PNG", baos);
            byte[] heatmapData = baos.toByteArray();
            
            return new CfaResult(
                heatmap,
                heatmapData,
                metrics.getInterpolationAnomalies(),
                metrics.getConfidenceScore(),
                metrics.getAnalysisSummary()
            );
            
        } catch (Exception e) {
            log.error("Error performing CFA analysis", e);
            throw new RuntimeException("Failed to perform CFA analysis", e);
        }
    }
    
    /**
     * Create heatmap using Laplacian operator (default method from original code)
     */
    private BufferedImage createLaplacianBasedHeatmap(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage heatmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        // Laplacian kernel
        int[][] kernel = {
            {0, -1, 0},
            {-1, 4, -1},
            {0, -1, 0}
        };
        
        double[][] scores = new double[width][height];
        double maxScore = 0;
        
        // Apply Laplacian operator to green channel (most sensitive to CFA artifacts)
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double score = 0;
                
                // Apply kernel
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        Color c = new Color(image.getRGB(x + j - 1, y + i - 1));
                        score += c.getGreen() * kernel[i][j];
                    }
                }
                
                scores[x][y] = Math.abs(score);
                maxScore = Math.max(maxScore, scores[x][y]);
            }
        }
        
        // Normalize and create heatmap
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (maxScore > 0) {
                    int intensity = (int) ((scores[x][y] / maxScore) * 255);
                    // Use color mapping: blue (low) to red (high)
                    Color heatColor = createHeatmapColor(intensity);
                    heatmap.setRGB(x, y, heatColor.getRGB());
                } else {
                    heatmap.setRGB(x, y, Color.BLACK.getRGB());
                }
            }
        }
        
        return heatmap;
    }
    
    /**
     * Create heatmap using gradient-based analysis
     */
    private BufferedImage createGradientBasedHeatmap(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage heatmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        double[][] gradientMagnitude = new double[width][height];
        double maxGradient = 0;
        
        // Calculate gradient magnitude
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // Get green channel values for gradient calculation
                int g00 = new Color(image.getRGB(x - 1, y - 1)).getGreen();
                int g10 = new Color(image.getRGB(x, y - 1)).getGreen();
                int g20 = new Color(image.getRGB(x + 1, y - 1)).getGreen();
                int g01 = new Color(image.getRGB(x - 1, y)).getGreen();
                int g21 = new Color(image.getRGB(x + 1, y)).getGreen();
                int g02 = new Color(image.getRGB(x - 1, y + 1)).getGreen();
                int g12 = new Color(image.getRGB(x, y + 1)).getGreen();
                int g22 = new Color(image.getRGB(x + 1, y + 1)).getGreen();
                
                // Sobel operators
                int gx = (g20 + 2 * g21 + g22) - (g00 + 2 * g01 + g02);
                int gy = (g02 + 2 * g12 + g22) - (g00 + 2 * g10 + g20);
                
                double magnitude = Math.sqrt(gx * gx + gy * gy);
                gradientMagnitude[x][y] = magnitude;
                maxGradient = Math.max(maxGradient, magnitude);
            }
        }
        
        // Normalize and create heatmap
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (maxGradient > 0) {
                    int intensity = (int) ((gradientMagnitude[x][y] / maxGradient) * 255);
                    Color heatColor = createHeatmapColor(intensity);
                    heatmap.setRGB(x, y, heatColor.getRGB());
                } else {
                    heatmap.setRGB(x, y, Color.BLACK.getRGB());
                }
            }
        }
        
        return heatmap;
    }
    
    /**
     * Create heat map color from intensity (0-255)
     */
    private Color createHeatmapColor(int intensity) {
        // Create blue-to-red heat map
        if (intensity < 64) {
            // Black to blue
            return new Color(0, 0, intensity * 4);
        } else if (intensity < 128) {
            // Blue to green
            int green = (intensity - 64) * 4;
            return new Color(0, green, 255 - green);
        } else if (intensity < 192) {
            // Green to yellow
            int red = (intensity - 128) * 4;
            return new Color(red, 255, 0);
        } else {
            // Yellow to red
            int green = 255 - (intensity - 192) * 4;
            return new Color(255, green, 0);
        }
    }
    
    /**
     * Analyze CFA heatmap to extract metrics
     */
    private CfaMetrics analyzeCfaHeatmap(BufferedImage heatmap, BufferedImage originalImage) {
        int width = heatmap.getWidth();
        int height = heatmap.getHeight();
        
        int highIntensityPixels = 0;
        int totalPixels = width * height;
        double totalIntensity = 0;
        
        // Threshold for detecting anomalies
        int intensityThreshold = 128; // Mid-range intensity
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(heatmap.getRGB(x, y));
                
                // Calculate intensity as maximum of RGB values
                int intensity = Math.max(Math.max(color.getRed(), color.getGreen()), color.getBlue());
                totalIntensity += intensity;
                
                if (intensity > intensityThreshold) {
                    highIntensityPixels++;
                }
            }
        }
        
        double avgIntensity = totalIntensity / totalPixels;
        double anomalyRatio = (double) highIntensityPixels / totalPixels;
        
        // Calculate confidence score based on intensity distribution
        double confidenceScore = Math.min(100, anomalyRatio * 100 * 1.5);
        
        // Count interpolation anomaly regions
        int interpolationAnomalies = countInterpolationAnomalies(heatmap, intensityThreshold);
        
        String analysisSummary = generateCfaAnalysisSummary(
            highIntensityPixels, totalPixels, avgIntensity, confidenceScore, interpolationAnomalies);
        
        return new CfaMetrics(interpolationAnomalies, confidenceScore, analysisSummary);
    }
    
    /**
     * Count interpolation anomaly regions
     */
    private int countInterpolationAnomalies(BufferedImage heatmap, int threshold) {
        int width = heatmap.getWidth();
        int height = heatmap.getHeight();
        boolean[][] visited = new boolean[width][height];
        int anomalies = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[x][y] && isHighIntensityPixel(heatmap, x, y, threshold)) {
                    // Found new anomaly region
                    floodFillCfa(heatmap, visited, x, y, threshold);
                    anomalies++;
                }
            }
        }
        
        return anomalies;
    }
    
    /**
     * Check if pixel has high intensity indicating potential interpolation artifact
     */
    private boolean isHighIntensityPixel(BufferedImage image, int x, int y, int threshold) {
        Color color = new Color(image.getRGB(x, y));
        int intensity = Math.max(Math.max(color.getRed(), color.getGreen()), color.getBlue());
        return intensity > threshold;
    }
    
    /**
     * Flood fill for CFA analysis
     */
    private void floodFillCfa(BufferedImage image, boolean[][] visited, int startX, int startY, int threshold) {
        if (startX < 0 || startX >= image.getWidth() || startY < 0 || startY >= image.getHeight() ||
            visited[startX][startY] || !isHighIntensityPixel(image, startX, startY, threshold)) {
            return;
        }
        
        visited[startX][startY] = true;
        
        // 4-connected neighbors for CFA analysis
        int[] dx = {-1, 1, 0, 0};
        int[] dy = {0, 0, -1, 1};
        
        for (int i = 0; i < 4; i++) {
            floodFillCfa(image, visited, startX + dx[i], startY + dy[i], threshold);
        }
    }
    
    /**
     * Generate human-readable CFA analysis summary
     */
    private String generateCfaAnalysisSummary(int highIntensityPixels, int totalPixels,
                                            double avgIntensity, double confidenceScore, int anomalies) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("CFA Analysis Results:\n");
        summary.append(String.format("- High intensity pixels: %d out of %d (%.2f%%)\n",
                                    highIntensityPixels, totalPixels, (double)highIntensityPixels/totalPixels*100));
        summary.append(String.format("- Average intensity: %.2f\n", avgIntensity));
        summary.append(String.format("- Interpolation anomaly regions: %d\n", anomalies));
        summary.append(String.format("- Confidence score: %.2f/100\n", confidenceScore));
        
        if (confidenceScore < 25) {
            summary.append("- Assessment: CFA pattern appears consistent with authentic image");
        } else if (confidenceScore < 50) {
            summary.append("- Assessment: Minor CFA inconsistencies detected");
        } else if (confidenceScore < 75) {
            summary.append("- Assessment: Significant CFA pattern disruption detected");
        } else {
            summary.append("- Assessment: Strong evidence of interpolation artifacts, likely manipulated");
        }
        
        return summary.toString();
    }
    
    @Data
    @AllArgsConstructor
    public static class CfaResult {
        private BufferedImage heatmapImage;
        private byte[] heatmapData;
        private int interpolationAnomalies;
        private double confidenceScore;
        private String analysisSummary;
    }
    
    @Data
    @AllArgsConstructor
    private static class CfaMetrics {
        private int interpolationAnomalies;
        private double confidenceScore;
        private String analysisSummary;
    }
}
