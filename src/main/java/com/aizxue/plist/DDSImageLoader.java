package com.aizxue.plist;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;

/**
 * DDS图像加载器
 * 支持基本的DDS格式读取并转换为BufferedImage
 */
public class DDSImageLoader {
    
    private static final int DDS_MAGIC = 0x20534444; // "DDS "
    private static final int DDSD_CAPS = 0x1;
    private static final int DDSD_HEIGHT = 0x2;
    private static final int DDSD_WIDTH = 0x4;
    private static final int DDSD_PITCH = 0x8;
    private static final int DDSD_PIXELFORMAT = 0x1000;
    private static final int DDSD_MIPMAPCOUNT = 0x20000;
    private static final int DDSD_LINEARSIZE = 0x80000;
    private static final int DDSD_DEPTH = 0x800000;
    
    private static final int DDPF_ALPHAPIXELS = 0x1;
    private static final int DDPF_ALPHA = 0x2;
    private static final int DDPF_FOURCC = 0x4;
    private static final int DDPF_RGB = 0x40;
    private static final int DDPF_YUV = 0x200;
    private static final int DDPF_LUMINANCE = 0x20000;
    
    public static class DDSHeader {
        public int size;
        public int flags;
        public int height;
        public int width;
        public int pitchOrLinearSize;
        public int depth;
        public int mipMapCount;
        public int[] reserved1 = new int[11];
        
        // Pixel format
        public int pfSize;
        public int pfFlags;
        public int pfFourCC;
        public int pfRGBBitCount;
        public int pfRBitMask;
        public int pfGBitMask;
        public int pfBBitMask;
        public int pfABitMask;
        
        public int caps;
        public int caps2;
        public int caps3;
        public int caps4;
        public int reserved2;
    }
    
