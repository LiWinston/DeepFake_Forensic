package org.example;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * NoiseAnalysis 类用于对图像进行噪声残差分析，以检测潜在的篡改。
 *
 * 算法原理:
 * 1. 使用中值滤波器对原始图像进行平滑处理，以去除图像的主要内容，保留其底层的噪声模式。
 *    中值滤波器相比高斯模糊等其他滤波器，能更好地保留边缘信息，使得噪声提取更纯粹。
 * 2. 从原始图像中减去经过滤波的平滑图像，得到“噪声残差图”。
 * 3. 对残差图进行增强处理（例如增加对比度），使其更容易被人眼观察。
 *
 * 如何解读结果:
 * - 对于一张未经修改的原始照片，其噪声残差图通常会呈现出一片均匀、随机的灰色噪点。
 * - 如果图像的某个区域被篡改过（例如，从别处复制粘贴而来），该区域的噪声模式会与
 *   周围区域不同，在最终的噪声残差图上会显示为一块结构异常、亮度或颜色明显不同的区域。
 */
public class NoiseAnalysis {

    /**
     * 对图像应用中值滤波器。
     * @param originalImage 原始 BufferedImage 对象。
     * @param kernelSize 滤波器核的大小（例如 3 表示 3x3 的核），必须是奇数。
     * @return 经过中值滤波处理后的新 BufferedImage 对象。
     */
    private BufferedImage applyMedianFilter(BufferedImage originalImage, int kernelSize) {
        if (kernelSize % 2 == 0) {
            throw new IllegalArgumentException("Kernel size must be an odd number.");
        }
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        int radius = kernelSize / 2;

        BufferedImage filteredImage = new BufferedImage(width, height, originalImage.getType());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] rValues = new int[kernelSize * kernelSize];
                int[] gValues = new int[kernelSize * kernelSize];
                int[] bValues = new int[kernelSize * kernelSize];
                int count = 0;

                // 遍历滤波器核覆盖的区域
                for (int ky = -radius; ky <= radius; ky++) {
                    for (int kx = -radius; kx <= radius; kx++) {
                        int pixelX = Math.max(0, Math.min(width - 1, x + kx));
                        int pixelY = Math.max(0, Math.min(height - 1, y + ky));

                        Color color = new Color(originalImage.getRGB(pixelX, pixelY));
                        rValues[count] = color.getRed();
                        gValues[count] = color.getGreen();
                        bValues[count] = color.getBlue();
                        count++;
                    }
                }

                // 对每个颜色通道的像素值进行排序并取中值
                Arrays.sort(rValues);
                Arrays.sort(gValues);
                Arrays.sort(bValues);

                int medianR = rValues[rValues.length / 2];
                int medianG = gValues[gValues.length / 2];
                int medianB = bValues[bValues.length / 2];

                Color newColor = new Color(medianR, medianG, medianB);
                filteredImage.setRGB(x, y, newColor.getRGB());
            }
        }
        return filteredImage;
    }

    /**
     * 计算两张图像的差异，并对结果进行增强以便于观察。
     * @param imageA 原始图像。
     * @param imageB 滤波后的图像。
     * @param scaleFactor 增强残差的比例因子。值越大，对比度越高。
     * @return 代表噪声残差的 BufferedImage 对象。
     */
    private BufferedImage getEnhancedResidual(BufferedImage imageA, BufferedImage imageB, int scaleFactor) {
        int width = imageA.getWidth();
        int height = imageA.getHeight();
        BufferedImage residualImage = new BufferedImage(width, height, imageA.getType());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color colorA = new Color(imageA.getRGB(x, y));
                Color colorB = new Color(imageB.getRGB(x, y));

                // 计算每个通道的差值
                int diffR = colorA.getRed() - colorB.getRed();
                int diffG = colorA.getGreen() - colorB.getGreen();
                int diffB = colorA.getBlue() - colorB.getBlue();

                // 增强并偏移，将差值映射到 0-255 范围
                // 128 作为基准（中性灰），正差值变亮，负差值变暗
                int enhancedR = 128 + diffR * scaleFactor;
                int enhancedG = 128 + diffG * scaleFactor;
                int enhancedB = 128 + diffB * scaleFactor;

                // 确保像素值在 0-255 范围内
                enhancedR = Math.max(0, Math.min(255, enhancedR));
                enhancedG = Math.max(0, Math.min(255, enhancedG));
                enhancedB = Math.max(0, Math.min(255, enhancedB));

                Color residualColor = new Color(enhancedR, enhancedG, enhancedB);
                residualImage.setRGB(x, y, residualColor.getRGB());
            }
        }
        return residualImage;
    }

    /**
     * 对指定的图像文件执行噪声分析。
     * @param sourceImage 原始图像文件。
     * @return 噪声分析结果图像。
     * @throws IOException 如果读取文件失败。
     */
    public BufferedImage analyze(File sourceImage) throws IOException {
        BufferedImage originalImage = ImageIO.read(sourceImage);

        System.out.println("Applying 3x3 Median Filter...");
        // 步骤 1: 应用中值滤波
        BufferedImage filteredImage = applyMedianFilter(originalImage, 9);

        System.out.println("Calculating and enhancing residual...");
        // 步骤 2 & 3: 计算并增强噪声残差
        // scaleFactor 可以根据需要调整，10 是一个比较常用的起始值
        BufferedImage noiseResidual = getEnhancedResidual(originalImage, filteredImage, 10);

        System.out.println("Analysis complete.");
        return noiseResidual;
    }


    /**
     * 主函数，用于从命令行运行噪声分析。
     * @param args 命令行参数。需要两个参数：输入文件路径和输出文件路径。
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java NoiseAnalysis <input_image_path> <output_image_path>");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        File inputFile = new File(inputPath);
        File outputFile = new File(outputPath);

        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found at " + inputPath);
            return;
        }

        try {
            NoiseAnalysis analyzer = new NoiseAnalysis();
            BufferedImage resultImage = analyzer.analyze(inputFile);

            // 获取输出文件格式（例如 "png"）
            String formatName = outputPath.substring(outputPath.lastIndexOf(".") + 1);
            ImageIO.write(resultImage, formatName, outputFile);

            System.out.println("Noise analysis result saved to: " + outputPath);

        } catch (IOException e) {
            System.err.println("An error occurred during image processing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}