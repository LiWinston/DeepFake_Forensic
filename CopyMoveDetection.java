import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.awt.Color;
import java.awt.Graphics2D;

public class CopyMoveDetection {

    private static final int BLOCK_SIZE = 8; // 将图片分成8x8的小块
    private static final double SIMILARITY_THRESHOLD = 10.0; // 判断两个块是否相似的阈值

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("使用方法: java CopyMoveDetection <输入图片路径> <输出结果路径>");
            System.out.println("例如: java CopyMoveDetection original.png cmfd_result.png");
            return;
        }

        try {
            System.out.println("正在加载图片: " + args[0]);
            BufferedImage image = ImageIO.read(new File(args[0]));

            System.out.println("正在进行复制-移动检测...");
            BufferedImage resultImage = detectCopyMove(image);

            System.out.println("正在保存检测结果到: " + args[1]);
            ImageIO.write(resultImage, "png", new File(args[1]));

            System.out.println("检测完成！");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage detectCopyMove(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // 步骤1: 将图片分割成重叠的小块，并计算每个块的“特征”
        Map<String, List<Point>> featureMap = new HashMap<>();
        for (int y = 0; y <= height - BLOCK_SIZE; y++) {
            for (int x = 0; x <= width - BLOCK_SIZE; x++) {
                double[] feature = getBlockFeature(image, x, y);
                // 为了快速查找，我们将特征向量四舍五入并转为字符串作为Key
                String featureKey = featureToString(feature);

                featureMap.putIfAbsent(featureKey, new ArrayList<>());
                featureMap.get(featureKey).add(new Point(x, y));
            }
        }

        // 步骤2: 寻找具有相同或相似特征的块
        List<Point> suspiciousPoints = new ArrayList<>();
        for (List<Point> points : featureMap.values()) {
            if (points.size() > 1) { // 如果多个块有相同的特征
                // 这是一个简化的处理，实际应用中还需要检查这些点是否相邻
                // 以排除自然纹理（如天空、墙壁）的干扰
                suspiciousPoints.addAll(points);
            }
        }

        // 步骤3: 在原图上高亮显示可疑区域
        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resultImage.createGraphics();
        g.drawImage(image, 0, 0, null);

        g.setColor(new Color(255, 0, 0, 128)); // 半透明红色
        for (Point p : suspiciousPoints) {
            g.fillRect(p.x, p.y, BLOCK_SIZE, BLOCK_SIZE);
        }
        g.dispose();

        return resultImage;
    }

    // 使用简化的平均颜色作为特征
    private static double[] getBlockFeature(BufferedImage image, int startX, int startY) {
        double redSum = 0, greenSum = 0, blueSum = 0;
        for (int y = 0; y < BLOCK_SIZE; y++) {
            for (int x = 0; x < BLOCK_SIZE; x++) {
                Color c = new Color(image.getRGB(startX + x, startY + y));
                redSum += c.getRed();
                greenSum += c.getGreen();
                blueSum += c.getBlue();
            }
        }
        int numPixels = BLOCK_SIZE * BLOCK_SIZE;
        return new double[]{redSum / numPixels, greenSum / numPixels, blueSum / numPixels};
    }

    // 将特征向量转为字符串以便放入HashMap
    private static String featureToString(double[] feature) {
        // 四舍五入以处理微小差异
        long r = Math.round(feature[0]);
        long g = Math.round(feature[1]);
        long b = Math.round(feature[2]);
        return r + "," + g + "," + b;
    }

    // 简单的Point类用于存储坐标
    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }
}