    public static BufferedImage loadDDS(File ddsFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(ddsFile);
             DataInputStream dis = new DataInputStream(fis)) {
            
            // 读取魔数
            int magic = Integer.reverseBytes(dis.readInt());
            if (magic != DDS_MAGIC) {
                throw new IOException("不是有效的DDS文件");
            }
            
            // 读取头部
            DDSHeader header = readHeader(dis);
            
            // 根据像素格式读取图像数据
            return readImageData(dis, header);
        }
    }
    
    private static DDSHeader readHeader(DataInputStream dis) throws IOException {
        DDSHeader header = new DDSHeader();
        
        header.size = Integer.reverseBytes(dis.readInt());
        header.flags = Integer.reverseBytes(dis.readInt());
        header.height = Integer.reverseBytes(dis.readInt());
        header.width = Integer.reverseBytes(dis.readInt());
        header.pitchOrLinearSize = Integer.reverseBytes(dis.readInt());
        header.depth = Integer.reverseBytes(dis.readInt());
        header.mipMapCount = Integer.reverseBytes(dis.readInt());
        
        // 跳过保留字段
        for (int i = 0; i < 11; i++) {
            header.reserved1[i] = Integer.reverseBytes(dis.readInt());
        }
        
        // 像素格式
        header.pfSize = Integer.reverseBytes(dis.readInt());
        header.pfFlags = Integer.reverseBytes(dis.readInt());
        header.pfFourCC = dis.readInt(); // FourCC保持原始字节序
        header.pfRGBBitCount = Integer.reverseBytes(dis.readInt());
        header.pfRBitMask = Integer.reverseBytes(dis.readInt());
        header.pfGBitMask = Integer.reverseBytes(dis.readInt());
        header.pfBBitMask = Integer.reverseBytes(dis.readInt());
        header.pfABitMask = Integer.reverseBytes(dis.readInt());
        
        header.caps = Integer.reverseBytes(dis.readInt());
        header.caps2 = Integer.reverseBytes(dis.readInt());
        header.caps3 = Integer.reverseBytes(dis.readInt());
        header.caps4 = Integer.reverseBytes(dis.readInt());
        header.reserved2 = Integer.reverseBytes(dis.readInt());
        
        return header;
    }
    
    private static BufferedImage readImageData(DataInputStream dis, DDSHeader header) throws IOException {
        BufferedImage image = new BufferedImage(header.width, header.height, BufferedImage.TYPE_INT_ARGB);
        
        if ((header.pfFlags & DDPF_RGB) != 0) {
            // 未压缩的RGB格式
            readUncompressedRGB(dis, header, image);
        } else if ((header.pfFlags & DDPF_FOURCC) != 0) {
            // 压缩格式 (DXT1, DXT3, DXT5等)
            // 这里简化处理，实际项目中需要实现DXT解压缩
            System.out.println("警告: 压缩格式DDS暂不完全支持，尝试简单读取");
            readCompressedData(dis, header, image);
        } else {
            throw new IOException("不支持的DDS像素格式");
        }
        
        return image;
    }
    
    private static void readUncompressedRGB(DataInputStream dis, DDSHeader header, BufferedImage image) throws IOException {
        int bytesPerPixel = header.pfRGBBitCount / 8;
        byte[] pixelData = new byte[header.width * header.height * bytesPerPixel];
        dis.readFully(pixelData);
        
        ByteBuffer buffer = ByteBuffer.wrap(pixelData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int y = 0; y < header.height; y++) {
            for (int x = 0; x < header.width; x++) {
                int pixel = 0;
                
                if (bytesPerPixel == 4) {
                    // RGBA
                    int b = buffer.get() & 0xFF;
                    int g = buffer.get() & 0xFF;
                    int r = buffer.get() & 0xFF;
                    int a = buffer.get() & 0xFF;
                    pixel = (a << 24) | (r << 16) | (g << 8) | b;
                } else if (bytesPerPixel == 3) {
                    // RGB
                    int b = buffer.get() & 0xFF;
                    int g = buffer.get() & 0xFF;
                    int r = buffer.get() & 0xFF;
                    pixel = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                
                image.setRGB(x, y, pixel);
            }
        }
    }
    
    private static void readCompressedData(DataInputStream dis, DDSHeader header, BufferedImage image) throws IOException {
        // 简化的DXT1压缩数据处理
        // 注意：这是一个简化的实现，不是完整的DXT解压缩
        
        try {
            // 读取压缩数据
            int dataSize = dis.available();
            byte[] compressedData = new byte[dataSize];
            dis.readFully(compressedData);
            
            System.out.println("已处理压缩DDS数据，数据大小: " + dataSize + " 字节");
            System.out.println("图像尺寸: " + header.width + "x" + header.height);
            System.out.println("像素格式标志: 0x" + Integer.toHexString(header.pfFlags));
            System.out.println("FourCC: 0x" + Integer.toHexString(header.pfFourCC) + " (" + fourCCToString(header.pfFourCC) + ")");
            System.out.println("RGB位数: " + header.pfRGBBitCount);
            
            // 检查是否为压缩格式
            if ((header.pfFlags & DDPF_FOURCC) != 0) {
                System.out.println("检测到FourCC压缩格式");
                // 检查具体的压缩格式
                String fourCCStr = fourCCToString(header.pfFourCC);
                if (header.pfFourCC == 0x31545844 || "DXT1".equals(fourCCStr) || "1TXD".equals(fourCCStr)) { // DXT1
                    System.out.println("使用DXT1解压缩");
                    decompressDXT1(compressedData, image, header.width, header.height);
                } else if (header.pfFourCC == 0x33545844 || "DXT3".equals(fourCCStr) || "3TXD".equals(fourCCStr)) { // DXT3
                    System.out.println("使用DXT3解压缩");
                    decompressDXT3(compressedData, image, header.width, header.height);
                } else if (header.pfFourCC == 0x35545844 || "DXT5".equals(fourCCStr) || "5TXD".equals(fourCCStr)) { // DXT5
                    System.out.println("使用DXT5解压缩");
                    decompressDXT5(compressedData, image, header.width, header.height);
                } else if (header.pfFourCC == 0x31435442 || "BC4U".equals(fourCCStr) || "ATI1".equals(fourCCStr)) { // BC4/ATI1
                    System.out.println("使用BC4/ATI1解压缩");
                    decompressBC4(compressedData, image, header.width, header.height);
                } else if (header.pfFourCC == 0x32435442 || "BC5U".equals(fourCCStr) || "ATI2".equals(fourCCStr)) { // BC5/ATI2
                    System.out.println("使用BC5/ATI2解压缩");
                    decompressBC5(compressedData, image, header.width, header.height);
                } else if (header.pfFourCC == 0x36435442 || "BC6H".equals(fourCCStr)) { // BC6H
                    System.out.println("使用BC6H解压缩");
                    decompressBC6H(compressedData, image, header.width, header.height);
                } else if (header.pfFourCC == 0x37435442 || "BC7".equals(fourCCStr)) { // BC7
                    System.out.println("使用BC7解压缩");
                    decompressBC7(compressedData, image, header.width, header.height);
                } else if ("ETC1".equals(fourCCStr) || "ETC2".equals(fourCCStr)) { // ETC压缩
                    System.out.println("使用ETC解压缩");
                    decompressETC(compressedData, image, header.width, header.height);
                } else {
                    System.out.println("使用通用压缩数据处理，FourCC: " + fourCCToString(header.pfFourCC));
                    // 对于未知格式，尝试作为原始数据处理
                    decompressAsRawData(compressedData, image, header.width, header.height);
                }
            } else if ((header.pfFlags & DDPF_RGB) != 0) {
                System.out.println("检测到RGB格式，但在压缩数据处理中 - 可能是错误的路径");
                // 这种情况下应该使用未压缩处理，但我们在这里尝试处理
                decompressAsRawData(compressedData, image, header.width, header.height);
            } else {
                System.out.println("未知像素格式，使用备用图像");
                createAnimationTestPattern(image, header.width, header.height);
            }
            
        } catch (Exception e) {
            System.out.println("压缩数据处理失败，使用备用图像: " + e.getMessage());
            e.printStackTrace();
            createAnimationTestPattern(image, header.width, header.height);
        }
    }
    
    /**
     * 将FourCC转换为字符串
     */
    private static String fourCCToString(int fourCC) {
        char[] chars = new char[4];
        chars[0] = (char) (fourCC & 0xFF);
        chars[1] = (char) ((fourCC >> 8) & 0xFF);
        chars[2] = (char) ((fourCC >> 16) & 0xFF);
        chars[3] = (char) ((fourCC >> 24) & 0xFF);
        return new String(chars);
    }
    
    /**
     * 简化的DXT1解压缩
     */
    private static void decompressDXT1(byte[] data, BufferedImage image, int width, int height) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // DXT1每个4x4块使用8字节
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                if (buffer.remaining() < 8) break;
                
                // 读取颜色信息
                int color0 = buffer.getShort() & 0xFFFF;
                int color1 = buffer.getShort() & 0xFFFF;
                int indices = buffer.getInt();
                
                // 解析RGB565颜色
                int[] colors = new int[4];
                colors[0] = (255 << 24) | rgb565ToArgb(color0); // 不透明
                colors[1] = (255 << 24) | rgb565ToArgb(color1); // 不透明
                
                // 计算中间颜色
                if (color0 > color1) {
                    colors[2] = interpolateColor(colors[0], colors[1], 1, 2);
                    colors[3] = interpolateColor(colors[0], colors[1], 2, 1);
                } else {
                    colors[2] = interpolateColor(colors[0], colors[1], 1, 1);
                    colors[3] = 0x00000000; // 透明
                }
                
                // 填充4x4块
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int px = bx * 4 + x;
                        int py = by * 4 + y;
                        
                        if (px < width && py < height) {
                            // DXT1索引按行优先顺序存储，每个像素2位
                            int pixelIndex = y * 4 + x;
                            int index = (indices >> (pixelIndex * 2)) & 0x3;
                            image.setRGB(px, py, colors[index]);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 简化的DXT5解压缩
     */
    /**
     * DXT3解压缩
     * DXT3使用显式的4位alpha值，每个4x4块使用16字节（8字节alpha + 8字节颜色）
     */
    private static void decompressDXT3(byte[] data, BufferedImage image, int width, int height) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // DXT3每个4x4块使用16字节（8字节alpha + 8字节颜色）
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                if (buffer.remaining() < 16) break;
                
                // 读取alpha信息（8字节，每个像素4位）
                long alphaData = buffer.getLong();
                
                // 读取颜色信息（8字节）
                int color0 = buffer.getShort() & 0xFFFF;
                int color1 = buffer.getShort() & 0xFFFF;
                int colorIndices = buffer.getInt();
                
                // 解析RGB565颜色
                int[] colors = new int[4];
                colors[0] = (255 << 24) | rgb565ToArgb(color0); // 不透明
                colors[1] = (255 << 24) | rgb565ToArgb(color1); // 不透明
                colors[2] = interpolateColor(colors[0], colors[1], 2, 1);
                colors[3] = interpolateColor(colors[0], colors[1], 1, 2);
                
                // 填充4x4块
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int px = bx * 4 + x;
                        int py = by * 4 + y;
                        
                        if (px < width && py < height) {
                            int colorIndex = (colorIndices >> ((y * 4 + x) * 2)) & 0x3;
                            int baseColor = colors[colorIndex];
                            
                            // 提取4位alpha值
                            int alphaIndex = y * 4 + x;
                            int alpha4bit = (int)((alphaData >> (alphaIndex * 4)) & 0xF);
                            int alpha = (alpha4bit * 255) / 15; // 将4位alpha扩展到8位
                            
                            int pixel = (alpha << 24) | (baseColor & 0x00FFFFFF);
                            image.setRGB(px, py, pixel);
                        }
                    }
                }
            }
        }
    }
    
    private static void decompressDXT5(byte[] data, BufferedImage image, int width, int height) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // DXT5每个4x4块使用16字节（8字节alpha + 8字节颜色）
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                if (buffer.remaining() < 16) break;
                
                // 读取alpha信息（8字节）
                int alpha0 = buffer.get() & 0xFF;
                int alpha1 = buffer.get() & 0xFF;
                long alphaIndices = 0;
                for (int i = 0; i < 6; i++) {
                    alphaIndices |= ((long)(buffer.get() & 0xFF)) << (i * 8);
                }
                
                // 计算alpha插值表
                int[] alphas = new int[8];
                alphas[0] = alpha0;
                alphas[1] = alpha1;
                if (alpha0 > alpha1) {
                    for (int i = 1; i <= 6; i++) {
                        alphas[i + 1] = ((6 - i) * alpha0 + i * alpha1) / 6;
                    }
                } else {
                    for (int i = 1; i <= 4; i++) {
                        alphas[i + 1] = ((4 - i) * alpha0 + i * alpha1) / 4;
                    }
                    alphas[6] = 0;
                    alphas[7] = 255;
                }
                
                // 读取颜色信息（8字节）
                int color0 = buffer.getShort() & 0xFFFF;
                int color1 = buffer.getShort() & 0xFFFF;
                int colorIndices = buffer.getInt();
                
                // 解析RGB565颜色
                int[] colors = new int[4];
                colors[0] = rgb565ToArgb(color0);
                colors[1] = rgb565ToArgb(color1);
                colors[2] = interpolateColor(colors[0], colors[1], 2, 1);
                colors[3] = interpolateColor(colors[0], colors[1], 1, 2);
                
                // 填充4x4块
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int px = bx * 4 + x;
                        int py = by * 4 + y;
                        
                        if (px < width && py < height) {
                            int colorIndex = (colorIndices >> ((y * 4 + x) * 2)) & 0x3;
                            int baseColor = colors[colorIndex];
                            
                            // 正确解析alpha值
                            int alphaIndex = (int)((alphaIndices >> ((y * 4 + x) * 3)) & 0x7);
                            int alpha = alphas[alphaIndex];
                            int pixel = (alpha << 24) | (baseColor & 0x00FFFFFF);
                            image.setRGB(px, py, pixel);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 作为原始数据处理
     */
    private static void decompressAsRawData(byte[] data, BufferedImage image, int width, int height) {
        System.out.println("尝试作为原始RGBA数据处理");
        
        // 计算每像素字节数
        int totalPixels = width * height;
        int bytesPerPixel = data.length / totalPixels;
        
        System.out.println("每像素字节数: " + bytesPerPixel);
        
        if (bytesPerPixel >= 3) {
            // 尝试作为RGB或RGBA数据
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int dataIndex = (y * width + x) * bytesPerPixel;
                    
                    if (dataIndex + 2 < data.length) {
                        int r = data[dataIndex] & 0xFF;
                        int g = data[dataIndex + 1] & 0xFF;
                        int b = data[dataIndex + 2] & 0xFF;
                        int a = bytesPerPixel >= 4 && dataIndex + 3 < data.length ? 
                               data[dataIndex + 3] & 0xFF : 255;
                        
                        int pixel = (a << 24) | (r << 16) | (g << 8) | b;
                        image.setRGB(x, y, pixel);
                    } else {
                        // 数据不足时使用测试图案
                        image.setRGB(x, y, 0xFF808080);
                    }
                }
            }
        } else {
            // 数据不足，创建基于数据的图案
            System.out.println("数据不足，创建基于数据的图案");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int dataIndex = (y * width + x) % data.length;
                    int intensity = data[dataIndex] & 0xFF;
                    int pixel = (255 << 24) | (intensity << 16) | (intensity << 8) | intensity;
                    image.setRGB(x, y, pixel);
                }
            }
        }
    }
    
    /**
     * 将RGB565转换为ARGB
     */
    private static int rgb565ToArgb(int rgb565) {
        // 正确的RGB565到RGB888转换
        // 红色：5位 -> 8位
        int r = (rgb565 >> 11) & 0x1F;
        r = (r << 3) | (r >> 2);  // 扩展到8位并填充低位
        
        // 绿色：6位 -> 8位
        int g = (rgb565 >> 5) & 0x3F;
        g = (g << 2) | (g >> 4);  // 扩展到8位并填充低位
        
        // 蓝色：5位 -> 8位
        int b = rgb565 & 0x1F;
        b = (b << 3) | (b >> 2);  // 扩展到8位并填充低位
        
        // 不设置alpha，让调用者决定alpha值
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * 颜色插值
     */
    private static int interpolateColor(int color1, int color2, int weight1, int weight2) {
        int totalWeight = weight1 + weight2;
        
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int a = (a1 * weight1 + a2 * weight2) / totalWeight;
        int r = (r1 * weight1 + r2 * weight2) / totalWeight;
        int g = (g1 * weight1 + g2 * weight2) / totalWeight;
        int b = (b1 * weight1 + b2 * weight2) / totalWeight;
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * BC4/ATI1解压缩 - 单通道压缩
     */
    private static void decompressBC4(byte[] data, BufferedImage image, int width, int height) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                if (buffer.remaining() < 8) break;
                
                // BC4使用与DXT5相同的alpha压缩算法，但用于红色通道
                int red0 = buffer.get() & 0xFF;
                int red1 = buffer.get() & 0xFF;
                long redIndices = 0;
                for (int i = 0; i < 6; i++) {
                    redIndices |= ((long)(buffer.get() & 0xFF)) << (i * 8);
                }
                
                // 计算红色插值表
                int[] reds = new int[8];
                reds[0] = red0;
                reds[1] = red1;
                if (red0 > red1) {
                    for (int i = 1; i <= 6; i++) {
                        reds[i + 1] = ((6 - i) * red0 + i * red1) / 6;
                    }
                } else {
                    for (int i = 1; i <= 4; i++) {
                        reds[i + 1] = ((4 - i) * red0 + i * red1) / 4;
                    }
                    reds[6] = 0;
                    reds[7] = 255;
                }
                
                // 填充4x4块
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int px = bx * 4 + x;
                        int py = by * 4 + y;
                        
                        if (px < width && py < height) {
                            int redIndex = (int)((redIndices >> ((y * 4 + x) * 3)) & 0x7);
                            int red = reds[redIndex];
                            int pixel = (255 << 24) | (red << 16) | (red << 8) | red; // 灰度图
                            image.setRGB(px, py, pixel);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * BC5/ATI2解压缩 - 双通道压缩
     */
    private static void decompressBC5(byte[] data, BufferedImage image, int width, int height) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        int blocksX = (width + 3) / 4;
        int blocksY = (height + 3) / 4;
        
        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                if (buffer.remaining() < 16) break;
                
                // 红色通道
                int red0 = buffer.get() & 0xFF;
                int red1 = buffer.get() & 0xFF;
                long redIndices = 0;
                for (int i = 0; i < 6; i++) {
                    redIndices |= ((long)(buffer.get() & 0xFF)) << (i * 8);
                }
                
                // 绿色通道
                int green0 = buffer.get() & 0xFF;
                int green1 = buffer.get() & 0xFF;
                long greenIndices = 0;
                for (int i = 0; i < 6; i++) {
                    greenIndices |= ((long)(buffer.get() & 0xFF)) << (i * 8);
                }
                
                // 计算插值表
                int[] reds = new int[8];
                int[] greens = new int[8];
                
                // 红色插值
                reds[0] = red0;
                reds[1] = red1;
                if (red0 > red1) {
                    for (int i = 1; i <= 6; i++) {
                        reds[i + 1] = ((6 - i) * red0 + i * red1) / 6;
                    }
                } else {
                    for (int i = 1; i <= 4; i++) {
                        reds[i + 1] = ((4 - i) * red0 + i * red1) / 4;
                    }
                    reds[6] = 0;
                    reds[7] = 255;
                }
                
                // 绿色插值
                greens[0] = green0;
                greens[1] = green1;
                if (green0 > green1) {
                    for (int i = 1; i <= 6; i++) {
                        greens[i + 1] = ((6 - i) * green0 + i * green1) / 6;
                    }
                } else {
                    for (int i = 1; i <= 4; i++) {
                        greens[i + 1] = ((4 - i) * green0 + i * green1) / 4;
                    }
                    greens[6] = 0;
                    greens[7] = 255;
                }
                
                // 填充4x4块
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int px = bx * 4 + x;
                        int py = by * 4 + y;
                        
                        if (px < width && py < height) {
                            int redIndex = (int)((redIndices >> ((y * 4 + x) * 3)) & 0x7);
                            int greenIndex = (int)((greenIndices >> ((y * 4 + x) * 3)) & 0x7);
                            int red = reds[redIndex];
                            int green = greens[greenIndex];
                            int pixel = (255 << 24) | (red << 16) | (green << 8) | 0; // RG格式
                            image.setRGB(px, py, pixel);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * BC6H解压缩 - HDR压缩（简化实现）
     */
    private static void decompressBC6H(byte[] data, BufferedImage image, int width, int height) {
        System.out.println("BC6H格式暂不完全支持，使用简化处理");
        // BC6H是HDR格式，需要复杂的浮点数处理，这里提供简化实现
        decompressAsRawData(data, image, width, height);
    }
    
    /**
     * BC7解压缩 - 高质量压缩（简化实现）
     */
    private static void decompressBC7(byte[] data, BufferedImage image, int width, int height) {
        System.out.println("BC7格式暂不完全支持，使用简化处理");
        // BC7有多种模式，实现复杂，这里提供简化实现
        decompressAsRawData(data, image, width, height);
    }
    
    /**
     * ETC解压缩 - 移动设备压缩（简化实现）
     */
    private static void decompressETC(byte[] data, BufferedImage image, int width, int height) {
        System.out.println("ETC格式暂不完全支持，使用简化处理");
        // ETC压缩主要用于移动设备，这里提供简化实现
        decompressAsRawData(data, image, width, height);
    }
    
    /**
     * 创建一个动画测试图案
     */
    private static void createAnimationTestPattern(BufferedImage image, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 创建一个更有意义的测试图案，模拟动画帧
                int centerX = width / 2;
                int centerY = height / 2;
                double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
                double maxDistance = Math.sqrt(centerX * centerX + centerY * centerY);
                
                // 创建径向渐变效果
                int intensity = (int) (255 * (1.0 - distance / maxDistance));
                intensity = Math.max(0, Math.min(255, intensity));
                
                int r = intensity;
                int g = intensity / 2;
                int b = intensity / 4;
                int a = 255;
                
                int pixel = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, pixel);
            }
        }
    }
    
    /**
     * 备用方法：如果DDS加载失败，创建一个测试图像
     */
    public static BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 创建一个彩色方格图案
                int blockSize = 32;
                int blockX = x / blockSize;
                int blockY = y / blockSize;
                
                int color;
                if ((blockX + blockY) % 2 == 0) {
                    color = 0xFF4CAF50; // 绿色
                } else {
                    color = 0xFF2196F3; // 蓝色
                }
                
                image.setRGB(x, y, color);
            }
        }
        
        return image;
    }
}