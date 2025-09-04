package com.itproject.traditional.OriginalFuncsReferencedByWU;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class LightingAnalysisTool extends JFrame {

    private ImagePanel imagePanel;

    public LightingAnalysisTool() {
        setTitle("光照分析概念工具 (Lighting Analysis Conceptual Tool)");
        setSize(1000, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 创建菜单栏
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem openItem = new JMenuItem("打开图片...");
        JMenuItem clearItem = new JMenuItem("清除线条");

        fileMenu.add(openItem);
        fileMenu.add(clearItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // 创建图片显示面板
        imagePanel = new ImagePanel();
        add(new JScrollPane(imagePanel)); // 添加滚动条以便查看大图

        // 添加事件监听器
        openItem.addActionListener(e -> imagePanel.loadImage());
        clearItem.addActionListener(e -> imagePanel.clearLines());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LightingAnalysisTool tool = new LightingAnalysisTool();
            tool.setVisible(true);
        });
    }
}

class ImagePanel extends JPanel {
    private BufferedImage image;
    private List<Point> points = new ArrayList<>(); // 存储用户点击的点

    public ImagePanel() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (image == null) return;
                points.add(e.getPoint());
                repaint(); // 每次点击后重绘面板
            }
        });
    }

    public void loadImage() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                image = ImageIO.read(fileChooser.getSelectedFile());
                points.clear(); // 加载新图片时清除旧的点和线
                setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
                revalidate(); // 更新滚动视图
                repaint();
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "无法加载图片", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void clearLines() {
        points.clear();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, this);
        }

        // 设置线条颜色和粗细
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.YELLOW);
        g2d.setStroke(new BasicStroke(2));

        // 每两个点画一条延伸的线
        for (int i = 0; i + 1 < points.size(); i += 2) {
            Point p1 = points.get(i);   // 物体点
            Point p2 = points.get(i + 1); // 阴影点

            // 计算向量
            int dx = p1.x - p2.x;
            int dy = p1.y - p2.y;

            // 计算并绘制延伸线
            // 我们将线延伸到画面边界外，以模拟光线
            int x3 = p1.x + dx * 20;
            int y3 = p1.y + dy * 20;

            g2d.drawLine(p2.x, p2.y, x3, y3);

            // 在点击的点上画小圆圈，方便观察
            g.setColor(Color.RED);
            g.fillOval(p1.x - 4, p1.y - 4, 8, 8);
            g.setColor(Color.CYAN);
            g.fillOval(p2.x - 4, p2.y - 4, 8, 8);
        }
    }
}