package com.aizxue.plist;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageCropTest extends JFrame {
    private BufferedImage originalImage;
    private BufferedImage croppedImage;
    private BufferedImage rotatedImage;
    
    public ImageCropTest() {
        setTitle("图像裁剪测试 - attack.png");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        try {
            // 加载原始图像
            loadImage();
            
            // 执行裁剪
            cropImage();
            
            // 执行旋转
            rotateImage();
            
            // 创建显示面板
            JPanel displayPanel = createDisplayPanel();
            add(displayPanel, BorderLayout.CENTER);
            
            // 添加信息标签
            JLabel infoLabel = new JLabel("<html>" +
                "<b>裁剪和旋转测试:</b><br>" +
                "原图尺寸: " + originalImage.getWidth() + "x" + originalImage.getHeight() + "<br>" +
                "裁剪区域: (0,252) 到 (144,348)<br>" +
                "裁剪尺寸: 144x96<br>" +
                "旋转角度: -90度" +
                "</html>");
            infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(infoLabel, BorderLayout.SOUTH);
            
            pack();
            setLocationRelativeTo(null);
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "加载图像失败: " + e.getMessage());
        }
    }
    
    private void loadImage() throws IOException {
        // 尝试加载attack.png，如果不存在则加载attack.dds
        File pngFile = new File("attack.png");
        if (pngFile.exists()) {
            originalImage = ImageIO.read(pngFile);
            System.out.println("已加载 attack.png");
        } else {
            // 如果没有PNG文件，尝试加载DDS文件
            File ddsFile = new File("attack.dds");
            if (ddsFile.exists()) {
                try {
                    originalImage = DDSImageLoader.loadDDS(ddsFile);
                    System.out.println("已加载 attack.dds");
                } catch (Exception e) {
                    throw new IOException("加载DDS文件失败: " + e.getMessage(), e);
                }
            } else {
                throw new IOException("找不到 attack.png 或 attack.dds 文件");
            }
        }
        
        System.out.println("原图尺寸: " + originalImage.getWidth() + "x" + originalImage.getHeight());
    }
    
    private void cropImage() {
        // 裁剪指定区域: (0,252) 到 (144,348)
        int cropX = 0;
        int cropY = 252;
        int cropWidth = 144;
        int cropHeight = 96;
        
        // 边界检查
        int maxX = Math.min(cropX + cropWidth, originalImage.getWidth());
        int maxY = Math.min(cropY + cropHeight, originalImage.getHeight());
        int actualWidth = maxX - cropX;
        int actualHeight = maxY - cropY;
        
        System.out.println("裁剪区域: (" + cropX + "," + cropY + ") 尺寸: " + actualWidth + "x" + actualHeight);
        
        // 执行裁剪
        croppedImage = originalImage.getSubimage(cropX, cropY, actualWidth, actualHeight);
        
        // 创建独立副本
        BufferedImage copy = new BufferedImage(croppedImage.getWidth(), croppedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(croppedImage, 0, 0, null);
        g2d.dispose();
        croppedImage = copy;
    }
    
    private void rotateImage() {
        if (croppedImage == null) return;
        
        // 旋转-90度
        int width = croppedImage.getWidth();
        int height = croppedImage.getHeight();
        
        // 创建旋转后的图像（宽高交换）
        rotatedImage = new BufferedImage(height, width, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotatedImage.createGraphics();
        
        // 设置旋转变换
        g2d.translate(height / 2.0, width / 2.0);
        g2d.rotate(Math.toRadians(-90));
        g2d.translate(-width / 2.0, -height / 2.0);
        
        // 绘制图像
        g2d.drawImage(croppedImage, 0, 0, null);
        g2d.dispose();
        
        System.out.println("旋转后尺寸: " + rotatedImage.getWidth() + "x" + rotatedImage.getHeight());
    }
    
    private JPanel createDisplayPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 原图显示（缩放）
        JPanel originalPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (originalImage != null) {
                    // 计算缩放比例以适应面板
                    double scaleX = (double) getWidth() / originalImage.getWidth();
                    double scaleY = (double) getHeight() / originalImage.getHeight();
                    double scale = Math.min(scaleX, scaleY);
                    
                    int scaledWidth = (int) (originalImage.getWidth() * scale);
                    int scaledHeight = (int) (originalImage.getHeight() * scale);
                    int x = (getWidth() - scaledWidth) / 2;
                    int y = (getHeight() - scaledHeight) / 2;
                    
                    g.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
                    
                    // 绘制裁剪区域标记
                    g.setColor(Color.RED);
                    ((Graphics2D) g).setStroke(new BasicStroke(2));
                    int markX = x + (int) (0 * scale);
                    int markY = y + (int) (252 * scale);
                    int markWidth = (int) (144 * scale);
                    int markHeight = (int) (96 * scale);
                    g.drawRect(markX, markY, markWidth, markHeight);
                }
            }
        };
        originalPanel.setPreferredSize(new Dimension(250, 250));
        originalPanel.setBorder(BorderFactory.createTitledBorder("原图 (红框为裁剪区域)"));
        
        // 裁剪图显示
        JPanel croppedPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (croppedImage != null) {
                    // 居中显示裁剪后的图像
                    int x = (getWidth() - croppedImage.getWidth()) / 2;
                    int y = (getHeight() - croppedImage.getHeight()) / 2;
                    g.drawImage(croppedImage, x, y, null);
                }
            }
        };
        croppedPanel.setPreferredSize(new Dimension(200, 200));
        croppedPanel.setBorder(BorderFactory.createTitledBorder("裁剪结果 (144x96)"));
        
        // 旋转图显示
        JPanel rotatedPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (rotatedImage != null) {
                    // 居中显示旋转后的图像
                    int x = (getWidth() - rotatedImage.getWidth()) / 2;
                    int y = (getHeight() - rotatedImage.getHeight()) / 2;
                    g.drawImage(rotatedImage, x, y, null);
                }
            }
        };
        rotatedPanel.setPreferredSize(new Dimension(200, 200));
        rotatedPanel.setBorder(BorderFactory.createTitledBorder("旋转-90度 (96x144)"));
        
        panel.add(originalPanel);
        panel.add(croppedPanel);
        panel.add(rotatedPanel);
        
        return panel;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 使用默认外观
            
            new ImageCropTest().setVisible(true);
        });
    }
}