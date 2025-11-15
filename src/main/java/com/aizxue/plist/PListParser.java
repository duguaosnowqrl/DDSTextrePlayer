package com.aizxue.plist;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.File;
import java.util.*;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.Dimension;

/**
 * PList解析器，用于解析TexturePacker生成的plist文件
 */
public class PListParser {
    
    public static class FrameInfo {
        public Rectangle frame;        // 在纹理图集中的位置和大小
        public Point offset;          // 偏移量
        public boolean rotated;       // 是否旋转
        public Rectangle sourceColorRect; // 源颜色矩形
        public Dimension sourceSize;  // 源尺寸
        public String name;           // 帧名称
        
        public FrameInfo(String name) {
            this.name = name;
        }
    }
    
    public static class TextureAtlasInfo {
        public Map<String, FrameInfo> frames = new LinkedHashMap<>();
        public String textureFileName;
        public Dimension textureSize;
        public String pixelFormat;
        
        public List<FrameInfo> getFramesInOrder() {
            List<FrameInfo> frameList = new ArrayList<>();
            // 按数字顺序排序帧
            List<String> sortedKeys = new ArrayList<>(frames.keySet());
            Collections.sort(sortedKeys, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    try {
                        int numA = Integer.parseInt(a);
                        int numB = Integer.parseInt(b);
                        return Integer.compare(numA, numB);
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                }
            });
            
            for (String key : sortedKeys) {
                frameList.add(frames.get(key));
            }
            return frameList;
        }
    }
    
    public static TextureAtlasInfo parsePList(File plistFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(plistFile);
        
        TextureAtlasInfo atlasInfo = new TextureAtlasInfo();
        
        // 获取根节点
        Element root = doc.getDocumentElement();
        NodeList dictNodes = root.getElementsByTagName("dict");
        
        if (dictNodes.getLength() > 0) {
            Element mainDict = (Element) dictNodes.item(0);
            parseMainDict(mainDict, atlasInfo);
        }
        
        return atlasInfo;
    }
    
    private static void parseMainDict(Element dictElement, TextureAtlasInfo atlasInfo) {
        NodeList children = dictElement.getChildNodes();
        String currentKey = null;
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                
                if ("key".equals(tagName)) {
                    currentKey = element.getTextContent();
                } else if ("dict".equals(tagName) && currentKey != null) {
                    if ("frames".equals(currentKey)) {
                        parseFramesDict(element, atlasInfo);
                    } else if ("metadata".equals(currentKey)) {
                        parseMetadataDict(element, atlasInfo);
                    }
                    currentKey = null;
                }
            }
        }
    }
    
    private static void parseFramesDict(Element framesDict, TextureAtlasInfo atlasInfo) {
        NodeList children = framesDict.getChildNodes();
        String currentFrameName = null;
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                
                if ("key".equals(tagName)) {
                    currentFrameName = element.getTextContent();
                } else if ("dict".equals(tagName) && currentFrameName != null) {
                    FrameInfo frameInfo = parseFrameDict(element, currentFrameName);
                    atlasInfo.frames.put(currentFrameName, frameInfo);
                    currentFrameName = null;
                }
            }
        }
    }
    
    private static FrameInfo parseFrameDict(Element frameDict, String frameName) {
        FrameInfo frameInfo = new FrameInfo(frameName);
        NodeList children = frameDict.getChildNodes();
        String currentKey = null;
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                
                if ("key".equals(tagName)) {
                    currentKey = element.getTextContent();
                } else if (currentKey != null) {
                    String value = element.getTextContent();
                    
                    switch (currentKey) {
                        case "frame":
                            frameInfo.frame = parseRect(value);
                            break;
                        case "offset":
                            frameInfo.offset = parsePoint(value);
                            break;
                        case "rotated":
                            frameInfo.rotated = "true".equals(tagName);
                            break;
                        case "sourceColorRect":
                            frameInfo.sourceColorRect = parseRect(value);
                            break;
                        case "sourceSize":
                            frameInfo.sourceSize = parseSize(value);
                            break;
                    }
                    currentKey = null;
                }
            }
        }
        
        return frameInfo;
    }
    
    private static void parseMetadataDict(Element metadataDict, TextureAtlasInfo atlasInfo) {
        NodeList children = metadataDict.getChildNodes();
        String currentKey = null;
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                
                if ("key".equals(tagName)) {
                    currentKey = element.getTextContent();
                } else if (currentKey != null) {
                    String value = element.getTextContent();
                    
                    switch (currentKey) {
                        case "textureFileName":
                        case "realTextureFileName":
                            atlasInfo.textureFileName = value;
                            break;
                        case "size":
                            atlasInfo.textureSize = parseSize(value);
                            break;
                        case "pixelFormat":
                            atlasInfo.pixelFormat = value;
                            break;
                    }
                    currentKey = null;
                }
            }
        }
    }
    
    // 解析形如 "{{x,y},{w,h}}" 的矩形字符串
    private static Rectangle parseRect(String rectStr) {
        rectStr = rectStr.replace("{", "").replace("}", "");
        String[] parts = rectStr.split(",");
        if (parts.length >= 4) {
            int x = (int) Math.round(Double.parseDouble(parts[0].trim()));
            int y = (int) Math.round(Double.parseDouble(parts[1].trim()));
            int w = (int) Math.round(Double.parseDouble(parts[2].trim()));
            int h = (int) Math.round(Double.parseDouble(parts[3].trim()));
            return new Rectangle(x, y, w, h);
        }
        return new Rectangle();
    }
    
    // 解析形如 "{x,y}" 的点字符串
    private static Point parsePoint(String pointStr) {
        pointStr = pointStr.replace("{", "").replace("}", "");
        String[] parts = pointStr.split(",");
        if (parts.length >= 2) {
            int x = (int) Math.round(Double.parseDouble(parts[0].trim()));
            int y = (int) Math.round(Double.parseDouble(parts[1].trim()));
            return new Point(x, y);
        }
        return new Point();
    }
    
    // 解析形如 "{w,h}" 的尺寸字符串
    private static Dimension parseSize(String sizeStr) {
        sizeStr = sizeStr.replace("{", "").replace("}", "");
        String[] parts = sizeStr.split(",");
        if (parts.length >= 2) {
            int w = (int) Math.round(Double.parseDouble(parts[0].trim()));
            int h = (int) Math.round(Double.parseDouble(parts[1].trim()));
            return new Dimension(w, h);
        }
        return new Dimension();
    }
}