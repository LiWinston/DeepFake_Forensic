package com.itproject.traditional.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Noise residual analysis utility.
 * Extract image noise via median filtering and enhanced residual to reveal potential tampering.
 */
@Slf4j
@Component
public class NoiseAnalysisUtil {

    /**
     * Perform Noise Residual Analysis
     *
     * @param imageStream input image stream
     * @param kernelSize  odd kernel size for median filter (e.g. 9)
     * @param scaleFactor residual amplification factor (e.g. 10)
     * @return noise analysis result
     */
    public NoiseResult performNoiseAnalysis(InputStream imageStream, int kernelSize, int scaleFactor) {
        long start = System.currentTimeMillis();
        try {
            BufferedImage original = ImageIO.read(imageStream);
            if (original == null) {
                throw new IllegalArgumentException("Unable to read image from input stream");
            }

            // Step 1: median filter to get smoothed image
            BufferedImage filtered = applyMedianFilter(original, kernelSize);

            // Step 2: residual and enhancement centered at 128 gray
            BufferedImage residual = getEnhancedResidual(original, filtered, scaleFactor);

            // Step 3: metrics on residual map
            NoiseMetrics metrics = analyzeResidual(residual);

            // Encode residual image as PNG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(residual, "PNG", baos);
            byte[] data = baos.toByteArray();

            log.info("Noise analysis completed in {}ms", System.currentTimeMillis() - start);
            return new NoiseResult(residual, data, metrics.getSuspiciousRegions(), metrics.getConfidenceScore(), metrics.getAnalysisSummary());

        } catch (Exception e) {
            log.error("Error performing noise analysis", e);
            throw new RuntimeException("Failed to perform noise analysis", e);
        }
    }

    // --- Core image ops ---

    private BufferedImage applyMedianFilter(BufferedImage original, int kernelSize) {
        if (kernelSize % 2 == 0) throw new IllegalArgumentException("Kernel size must be odd");
        int w = original.getWidth();
        int h = original.getHeight();
        int r = kernelSize / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        int area = kernelSize * kernelSize;
        int[] rs = new int[area];
        int[] gs = new int[area];
        int[] bs = new int[area];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = 0;
                for (int ky = -r; ky <= r; ky++) {
                    int py = Math.max(0, Math.min(h - 1, y + ky));
                    for (int kx = -r; kx <= r; kx++) {
                        int px = Math.max(0, Math.min(w - 1, x + kx));
                        Color color = new Color(original.getRGB(px, py));
                        rs[c] = color.getRed();
                        gs[c] = color.getGreen();
                        bs[c] = color.getBlue();
                        c++;
                    }
                }
                java.util.Arrays.sort(rs);
                java.util.Arrays.sort(gs);
                java.util.Arrays.sort(bs);
                int mr = rs[area / 2];
                int mg = gs[area / 2];
                int mb = bs[area / 2];
                out.setRGB(x, y, new Color(mr, mg, mb).getRGB());
            }
        }
        return out;
    }

    private BufferedImage getEnhancedResidual(BufferedImage a, BufferedImage b, int scaleFactor) {
        int w = a.getWidth();
        int h = a.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color ca = new Color(a.getRGB(x, y));
                Color cb = new Color(b.getRGB(x, y));
                int dr = 128 + (ca.getRed() - cb.getRed()) * scaleFactor;
                int dg = 128 + (ca.getGreen() - cb.getGreen()) * scaleFactor;
                int db = 128 + (ca.getBlue() - cb.getBlue()) * scaleFactor;
                dr = Math.max(0, Math.min(255, dr));
                dg = Math.max(0, Math.min(255, dg));
                db = Math.max(0, Math.min(255, db));
                out.setRGB(x, y, new Color(dr, dg, db).getRGB());
            }
        }
        return out;
    }

    // --- Metrics ---

    private NoiseMetrics analyzeResidual(BufferedImage residual) {
        int w = residual.getWidth();
        int h = residual.getHeight();

        int suspicious = 0;
        int total = w * h;

        // Pixels far away from neutral gray (128) in luminance are suspicious
        int threshold = 25; // deviation threshold from 128

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = new Color(residual.getRGB(x, y));
                double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
                double dev = Math.abs(lum - 128);
                if (dev > threshold) suspicious++;
            }
        }

        double ratio = total > 0 ? (double) suspicious / total : 0.0;
        // Confidence policy: stronger residual structure -> higher suspicion
        double confidence = Math.min(100.0, ratio * 200.0);

        int regions = countSuspiciousRegions(residual, threshold);
        String summary = generateSummary(suspicious, total, regions, confidence);
        return new NoiseMetrics(regions, confidence, summary);
    }

    private int countSuspiciousRegions(BufferedImage img, int threshold) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] visited = new boolean[w][h];
        int regions = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!visited[x][y] && isSuspicious(img, x, y, threshold)) {
                    floodFill(img, visited, x, y, threshold);
                    regions++;
                }
            }
        }
        return regions;
    }

    private boolean isSuspicious(BufferedImage img, int x, int y, int threshold) {
        Color c = new Color(img.getRGB(x, y));
        double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return Math.abs(lum - 128) > threshold;
    }

    private void floodFill(BufferedImage img, boolean[][] visited, int sx, int sy, int threshold) {
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.offer(new int[]{sx, sy});
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
        int processed = 0;
        int sizeLimit = Math.max(200000, (img.getWidth() * img.getHeight()) / 5);
        while (!q.isEmpty() && processed < sizeLimit) {
            int[] p = q.poll();
            int x = p[0], y = p[1];
            if (x < 0 || x >= img.getWidth() || y < 0 || y >= img.getHeight()) continue;
            if (visited[x][y] || !isSuspicious(img, x, y, threshold)) continue;
            visited[x][y] = true;
            processed++;
            for (int i = 0; i < 8; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < img.getWidth() && ny >= 0 && ny < img.getHeight() && !visited[nx][ny]) {
                    if (isSuspicious(img, nx, ny, threshold)) q.offer(new int[]{nx, ny});
                }
            }
        }
        q.clear();
    }

    private String generateSummary(int suspiciousPixels, int totalPixels, int regions, double confidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("Noise Residual Analysis Results:\n");
        sb.append(String.format("- Suspicious pixels: %d / %d (%.2f%%)\n", suspiciousPixels, totalPixels, totalPixels > 0 ? (suspiciousPixels * 100.0 / totalPixels) : 0));
        sb.append(String.format("- Suspicious regions: %d\n", regions));
        sb.append(String.format("- Confidence score: %.1f/100\n", confidence));
        if (confidence < 20) {
            sb.append("- Assessment: Residual appears uniform; image likely authentic");
        } else if (confidence < 50) {
            sb.append("- Assessment: Mild residual structures; possible minor edits");
        } else if (confidence < 80) {
            sb.append("- Assessment: Pronounced residual structures; likely edited");
        } else {
            sb.append("- Assessment: Strong structured residuals; high probability of manipulation");
        }
        return sb.toString();
    }

    // --- DTOs ---
    @Data
    @AllArgsConstructor
    public static class NoiseResult {
        private BufferedImage residualImage;
        private byte[] residualImageData;
        private int suspiciousRegions;
        private double confidenceScore;
        private String analysisSummary;
    }

    @Data
    @AllArgsConstructor
    private static class NoiseMetrics {
        private int suspiciousRegions;
        private double confidenceScore;
        private String analysisSummary;
    }
}
