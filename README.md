# TexturePacker 动画播放器

这是一个基于 Maven 和 Swing 技术的动画播放器，用于播放 TexturePacker 打包后的资源。

## 功能特性

- 解析 TexturePacker 生成的 .plist 文件
- 支持 DDS 纹理格式加载
- 动画播放控制（播放、暂停、停止）
- 可调节播放速度
- 支持旋转帧的正确显示
- 实时显示帧信息

## 项目结构

```
plist-test/
├── pom.xml                           # Maven 配置文件
├── attack.dds                        # DDS 纹理文件
├── attack.plist                      # TexturePacker 生成的配置文件
├── README.md                         # 说明文档
└── src/main/java/com/example/
    ├── AnimationPlayer.java          # 主应用程序类
    ├── PListParser.java              # PList 文件解析器
    └── DDSImageLoader.java           # DDS 图像加载器
```

## 运行要求

- Java 8 或更高版本
- Maven 3.6 或更高版本

## 编译和运行

1. **编译项目**
   ```bash
   mvn clean compile
   ```

2. **运行应用程序**
   ```bash
   mvn exec:java
   ```

   或者使用完整的类名：
   ```bash
   mvn exec:java -Dexec.mainClass="com.example.AnimationPlayer"
   ```

3. **打包为可执行 JAR**
   ```bash
   mvn clean package
   java -jar target/texture-packer-player-1.0-SNAPSHOT.jar
   ```

## 使用说明

1. **文件准备**
   - 确保 `attack.plist` 和 `attack.dds` 文件在项目根目录下
   - 如果没有 DDS 文件，程序会自动生成测试图像

2. **控制面板**
   - **播放按钮**: 开始播放动画
   - **暂停按钮**: 暂停当前播放
   - **停止按钮**: 停止播放并回到第一帧
   - **速度滑块**: 调整播放速度（10-500毫秒/帧）

3. **信息显示**
   - 当前帧数和总帧数
   - 播放状态
   - 帧的详细信息（尺寸、是否旋转等）

## 支持的格式

- **PList**: TexturePacker 生成的 XML 格式配置文件
- **DDS**: DirectDraw Surface 纹理格式（基础支持）
- **动画帧**: 支持旋转帧的正确显示

## 技术实现

### PList 解析
- 使用 DOM 解析器读取 XML 格式的 plist 文件
- 提取帧信息包括位置、尺寸、偏移、旋转状态等
- 按数字顺序排序动画帧

### DDS 图像处理
- 实现基础的 DDS 文件头解析
- 支持未压缩的 RGB/RGBA 格式
- 对于压缩格式提供备用处理方案

### 动画播放
- 使用 Swing Timer 实现帧动画
- 支持帧的旋转变换
- 自适应缩放以适应显示窗口

## 故障排除

1. **DDS 文件加载失败**
   - 程序会自动使用测试图像替代
   - 检查 DDS 文件是否损坏或格式不支持

2. **PList 解析错误**
   - 确认 plist 文件格式正确
   - 检查 XML 语法是否有误

3. **动画不显示**
   - 确认文件路径正确
   - 查看控制台错误信息

## 扩展功能

可以考虑添加的功能：
- 支持更多 DDS 压缩格式
- 添加帧编辑功能
- 支持其他纹理格式（PNG、JPG等）
- 导出动画为 GIF
- 批量处理多个动画文件

## 依赖库

- JOGL: 用于 DDS 格式支持
- JNA: 提供本地库访问能力
- Java Swing: GUI 框架

## 许可证

本项目仅供学习和演示使用。