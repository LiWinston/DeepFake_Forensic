import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.awt.Color;

public class ErrorLevelAnalysis {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("使用方法: java ErrorLevelAnalysis <输入图片路径> <输出图片路径> <JPEG质量百分比> <差异缩放倍数>");
            System.out.println("例如: java ErrorLevelAnalysis original.jpg ela_result.png 95 20");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        int jpegQuality = Integer.parseInt(args[2]);
        int scale = Integer.parseInt(args[3]);

        try {
            System.out.println("正在加载原始图片: " + inputPath);
            File originalFile = new File(inputPath);
            BufferedImage originalImage = ImageIO.read(originalFile);

            // 步骤 1: 将原始图片以指定的JPEG质量重新保存为一个临时文件
            File tempFile = File.createTempFile("ela_temp", ".jpg");
            System.out.println("正在以 " + jpegQuality + "% 的质量重新压缩图片到临时文件...");
            saveAsJPEG(originalImage, tempFile, jpegQuality / 100.0f);

            // 步骤 2: 将重新保存的图片加载回来
            System.out.println("正在加载重新压缩后的图片...");
            BufferedImage resavedImage = ImageIO.read(tempFile);

            // 步骤 3: 计算两张图片之间的差异，并生成ELA结果图
            System.out.println("正在计算像素差异...");
            BufferedImage elaImage = createElaImage(originalImage, resavedImage, scale);

            // 步骤 4: 保存最终的ELA结果图 (建议保存为PNG以避免二次压缩)
            System.out.println("正在保存ELA结果到: " + outputPath);
            ImageIO.write(elaImage, "png", new File(outputPath));

            // 清理临时文件
            tempFile.delete();

            System.out.println("ELA分析完成！");

        } catch (IOException e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 以指定的JPEG质量保存BufferedImage
     * @param image       要保存的图片
     * @param file        保存到的文件
     * @param quality     压缩质量，从 0.0f (最低) 到 1.0f (最高)
     * @throws IOException
     */
    private static void saveAsJPEG(BufferedImage image, File file, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(new FileOutputStream(file))) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
        }
    }

    /**
     * 创建ELA差异图
     * @param originalImage 原始图片
     * @param resavedImage  重保存后的图片
     * @param scale         差异放大倍数
     * @return ELA结果图
     */
    private static BufferedImage createElaImage(BufferedImage originalImage, BufferedImage resavedImage, int scale) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        BufferedImage elaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取两张图片在同一位置的像素颜色
                Color originalColor = new Color(originalImage.getRGB(x, y));
                Color resavedColor = new Color(resavedImage.getRGB(x, y));

                // 计算红、绿、蓝三个通道的差异绝对值
                int diffRed = Math.abs(originalColor.getRed() - resavedColor.getRed());
                int diffGreen = Math.abs(originalColor.getGreen() - resavedColor.getGreen());
                int diffBlue = Math.abs(originalColor.getBlue() - resavedColor.getBlue());

                // 将差异放大，使其肉眼可见，并确保结果不超过255
                int scaledRed = Math.min(255, diffRed * scale);
                int scaledGreen = Math.min(255, diffGreen * scale);
                int scaledBlue = Math.min(255, diffBlue * scale);

                // 在ELA结果图上设置新的像素颜色
                Color newColor = new Color(scaledRed, scaledGreen, scaledBlue);
                elaImage.setRGB(x, y, newColor.getRGB());
            }
        }
        return elaImage;
    }
}