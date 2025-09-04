import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.awt.Color;

public class CFAAnalysis {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("使用方法: java CFAAnalysis <输入图片路径> <输出热力图路径>");
            System.out.println("例如: java CFAAnalysis original.png cfa_heatmap.png");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        try {
            System.out.println("正在加载图片: " + inputPath);
            BufferedImage image = ImageIO.read(new File(inputPath));

            System.out.println("正在进行CFA模式分析...");
            BufferedImage heatmap = createCfaHeatmap(image);

            System.out.println("正在保存CFA热力图到: " + outputPath);
            ImageIO.write(heatmap, "png", new File(outputPath));

            System.out.println("CFA分析完成！");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建一个简化的CFA不一致性热力图
     * 这个版本通过计算每个像素与其邻居在绿色通道上的二阶导数（拉普拉斯算子）来工作。
     * 篡改区域的插值算法不同，会导致这个值的统计特性发生变化。
     * @param image 原始图片
     * @return 热力图
     */
    private static BufferedImage createCfaHeatmap(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage heatmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 用于存储每个像素的“不一致性”得分
        double[][] scores = new double[width][height];
        double maxScore = 0;

        // 拉普拉斯算子核
        int[][] kernel = {
                {0, -1, 0},
                {-1, 4, -1},
                {0, -1, 0}
        };

        // 遍历图像的中心部分（忽略边缘以简化计算）
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double score = 0;
                // 应用拉普拉斯算子于绿色通道
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        Color c = new Color(image.getRGB(x + j - 1, y + i - 1));
                        score += c.getGreen() * kernel[i][j];
                    }
                }
                scores[x][y] = Math.abs(score);
                if (scores[x][y] > maxScore) {
                    maxScore = scores[x][y];
                }
            }
        }

        // 将得分归一化并映射到灰度图
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (maxScore > 0) {
                    int gray = (int) ( (scores[x][y] / maxScore) * 255 );
                    heatmap.setRGB(x, y, new Color(gray, gray, gray).getRGB());
                }
            }
        }

        return heatmap;
    }
}