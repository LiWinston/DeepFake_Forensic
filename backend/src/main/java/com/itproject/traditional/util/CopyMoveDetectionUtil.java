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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Copy-Move Detection implementation
 * Detects duplicated regions that may indicate tampering
 */
@Slf4j
@Component
public class CopyMoveDetectionUtil {
    
    /**
     * Perform copy-move detection on an image
     * 
     * @param imageStream Input image stream
     * @param blockSize Size of blocks to analyze (default 8)
     * @param similarityThreshold Threshold for block similarity (default 10.0)
     * @return Copy-move detection result
     */
    public CopyMoveResult performCopyMoveDetection(InputStream imageStream, int blockSize, double similarityThreshold) {
        try {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new IllegalArgumentException("Unable to read image from input stream");
            }
            
            // Detect copy-move regions
            List<SuspiciousBlockPair> suspiciousPairs = detectCopyMoveRegions(image, blockSize, similarityThreshold);
            
            // Create visualization
            BufferedImage resultImage = visualizeCopyMoveDetection(image, suspiciousPairs, blockSize);
            
            // Analyze results
            CopyMoveMetrics metrics = analyzeCopyMoveResults(suspiciousPairs, image.getWidth(), image.getHeight(), blockSize);
            
            // Convert result image to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resultImage, "PNG", baos);
            byte[] resultImageData = baos.toByteArray();
            
            return new CopyMoveResult(
                resultImage,
                resultImageData,
                suspiciousPairs.size(),
                metrics.getConfidenceScore(),
                metrics.getAnalysisSummary(),
                suspiciousPairs
            );
            
        } catch (Exception e) {
            log.error("Error performing copy-move detection", e);
            throw new RuntimeException("Failed to perform copy-move detection", e);
        }
    }
    
    /**
     * Detect copy-move regions in the image
     */
    private List<SuspiciousBlockPair> detectCopyMoveRegions(BufferedImage image, int blockSize, double threshold) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Extract features from overlapping blocks
        Map<String, List<BlockInfo>> featureMap = new HashMap<>();
        
        for (int y = 0; y <= height - blockSize; y += blockSize / 2) { // Overlapping blocks
            for (int x = 0; x <= width - blockSize; x += blockSize / 2) {
                double[] features = extractBlockFeatures(image, x, y, blockSize);
                String featureKey = quantizeFeatures(features);
                
                featureMap.computeIfAbsent(featureKey, k -> new ArrayList<>())
                         .add(new BlockInfo(x, y, features));
            }
        }
        
        // Find similar blocks
        List<SuspiciousBlockPair> suspiciousPairs = new ArrayList<>();
        
        for (List<BlockInfo> blocks : featureMap.values()) {
            if (blocks.size() > 1) {
                // Check all pairs in this feature group
                for (int i = 0; i < blocks.size(); i++) {
                    for (int j = i + 1; j < blocks.size(); j++) {
                        BlockInfo block1 = blocks.get(i);
                        BlockInfo block2 = blocks.get(j);
                        
                        // Calculate distance between blocks
                        double distance = calculateEuclideanDistance(block1.getFeatures(), block2.getFeatures());
                        
                        // Check if blocks are similar but not too close spatially
                        if (distance <= threshold && !areBlocksTooClose(block1, block2, blockSize)) {
                            suspiciousPairs.add(new SuspiciousBlockPair(block1, block2, distance));
                        }
                    }
                }
            }
        }
        
        return filterOverlappingPairs(suspiciousPairs);
    }
    
    /**
     * Extract features from a block (simplified DCT-based approach)
     */
    private double[] extractBlockFeatures(BufferedImage image, int startX, int startY, int blockSize) {
        double[] features = new double[blockSize * blockSize];
        int index = 0;
        
        for (int y = startY; y < startY + blockSize && y < image.getHeight(); y++) {
            for (int x = startX; x < startX + blockSize && x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                // Use luminance as feature
                double luminance = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
                features[index++] = luminance;
            }
        }
        
        // Apply simple DCT approximation (use only low-frequency components)
        return applySimpleDCT(features, blockSize);
    }
    
    /**
     * Apply simplified DCT to extract low-frequency features
     */
    private double[] applySimpleDCT(double[] block, int blockSize) {
        // Use only the first few DCT coefficients as features
        int featureCount = Math.min(16, blockSize * blockSize / 4);
        double[] features = new double[featureCount];
        
        for (int i = 0; i < featureCount; i++) {
            double sum = 0;
            for (int j = 0; j < blockSize * blockSize; j++) {
                sum += block[j] * Math.cos(Math.PI * i * j / (blockSize * blockSize));
            }
            features[i] = sum;
        }
        
        return features;
    }
    
    /**
     * Quantize features for quick lookup
     */
    private String quantizeFeatures(double[] features) {
        StringBuilder key = new StringBuilder();
        for (double feature : features) {
            key.append((int)(feature / 10.0)).append(",");
        }
        return key.toString();
    }
    
    /**
     * Calculate Euclidean distance between feature vectors
     */
    private double calculateEuclideanDistance(double[] features1, double[] features2) {
        double sum = 0;
        for (int i = 0; i < Math.min(features1.length, features2.length); i++) {
            double diff = features1[i] - features2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
    
    /**
     * Check if two blocks are too close spatially (likely not copy-move)
     */
    private boolean areBlocksTooClose(BlockInfo block1, BlockInfo block2, int blockSize) {
        int dx = Math.abs(block1.getX() - block2.getX());
        int dy = Math.abs(block1.getY() - block2.getY());
        
        // Minimum distance should be at least 2 block sizes
        return dx < blockSize * 2 && dy < blockSize * 2;
    }
    
    /**
     * Filter overlapping pairs to reduce false positives
     */
    private List<SuspiciousBlockPair> filterOverlappingPairs(List<SuspiciousBlockPair> pairs) {
        // Sort by distance (best matches first)
        pairs.sort((a, b) -> Double.compare(a.getDistance(), b.getDistance()));
        
        List<SuspiciousBlockPair> filtered = new ArrayList<>();
        
        for (SuspiciousBlockPair pair : pairs) {
            boolean overlapsWithExisting = false;
            
            for (SuspiciousBlockPair existing : filtered) {
                if (blocksOverlap(pair.getBlock1(), existing.getBlock1()) ||
                    blocksOverlap(pair.getBlock1(), existing.getBlock2()) ||
                    blocksOverlap(pair.getBlock2(), existing.getBlock1()) ||
                    blocksOverlap(pair.getBlock2(), existing.getBlock2())) {
                    overlapsWithExisting = true;
                    break;
                }
            }
            
            if (!overlapsWithExisting) {
                filtered.add(pair);
            }
        }
        
        return filtered;
    }
    
    /**
     * Check if two blocks overlap significantly
     */
    private boolean blocksOverlap(BlockInfo block1, BlockInfo block2) {
        int overlapX = Math.max(0, Math.min(block1.getX() + 8, block2.getX() + 8) - Math.max(block1.getX(), block2.getX()));
        int overlapY = Math.max(0, Math.min(block1.getY() + 8, block2.getY() + 8) - Math.max(block1.getY(), block2.getY()));
        
        // Blocks overlap if overlap area is more than 50% of block size
        return (overlapX * overlapY) > (8 * 8 * 0.5);
    }
    
    /**
     * Visualize copy-move detection results
     */
    private BufferedImage visualizeCopyMoveDetection(BufferedImage original, List<SuspiciousBlockPair> pairs, int blockSize) {
        BufferedImage result = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        
        // Draw original image
        g2d.drawImage(original, 0, 0, null);
        
        // Draw suspicious regions
        g2d.setStroke(new BasicStroke(2.0f));
        
        int colorIndex = 0;
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN};
        
        for (SuspiciousBlockPair pair : pairs) {
            Color color = colors[colorIndex % colors.length];
            g2d.setColor(color);
            
            // Draw rectangles around suspicious blocks
            g2d.drawRect(pair.getBlock1().getX(), pair.getBlock1().getY(), blockSize, blockSize);
            g2d.drawRect(pair.getBlock2().getX(), pair.getBlock2().getY(), blockSize, blockSize);
            
            // Draw line connecting the blocks
            g2d.drawLine(
                pair.getBlock1().getX() + blockSize / 2, pair.getBlock1().getY() + blockSize / 2,
                pair.getBlock2().getX() + blockSize / 2, pair.getBlock2().getY() + blockSize / 2
            );
            
            colorIndex++;
        }
        
        g2d.dispose();
        return result;
    }
    
    /**
     * Analyze copy-move detection results
     */
    private CopyMoveMetrics analyzeCopyMoveResults(List<SuspiciousBlockPair> pairs, int width, int height, int blockSize) {
        int suspiciousBlocks = pairs.size() * 2; // Each pair has 2 blocks
        int totalBlocks = (width / blockSize) * (height / blockSize);
        
        double avgDistance = pairs.stream()
                                 .mapToDouble(SuspiciousBlockPair::getDistance)
                                 .average()
                                 .orElse(0.0);
        
        // Calculate confidence based on number of pairs and their similarity
        double confidenceScore = Math.min(100, pairs.size() * 20); // Scale factor
        
        String analysisSummary = generateCopyMoveAnalysisSummary(pairs.size(), suspiciousBlocks, avgDistance, confidenceScore);
        
        return new CopyMoveMetrics(suspiciousBlocks, confidenceScore, analysisSummary);
    }
    
    /**
     * Generate human-readable copy-move analysis summary
     */
    private String generateCopyMoveAnalysisSummary(int pairCount, int suspiciousBlocks, double avgDistance, double confidenceScore) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Copy-Move Detection Results:\n");
        summary.append(String.format("- Suspicious block pairs found: %d\n", pairCount));
        summary.append(String.format("- Total suspicious blocks: %d\n", suspiciousBlocks));
        summary.append(String.format("- Average similarity distance: %.2f\n", avgDistance));
        summary.append(String.format("- Confidence score: %.2f/100\n", confidenceScore));
        
        if (pairCount == 0) {
            summary.append("- Assessment: No copy-move regions detected");
        } else if (pairCount < 3) {
            summary.append("- Assessment: Few potential copy-move regions detected, may be false positives");
        } else if (pairCount < 10) {
            summary.append("- Assessment: Moderate evidence of copy-move manipulation");
        } else {
            summary.append("- Assessment: Strong evidence of copy-move manipulation detected");
        }
        
        return summary.toString();
    }
    
    @Data
    @AllArgsConstructor
    public static class CopyMoveResult {
        private BufferedImage resultImage;
        private byte[] resultImageData;
        private int suspiciousBlocks;
        private double confidenceScore;
        private String analysisSummary;
        private List<SuspiciousBlockPair> suspiciousPairs;
    }
    
    @Data
    @AllArgsConstructor
    public static class SuspiciousBlockPair {
        private BlockInfo block1;
        private BlockInfo block2;
        private double distance;
    }
    
    @Data
    @AllArgsConstructor
    public static class BlockInfo {
        private int x;
        private int y;
        private double[] features;
    }
    
    @Data
    @AllArgsConstructor
    private static class CopyMoveMetrics {
        private int suspiciousBlocks;
        private double confidenceScore;
        private String analysisSummary;
    }
}
