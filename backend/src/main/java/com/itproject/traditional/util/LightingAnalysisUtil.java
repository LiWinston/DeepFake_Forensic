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
import java.util.ArrayList;
import java.util.List;

/**
 * Lighting Analysis implementation for detecting inconsistent lighting patterns
 * Simplified version that analyzes lighting without GUI interaction
 */
@Slf4j
@Component
public class LightingAnalysisUtil {
    
    /**
     * Perform automated lighting analysis on an image
     * 
     * @param imageStream Input image stream
     * @param sensitivity Analysis sensitivity (1-10)
     * @return Lighting analysis result
     */
    public LightingResult performLightingAnalysis(InputStream imageStream, int sensitivity) {
        try {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new IllegalArgumentException("Unable to read image from input stream");
            }
            
            // Analyze lighting patterns
            LightingData lightingData = analyzeLightingPatterns(image, sensitivity);
            
            // Create visualization
            BufferedImage analysisImage = createLightingVisualization(image, lightingData);
            
            // Convert result image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(analysisImage, "PNG", baos);
            byte[] analysisImageData = baos.toByteArray();
            
            // Calculate metrics
            LightingMetrics metrics = calculateLightingMetrics(lightingData, image);
            
            return new LightingResult(
                analysisImage,
                analysisImageData,
                metrics.getInconsistencies(),
                metrics.getConfidenceScore(),
                metrics.getAnalysisSummary(),
                lightingData
            );
            
        } catch (Exception e) {
            log.error("Error performing lighting analysis", e);
            throw new RuntimeException("Failed to perform lighting analysis", e);
        }
    }
    
    /**
     * Analyze lighting patterns in the image
     */
    private LightingData analyzeLightingPatterns(BufferedImage image, int sensitivity) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        List<LightingRegion> regions = new ArrayList<>();
        
        // Divide image into analysis regions (grid-based approach)
        int regionSize = Math.max(32, Math.min(width, height) / 10); // Adaptive region size
        
        for (int y = 0; y < height - regionSize; y += regionSize) {
            for (int x = 0; x < width - regionSize; x += regionSize) {
                LightingRegion region = analyzeRegionLighting(image, x, y, regionSize, sensitivity);
                if (region != null) {
                    regions.add(region);
                }
            }
        }
        
        // Detect inconsistencies between regions
        List<LightingInconsistency> inconsistencies = detectLightingInconsistencies(regions, sensitivity);
        
        return new LightingData(regions, inconsistencies);
    }
    
    /**
     * Analyze lighting characteristics of a specific region
     */
    private LightingRegion analyzeRegionLighting(BufferedImage image, int startX, int startY, int size, int sensitivity) {
        double totalBrightness = 0;
        double totalContrast = 0;
        int pixelCount = 0;
        
        double[] colorChannels = new double[3]; // RGB averages
        
        for (int y = startY; y < startY + size && y < image.getHeight(); y++) {
            for (int x = startX; x < startX + size && x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                
                // Calculate brightness (luminance)
                double brightness = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
                totalBrightness += brightness;
                
                // Accumulate color channels
                colorChannels[0] += color.getRed();
                colorChannels[1] += color.getGreen();
                colorChannels[2] += color.getBlue();
                
                pixelCount++;
            }
        }
        
        if (pixelCount == 0) return null;
        
        double avgBrightness = totalBrightness / pixelCount;
        
        // Calculate average color channels
        for (int i = 0; i < 3; i++) {
            colorChannels[i] /= pixelCount;
        }
        
        // Calculate local contrast
        double contrast = calculateRegionContrast(image, startX, startY, size);
        
        // Estimate lighting direction based on brightness gradients
        Point lightingDirection = estimateLightingDirection(image, startX, startY, size);
        
        return new LightingRegion(
            startX + size / 2, startY + size / 2, // Center point
            avgBrightness,
            contrast,
            colorChannels.clone(),
            lightingDirection
        );
    }
    
    /**
     * Calculate contrast within a region
     */
    private double calculateRegionContrast(BufferedImage image, int startX, int startY, int size) {
        List<Double> brightnessValues = new ArrayList<>();
        
        for (int y = startY; y < startY + size && y < image.getHeight(); y++) {
            for (int x = startX; x < startX + size && x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                double brightness = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
                brightnessValues.add(brightness);
            }
        }
        
        if (brightnessValues.size() < 2) return 0;
        
        // Calculate standard deviation as a measure of contrast
        double mean = brightnessValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = brightnessValues.stream()
                                        .mapToDouble(brightness -> Math.pow(brightness - mean, 2))
                                        .average().orElse(0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Estimate lighting direction using gradient analysis
     */
    private Point estimateLightingDirection(BufferedImage image, int startX, int startY, int size) {
        double totalGradX = 0;
        double totalGradY = 0;
        int count = 0;
        
        for (int y = startY + 1; y < startY + size - 1 && y < image.getHeight() - 1; y++) {
            for (int x = startX + 1; x < startX + size - 1 && x < image.getWidth() - 1; x++) {
                // Calculate gradients
                Color leftColor = new Color(image.getRGB(x - 1, y));
                Color rightColor = new Color(image.getRGB(x + 1, y));
                Color topColor = new Color(image.getRGB(x, y - 1));
                Color bottomColor = new Color(image.getRGB(x, y + 1));
                
                double leftBrightness = 0.299 * leftColor.getRed() + 0.587 * leftColor.getGreen() + 0.114 * leftColor.getBlue();
                double rightBrightness = 0.299 * rightColor.getRed() + 0.587 * rightColor.getGreen() + 0.114 * rightColor.getBlue();
                double topBrightness = 0.299 * topColor.getRed() + 0.587 * topColor.getGreen() + 0.114 * topColor.getBlue();
                double bottomBrightness = 0.299 * bottomColor.getRed() + 0.587 * bottomColor.getGreen() + 0.114 * bottomColor.getBlue();
                
                totalGradX += (rightBrightness - leftBrightness);
                totalGradY += (bottomBrightness - topBrightness);
                count++;
            }
        }
        
        if (count > 0) {
            totalGradX /= count;
            totalGradY /= count;
        }
        
        return new Point((int) totalGradX, (int) totalGradY);
    }
    
    /**
     * Detect inconsistencies between lighting regions
     */
    private List<LightingInconsistency> detectLightingInconsistencies(List<LightingRegion> regions, int sensitivity) {
        List<LightingInconsistency> inconsistencies = new ArrayList<>();
        
        if (regions.size() < 2) return inconsistencies;
        
        // Calculate thresholds based on sensitivity
        double brightnessThreshold = 50.0 - (sensitivity * 3.0); // More sensitive = lower threshold
        double contrastThreshold = 30.0 - (sensitivity * 2.0);
        double colorThreshold = 40.0 - (sensitivity * 2.5);
        
        for (int i = 0; i < regions.size(); i++) {
            for (int j = i + 1; j < regions.size(); j++) {
                LightingRegion region1 = regions.get(i);
                LightingRegion region2 = regions.get(j);
                
                // Check brightness inconsistency
                double brightnessDiff = Math.abs(region1.getAvgBrightness() - region2.getAvgBrightness());
                if (brightnessDiff > brightnessThreshold) {
                    inconsistencies.add(new LightingInconsistency(
                        "BRIGHTNESS", region1, region2, brightnessDiff,
                        String.format("Significant brightness difference: %.2f", brightnessDiff)
                    ));
                }
                
                // Check contrast inconsistency
                double contrastDiff = Math.abs(region1.getContrast() - region2.getContrast());
                if (contrastDiff > contrastThreshold) {
                    inconsistencies.add(new LightingInconsistency(
                        "CONTRAST", region1, region2, contrastDiff,
                        String.format("Significant contrast difference: %.2f", contrastDiff)
                    ));
                }
                
                // Check color temperature inconsistency
                double colorDiff = calculateColorDifference(region1.getColorChannels(), region2.getColorChannels());
                if (colorDiff > colorThreshold) {
                    inconsistencies.add(new LightingInconsistency(
                        "COLOR_TEMPERATURE", region1, region2, colorDiff,
                        String.format("Significant color temperature difference: %.2f", colorDiff)
                    ));
                }
            }
        }
        
        return inconsistencies;
    }
    
    /**
     * Calculate color difference between two regions
     */
    private double calculateColorDifference(double[] color1, double[] color2) {
        double sum = 0;
        for (int i = 0; i < Math.min(color1.length, color2.length); i++) {
            sum += Math.pow(color1[i] - color2[i], 2);
        }
        return Math.sqrt(sum);
    }
    
    /**
     * Create visualization of lighting analysis
     */
    private BufferedImage createLightingVisualization(BufferedImage original, LightingData lightingData) {
        BufferedImage result = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        
        // Draw original image with reduced opacity
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2d.drawImage(original, 0, 0, null);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        
        // Draw lighting regions
        for (LightingRegion region : lightingData.getRegions()) {
            // Color-code based on brightness
            int brightness = (int) Math.min(255, region.getAvgBrightness());
            Color regionColor = new Color(255 - brightness, brightness, 128, 100);
            
            g2d.setColor(regionColor);
            g2d.fillOval(region.getCenterX() - 10, region.getCenterY() - 10, 20, 20);
        }
        
        // Draw inconsistencies
        g2d.setStroke(new BasicStroke(3.0f));
        for (LightingInconsistency inconsistency : lightingData.getInconsistencies()) {
            Color inconsistencyColor;
            switch (inconsistency.getType()) {
                case "BRIGHTNESS":
                    inconsistencyColor = Color.RED;
                    break;
                case "CONTRAST":
                    inconsistencyColor = Color.ORANGE;
                    break;
                case "COLOR_TEMPERATURE":
                    inconsistencyColor = Color.YELLOW;
                    break;
                default:
                    inconsistencyColor = Color.MAGENTA;
            }
            
            g2d.setColor(inconsistencyColor);
            g2d.drawLine(
                inconsistency.getRegion1().getCenterX(), inconsistency.getRegion1().getCenterY(),
                inconsistency.getRegion2().getCenterX(), inconsistency.getRegion2().getCenterY()
            );
        }
        
        g2d.dispose();
        return result;
    }
    
    /**
     * Calculate lighting analysis metrics
     */
    private LightingMetrics calculateLightingMetrics(LightingData lightingData, BufferedImage image) {
        int inconsistencies = lightingData.getInconsistencies().size();
        int totalRegions = lightingData.getRegions().size();
        
        // Calculate confidence based on inconsistency ratio
        double inconsistencyRatio = totalRegions > 0 ? (double) inconsistencies / totalRegions : 0;
        double confidenceScore = Math.min(100, inconsistencyRatio * 150); // Scale factor
        
        String analysisSummary = generateLightingAnalysisSummary(inconsistencies, totalRegions, confidenceScore);
        
        return new LightingMetrics(inconsistencies, confidenceScore, analysisSummary);
    }
    
    /**
     * Generate human-readable lighting analysis summary
     */
    private String generateLightingAnalysisSummary(int inconsistencies, int totalRegions, double confidenceScore) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Lighting Analysis Results:\n");
        summary.append(String.format("- Analyzed regions: %d\n", totalRegions));
        summary.append(String.format("- Lighting inconsistencies found: %d\n", inconsistencies));
        summary.append(String.format("- Confidence score: %.2f/100\n", confidenceScore));
        
        if (inconsistencies == 0) {
            summary.append("- Assessment: Lighting appears consistent across the image");
        } else if (inconsistencies < 3) {
            summary.append("- Assessment: Minor lighting inconsistencies detected");
        } else if (inconsistencies < 8) {
            summary.append("- Assessment: Moderate lighting inconsistencies suggest possible manipulation");
        } else {
            summary.append("- Assessment: Significant lighting inconsistencies indicate likely manipulation");
        }
        
        return summary.toString();
    }
    
    @Data
    @AllArgsConstructor
    public static class LightingResult {
        private BufferedImage analysisImage;
        private byte[] analysisImageData;
        private int inconsistencies;
        private double confidenceScore;
        private String analysisSummary;
        private LightingData lightingData;
    }
    
    @Data
    @AllArgsConstructor
    public static class LightingData {
        private List<LightingRegion> regions;
        private List<LightingInconsistency> inconsistencies;
    }
    
    @Data
    @AllArgsConstructor
    public static class LightingRegion {
        private int centerX;
        private int centerY;
        private double avgBrightness;
        private double contrast;
        private double[] colorChannels;
        private Point lightingDirection;
    }
    
    @Data
    @AllArgsConstructor
    public static class LightingInconsistency {
        private String type;
        private LightingRegion region1;
        private LightingRegion region2;
        private double severity;
        private String description;
    }
    
    @Data
    @AllArgsConstructor
    private static class LightingMetrics {
        private int inconsistencies;
        private double confidenceScore;
        private String analysisSummary;
    }
}
