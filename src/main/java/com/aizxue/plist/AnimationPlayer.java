package com.aizxue.plist;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * TexturePacker动画播放器主类
 */
public class AnimationPlayer extends JFrame {
    
    // 内部类用于存储plist文件节点信息
    private static class PlistFileNode {
        private String displayName;
        private String filePath;
        
        public PlistFileNode(String displayName, String filePath) {
            this.displayName = displayName;
            this.filePath = filePath;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private JPanel animationPanel;
    private JButton playPauseButton; // 合并播放和暂停按钮
    private JButton stopButton;
    private JButton prevFrameButton;
    private JButton nextFrameButton;
    private JTextField speedField;
    private JLabel frameLabel;
    private JLabel statusLabel;
    private JLabel fpsLabel;
    
    // 新增UI组件
    private JMenuBar menuBar;
    private JTree directoryTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JSplitPane mainSplitPane;
    private JScrollPane treeScrollPane;
    private JScrollPane thumbnailsScrollPane;
    private JPanel thumbnailsPanel;
    private boolean useThumbnailView = false;
    private boolean useThumbnailSummaryView = false;
    private java.util.List<PlistFileNode> plistNodeList = new java.util.ArrayList<>();
    private JPopupMenu viewModePopup;
    private JPanel rightPanel;
    // 缩略图加载优化相关
    private java.util.Map<String, BufferedImage> thumbnailCache = new java.util.HashMap<>();
    private java.util.Map<String, JButton> thumbnailButtons = new java.util.HashMap<>();
    private java.util.Set<String> scheduledThumbnails = new java.util.HashSet<>();
    private java.util.concurrent.ExecutorService thumbnailExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);
    private ImageIcon placeholderIcon100;
    // 扫描进度UI节流时间戳（毫秒）
    private long lastProgressUpdateMs = 0L;
    // 顶部菜单与右键菜单的显示方式单选项，用于状态同步
    private JRadioButtonMenuItem viewTreeModeItem;
    private JRadioButtonMenuItem viewThumbModeItem;
    private JRadioButtonMenuItem viewSummaryModeItem;
    private JRadioButtonMenuItem contextTreeModeItem;
    private JRadioButtonMenuItem contextThumbModeItem;
    private JRadioButtonMenuItem contextSummaryModeItem;
    
    private Timer animationTimer;
    private List<PListParser.FrameInfo> frames;
    private BufferedImage textureAtlas;
    private int currentFrameIndex = 0;
    private boolean isPlaying = false;
    private int animationSpeed = 67; // 毫秒 (15fps = 1000/15 ≈ 67ms)
    
    // FPS计算相关
    private long lastFrameTime = 0;
    private double actualFps = 0.0;
    private long frameCount = 0;
    private long fpsStartTime = 0;
    
    // 查看菜单控制变量
    private boolean showInfoText = Const.APP_DEFAULT_SHOW_INFO_TEXT; // 控制信息文字显示
    private boolean showSpriteBorder = Const.APP_DEFAULT_SHOW_SPRITE_BORDER; // 控制精灵边框显示
    
    // 当前文件路径
    private String currentPlistPath = ""; // 存储当前打开的plist文件路径
    
    // 记忆上次打开的目录
    private File lastOpenedDirectory = null; // 存储上次打开的目录位置
    
    public AnimationPlayer() {
        initializeUI();
    }
    
    private void initializeUI() {
        setTitle(Const.APP_NAME);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // 创建菜单栏
        createMenuBar();
        setJMenuBar(menuBar);
        
        // 创建目录树与缩略图面板
        createDirectoryTree();
        createThumbnailPanel();
        
        // 创建右侧面板（动画播放区域）
        createRightPanel();
        
        // 创建主分割面板
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(treeScrollPane);
        mainSplitPane.setRightComponent(rightPanel);
        mainSplitPane.setDividerLocation(250);
        mainSplitPane.setResizeWeight(0.0);
        
        add(mainSplitPane, BorderLayout.CENTER);
        
        // 在所有组件添加完成后设置窗口大小
        setPreferredSize(new Dimension(Const.APP_WINDOW_WIDTH, Const.APP_WINDOW_HEIGHT)); // 设置首选大小
        pack(); // 根据组件的首选大小调整窗口
        setLocationRelativeTo(null); // 居中显示
        
        // 初始化动画定时器
        animationTimer = new Timer(animationSpeed, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nextFrame();
            }
        });
        
        // 添加键盘监听器
        addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE:
                        // 空格键切换播放/暂停状态
                        if (isPlaying) {
                            pause();
                        } else {
                            play();
                        }
                        break;
                    case KeyEvent.VK_LEFT:
                        // 左方向键：上一帧
                        if (isPlaying) {
                            pause(); // 如果正在播放，先暂停
                        }
                        previousFrame();
                        break;
                    case KeyEvent.VK_RIGHT:
                        // 右方向键：下一帧
                        if (isPlaying) {
                            pause(); // 如果正在播放，先暂停
                        }
                        nextFrame();
                        break;
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });

        // 确保窗口可以接收键盘焦点
        setFocusable(true);
        requestFocus();

        // 创建左侧视图切换右键菜单
        createLeftPaneContextMenu();

        pack();
        setLocationRelativeTo(null);
    }
    
    private void createMenuBar() {
        menuBar = new JMenuBar();
        
        // 文件菜单
        JMenu fileMenu = new JMenu("文件");
        
        JMenuItem openFolderItem = new JMenuItem("打开文件夹");
        openFolderItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFolder();
            }
        });
        
        JMenuItem openFileItem = new JMenuItem("打开文件");
        openFileItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFile();
            }
        });
        
        fileMenu.add(openFolderItem);
        fileMenu.add(openFileItem);
        menuBar.add(fileMenu);
        
        // 添加5像素间距
        menuBar.add(Box.createHorizontalStrut(5));
        
        // 查看菜单
        JMenu viewMenu = new JMenu("查看");
        
        // 信息文字选项
        JCheckBoxMenuItem infoTextItem = new JCheckBoxMenuItem("信息文字", showInfoText);
        infoTextItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showInfoText = infoTextItem.isSelected();
                animationPanel.repaint(); // 重新绘制面板
            }
        });
        
        // 精灵边框选项
        JCheckBoxMenuItem spriteBorderItem = new JCheckBoxMenuItem("精灵边框", showSpriteBorder);
        spriteBorderItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSpriteBorder = spriteBorderItem.isSelected();
                animationPanel.repaint(); // 重新绘制面板
            }
        });
        
        viewMenu.add(infoTextItem);
        viewMenu.add(spriteBorderItem);

        // 显示方式子菜单（树状目录 / 缩略图）
        JMenu displayModeMenu = new JMenu("显示方式");
        ButtonGroup modeGroup = new ButtonGroup();
        viewTreeModeItem = new JRadioButtonMenuItem("树状目录", !useThumbnailView && !useThumbnailSummaryView);
        viewThumbModeItem = new JRadioButtonMenuItem("缩略图", useThumbnailView);
        viewSummaryModeItem = new JRadioButtonMenuItem("缩略图简要", useThumbnailSummaryView);
        modeGroup.add(viewTreeModeItem);
        modeGroup.add(viewThumbModeItem);
        modeGroup.add(viewSummaryModeItem);
        displayModeMenu.add(viewTreeModeItem);
        displayModeMenu.add(viewThumbModeItem);
        displayModeMenu.add(viewSummaryModeItem);

        viewTreeModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchToTreeView();
            }
        });
        viewThumbModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchToThumbnailView();
            }
        });
        viewSummaryModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchToThumbnailSummaryView();
            }
        });
        viewMenu.add(displayModeMenu);
        menuBar.add(viewMenu);
        
        // 添加5像素间距
        menuBar.add(Box.createHorizontalStrut(5));
        
        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");
        
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAboutDialog();
            }
        });
        
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
    }
    
    private void showAboutDialog() {
        JDialog aboutDialog = new JDialog(this, "关于", true);
        aboutDialog.setSize(400, 300);
        aboutDialog.setLocationRelativeTo(this);
        aboutDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // 软件信息面板 - 使用简单的垂直布局实现左对齐
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        
        // 软件名称
        JLabel titleLabel = new JLabel(Const.APP_NAME);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(titleLabel);
        infoPanel.add(Box.createVerticalStrut(10));
        
        // 描述信息
        JTextArea descArea = new JTextArea(Const.APP_DESCRIPTION);
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, descArea.getPreferredSize().height));
        infoPanel.add(descArea);
        infoPanel.add(Box.createVerticalStrut(10));

        // 版本信息
        JLabel versionLabel = new JLabel("版本: " + Const.APP_VERSION);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(versionLabel);
        infoPanel.add(Box.createVerticalStrut(2));
        
        // 作者信息
        JLabel authorLabel = new JLabel("作者: " + Const.APP_AUTHOR);
        authorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(authorLabel);
        infoPanel.add(Box.createVerticalStrut(2));

         // 作者信息
        JLabel javaVersionLabel = new JLabel("JAVA: " + Const.APP_JAVA_VERSION);
        javaVersionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(javaVersionLabel);
        infoPanel.add(Box.createVerticalStrut(2));
        
        // 添加垂直胶水推到顶部
        infoPanel.add(Box.createVerticalGlue());
        
        mainPanel.add(infoPanel, BorderLayout.CENTER);
        
        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okButton = new JButton("确定");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                aboutDialog.dispose();
            }
        });
        buttonPanel.add(okButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        aboutDialog.add(mainPanel);
        aboutDialog.setVisible(true);
    }
    
    private void createDirectoryTree() {
        rootNode = new DefaultMutableTreeNode("项目文件");
        treeModel = new DefaultTreeModel(rootNode);
        directoryTree = new JTree(treeModel);
        directoryTree.setRootVisible(true);  // 显示根节点
        directoryTree.setShowsRootHandles(true);
        
        // 添加树节点选择监听器
        directoryTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) directoryTree.getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.isLeaf()) {
                Object userObject = selectedNode.getUserObject();
                if (userObject instanceof PlistFileNode) {
                    loadPlistFile(selectedNode);
                } else if (userObject instanceof String) {
                    String plistPath = (String) userObject;
                    if (plistPath.endsWith(".plist")) {
                        loadPlistFile(selectedNode);
                    }
                }
            }
        });
        
        treeScrollPane = new JScrollPane(directoryTree);
        treeScrollPane.setPreferredSize(new Dimension(250, 400));

        // 绑定右键菜单到树
        directoryTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    viewModePopup.show(directoryTree, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    viewModePopup.show(directoryTree, e.getX(), e.getY());
                }
            }
        });
    }
    
    private void createRightPanel() {
        rightPanel = new JPanel(new BorderLayout());
        
        // 为右边面板添加鼠标点击事件，让其能够获取焦点
        rightPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 点击右边面板时，让主窗口重新获取焦点，以便快捷键能够工作
                AnimationPlayer.this.requestFocus();
            }
        });
        
        // 创建信息面板
        JPanel infoPanel = createInfoPanel();
        rightPanel.add(infoPanel, BorderLayout.NORTH);
        
        // 创建动画显示面板
        animationPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawCurrentFrame(g);
            }
        };
        animationPanel.setPreferredSize(new Dimension(400, 400));
        animationPanel.setBackground(new Color(128, 128, 128)); // 使用灰色背景更好地显示半透明效果
        
        // 为动画面板也添加鼠标点击事件
        animationPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 点击动画面板时，让主窗口重新获取焦点，以便快捷键能够工作
                AnimationPlayer.this.requestFocus();
            }
        });
        
        rightPanel.add(animationPanel, BorderLayout.CENTER);
        
        // 创建控制面板
        JPanel controlPanel = createControlPanel();
        rightPanel.add(controlPanel, BorderLayout.SOUTH);
    }
    
    private void openFolder() {
        JFileChooser fileChooser = new JFileChooser(getDefaultStartDirectory());
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("选择包含plist文件的文件夹");
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fileChooser.getSelectedFile();
            // 记忆选择的文件夹
            lastOpenedDirectory = selectedFolder;
            loadFolderStructure(selectedFolder);
            // 刷新缩略图视图
            refreshThumbnailsFromTree();
        }
    }
    
    private void openFile() {
        JFileChooser fileChooser = new JFileChooser(getDefaultStartDirectory());
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("选择plist文件");
        
        // 设置文件过滤器，只显示.plist文件
        javax.swing.filechooser.FileNameExtensionFilter filter = 
            new javax.swing.filechooser.FileNameExtensionFilter("Plist文件 (*.plist)", "plist");
        fileChooser.setFileFilter(filter);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // 记忆选择文件的父目录
            lastOpenedDirectory = selectedFile.getParentFile();
            
            // 更新目录树显示选中的文件
            updateTreeForSingleFile(selectedFile);
            
            // 加载文件
            loadSinglePlistFile(selectedFile);
        }
    }
    
    /**
     * 获取默认的起始目录
     * 优先使用记忆的目录，如果不存在则使用文档目录
     */
    private File getDefaultStartDirectory() {
        // 如果有记忆的目录且该目录存在，则使用记忆的目录
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists() && lastOpenedDirectory.isDirectory()) {
            return lastOpenedDirectory;
        }
        
        // 否则使用文档目录作为默认起始位置
        return new File(System.getProperty("user.home") + File.separator + "Documents");
    }
    
    /**
     * 智能文件名比较，支持数字排序
     * 例如：1, 2, 10, 20 而不是 1, 10, 2, 20
     */
    private int compareFileNames(String name1, String name2) {
        // 如果两个字符串相等，返回0
        if (name1.equals(name2)) {
            return 0;
        }
        
        // 提取文件名中的数字部分进行比较
        String num1 = name1.replaceAll("\\D", ""); // 移除非数字字符
        String num2 = name2.replaceAll("\\D", "");
        
        // 判断两个字符串是否都包含数字
        boolean hasNum1 = !num1.isEmpty();
        boolean hasNum2 = !num2.isEmpty();
        
        if (hasNum1 && hasNum2) {
            // 两个都包含数字，按数字比较
            try {
                long n1 = Long.parseLong(num1);
                long n2 = Long.parseLong(num2);
                int result = Long.compare(n1, n2);
                if (result != 0) {
                    return result;
                }
                // 如果数字相同，按字符串比较
                return name1.compareToIgnoreCase(name2);
            } catch (NumberFormatException e) {
                // 数字太大，按字符串比较
                return name1.compareToIgnoreCase(name2);
            }
        } else if (hasNum1 && !hasNum2) {
            // 只有第一个包含数字，数字优先
            return -1;
        } else if (!hasNum1 && hasNum2) {
            // 只有第二个包含数字，数字优先
            return 1;
        } else {
            // 两个都不包含数字，按字符串比较
            return name1.compareToIgnoreCase(name2);
        }
    }
    
    private void updateTreeForSingleFile(File plistFile) {
        // 清空当前树
        rootNode.removeAllChildren();
        
        // 设置根节点为文件所在的目录名
        File parentDir = plistFile.getParentFile();
        String dirName = parentDir != null ? parentDir.getName() : "根目录";
        rootNode.setUserObject(dirName);
        
        // 创建文件节点
        String fileName = plistFile.getName();
        String displayName = fileName.endsWith(".plist") ? 
            fileName.substring(0, fileName.length() - 6) : fileName;
        
        PlistFileNode plistNode = new PlistFileNode(displayName, plistFile.getAbsolutePath());
        DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(plistNode);
        
        // 添加到根节点
        rootNode.add(fileNode);
        
        // 刷新树模型
        treeModel.reload();
        
        // 展开根节点并选中文件节点
        directoryTree.expandPath(new TreePath(rootNode.getPath()));
        directoryTree.setSelectionPath(new TreePath(fileNode.getPath()));

        // 更新缩略图列表
        plistNodeList.clear();
        plistNodeList.add(plistNode);
        populateThumbnails(plistNodeList);
    }
    
    private void loadFolderStructure(File folder) {
        // 清理当前播放区内容
        if (isPlaying) {
            pause();
        }
        frames = null;
        textureAtlas = null;
        currentFrameIndex = 0;
        lastFrameTime = 0;
        actualFps = 0.0;
        animationPanel.repaint();
        updateFrameLabel();
        
        // 创建进度对话框
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("正在扫描文件夹...");
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setMaximum(100);
        
        JDialog progressDialog = new JDialog(this, "加载中", true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.add(new JLabel("正在扫描文件夹，请稍候...", JLabel.CENTER), BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        
        // 添加取消按钮
        JButton cancelButton = new JButton("取消");
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(cancelButton);
        progressDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        progressDialog.setSize(400, 120);
        progressDialog.setLocationRelativeTo(this);
        
        // 用于控制扫描是否被取消
        final boolean[] isCancelled = {false};
        
        // 在后台线程中执行文件扫描
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 清空现有树结构
                SwingUtilities.invokeLater(() -> {
                    rootNode.removeAllChildren();
                    rootNode.setUserObject(folder.getName());
                    // 重置缩略图加载状态
                    resetThumbnailLoadingState();
                });
                plistNodeList.clear();
                
                // 递归扫描文件夹
                scanDirectoryWithProgress(folder, rootNode, progressBar, isCancelled);
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    if (!isCancelled()) {
                        get(); // 检查是否有异常
                        
                        // 刷新树显示
                        SwingUtilities.invokeLater(() -> {
                            treeModel.reload();
                            directoryTree.expandRow(0);
                            progressDialog.dispose();
                            // 扫描完成后，缩略图已增量加入；触发一次可视区域调度
                            scheduleVisibleThumbnails();
                        });
                    } else {
                        // 取消操作，恢复原状态
                        SwingUtilities.invokeLater(() -> {
                            rootNode.removeAllChildren();
                            treeModel.reload();
                            progressDialog.dispose();
                            thumbnailsPanel.removeAll();
                            thumbnailsPanel.revalidate();
                            thumbnailsPanel.repaint();
                        });
                    }
                } catch (Exception e) {
                    if (!isCancelled[0]) {
                        e.printStackTrace();
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(AnimationPlayer.this, 
                            "加载文件夹时出错: " + e.getMessage(), 
                            "错误", JOptionPane.ERROR_MESSAGE);
                    } else {
                        progressDialog.dispose();
                    }
                }
            }
        };
        
        // 取消按钮事件处理
        cancelButton.addActionListener(e -> {
            isCancelled[0] = true;
            worker.cancel(true);
            progressDialog.dispose();
        });
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    private void scanDirectory(File directory, DefaultMutableTreeNode parentNode) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        // 对文件进行简单排序
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                // 先按类型排序：文件夹在前，文件在后
                if (a.isDirectory() && !b.isDirectory()) {
                    return -1;
                } else if (!a.isDirectory() && b.isDirectory()) {
                    return 1;
                }
                
                // 同类型的按文件名排序
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        
        // 添加文件夹和文件
        for (File file : files) {
            if (file.isDirectory()) {
                DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(file.getName());
                parentNode.add(dirNode);
                scanDirectory(file, dirNode);
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".plist")) {
                String fileName = file.getName();
                String displayName = fileName.substring(0, fileName.lastIndexOf('.'));
                // 创建一个包含显示名称和完整路径的对象
                PlistFileNode plistNode = new PlistFileNode(displayName, file.getAbsolutePath());
                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(plistNode);
                parentNode.add(fileNode);
            }
        }
    }
    
    private void scanDirectoryWithProgress(File directory, DefaultMutableTreeNode parentNode, JProgressBar progressBar, boolean[] isCancelled) {
        // 检查是否被取消
        if (isCancelled[0]) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) return;
        
        // 对文件进行简单排序
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                // 先按类型排序：文件夹在前，文件在后
                if (a.isDirectory() && !b.isDirectory()) {
                    return -1;
                } else if (!a.isDirectory() && b.isDirectory()) {
                    return 1;
                }
                
                // 同类型的按文件名排序
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        
        // 更新进度条显示当前扫描的目录完整路径（节流，避免EDT任务堆积）
        if (shouldUpdateProgressUi()) {
            SwingUtilities.invokeLater(() -> {
                String path = directory.getAbsolutePath();
                // 如果路径太长，显示省略号
                if (path.length() > 50) {
                    path = "..." + path.substring(path.length() - 47);
                }
                progressBar.setString("正在扫描: " + path);
            });
        }
        
        // 添加文件夹和文件
        for (File file : files) {
            if (isCancelled[0]) return; // 检查取消状态
            
            if (file.isDirectory()) {
                DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(file.getName());
                SwingUtilities.invokeLater(() -> {
                    parentNode.add(dirNode);
                });
                scanDirectoryWithProgress(file, dirNode, progressBar, isCancelled);
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".plist")) {
                String fileName = file.getName();
                String displayName = fileName.substring(0, fileName.lastIndexOf('.'));
                // 创建一个包含显示名称和完整路径的对象
                PlistFileNode plistNode = new PlistFileNode(displayName, file.getAbsolutePath());
                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(plistNode);
                SwingUtilities.invokeLater(() -> {
                    parentNode.add(fileNode);
                    plistNodeList.add(plistNode);
                    // 增量添加缩略图项（切到缩略图视图时即可看到逐步出现）
                    addThumbnailItemIncremental(plistNode);
                    thumbnailsPanel.revalidate();
                    thumbnailsPanel.repaint();
                });
            }
        }
        
        // 添加小延迟以便用户能看到进度更新
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            isCancelled[0] = true; // 标记为取消
        }
    }

    // 进度UI更新节流：至少间隔120ms再更新一次，避免EDT积压
    private boolean shouldUpdateProgressUi() {
        long now = System.currentTimeMillis();
        if (now - lastProgressUpdateMs >= 120) {
            lastProgressUpdateMs = now;
            return true;
        }
        return false;
    }

    private void refreshThumbnailsFromTree() {
        // 根据当前树中记录的plistNodeList刷新缩略图
        if (useThumbnailSummaryView) {
            rebuildThumbnailsForSummaryMode();
        } else {
            populateThumbnails(plistNodeList);
        }
        if (useThumbnailView) {
            switchToThumbnailView();
        }
    }

    // 复用的100x100占位图标，避免每次都在EDT绘制
    private ImageIcon getPlaceholderIcon100() {
        if (placeholderIcon100 == null) {
            BufferedImage placeholder = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = placeholder.createGraphics();
            g.setColor(new Color(220, 220, 220));
            g.fillRect(0, 0, 100, 100);
            g.setColor(new Color(180, 180, 180));
            g.drawRect(0, 0, 99, 99);
            g.setColor(Color.DARK_GRAY);
            g.drawString("加载中...", 20, 54);
            g.dispose();
            placeholderIcon100 = new ImageIcon(placeholder);
        }
        return placeholderIcon100;
    }

    private void populateThumbnails(java.util.List<PlistFileNode> nodes) {
        resetThumbnailLoadingState();
        // 增量添加，避免一次性在EDT创建海量组件阻塞
        for (PlistFileNode node : nodes) {
            addThumbnailItemIncremental(node);
        }
        thumbnailsPanel.revalidate();
        thumbnailsPanel.repaint();
        // 首屏与可视范围懒加载
        scheduleVisibleThumbnails();
    }

    private void resetThumbnailLoadingState() {
        scheduledThumbnails.clear();
        thumbnailButtons.clear();
        // 取消并重建线程池
        thumbnailExecutor.shutdownNow();
        thumbnailExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);
        thumbnailsPanel.removeAll();
    }

    private void addThumbnailItemIncremental(PlistFileNode node) {
        final String plistPath = node.getFilePath();
        final String displayName = node.getDisplayName();
        if (thumbnailButtons.containsKey(plistPath)) return;

        JButton thumbButton = new JButton(displayName);
        thumbButton.setPreferredSize(new Dimension(120, 140));
        thumbButton.setHorizontalTextPosition(SwingConstants.CENTER);
        thumbButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        thumbButton.setIconTextGap(6);
        thumbButton.putClientProperty("plistPath", plistPath);
        thumbButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadSinglePlistFile(new File(plistPath));
            }
        });
        thumbButton.setIcon(getPlaceholderIcon100());

        thumbnailsPanel.add(thumbButton);
        thumbnailButtons.put(plistPath, thumbButton);

        // 首屏优先加载，其他懒加载
        if (thumbnailsPanel.getComponentCount() <= 24) {
            scheduleThumbnailGeneration(plistPath, thumbButton);
        }
    }

    private void scheduleThumbnailGeneration(String plistPath, JButton button) {
        // 已有缓存直接应用
        BufferedImage cached = thumbnailCache.get(plistPath);
        if (cached != null) {
            button.setIcon(new ImageIcon(cached));
            return;
        }
        // 已在队列则跳过
        if (!scheduledThumbnails.add(plistPath)) return;

        thumbnailExecutor.submit(() -> {
            try {
                BufferedImage img = generateThumbnailForPlist(new File(plistPath), 100);
                if (img != null) {
                    thumbnailCache.put(plistPath, img);
                    SwingUtilities.invokeLater(() -> {
                        JButton b = thumbnailButtons.get(plistPath);
                        if (b != null) {
                            b.setIcon(new ImageIcon(img));
                            b.revalidate();
                            b.repaint();
                        }
                    });
                }
            } catch (Exception ignore) {
            }
        });
    }

    private void scheduleVisibleThumbnails() {
        Rectangle vis = thumbnailsPanel.getVisibleRect();
        for (Component comp : thumbnailsPanel.getComponents()) {
            if (!(comp instanceof JButton)) continue;
            JButton btn = (JButton) comp;
            Object prop = btn.getClientProperty("plistPath");
            if (!(prop instanceof String)) continue;
            String path = (String) prop;
            if (vis.intersects(btn.getBounds())) {
                if (!thumbnailCache.containsKey(path)) {
                    scheduleThumbnailGeneration(path, btn);
                }
            }
        }
    }

    private BufferedImage generateThumbnailForPlist(File plistFile, int thumbSize) {
        try {
            PListParser.TextureAtlasInfo atlasInfo = PListParser.parsePList(plistFile);
            java.util.List<PListParser.FrameInfo> frameList = atlasInfo.getFramesInOrder();
            File parentDir = plistFile.getParentFile();

            // 查找同名纹理图集
            String baseName = plistFile.getName().substring(0, plistFile.getName().lastIndexOf('.'));
            File[] possibleTextures = parentDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return (lowerName.startsWith(baseName.toLowerCase()) &&
                        (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                         lowerName.endsWith(".jpeg") || lowerName.endsWith(".dds")));
            });

            if (possibleTextures == null || possibleTextures.length == 0) {
                return null;
            }

            BufferedImage atlas;
            String texturePath = possibleTextures[0].getAbsolutePath();
            if (texturePath.toLowerCase().endsWith(".dds")) {
                atlas = DDSImageLoader.loadDDS(possibleTextures[0]);
            } else {
                atlas = javax.imageio.ImageIO.read(new File(texturePath));
            }

            BufferedImage preview;
            if (frameList != null && !frameList.isEmpty()) {
                PListParser.FrameInfo first = frameList.get(0);
                Rectangle frameRect = first.frame;
                if (frameRect == null) return null;

                int atlasWidth = atlas.getWidth();
                int atlasHeight = atlas.getHeight();
                int startX = frameRect.x;
                int startY = frameRect.y;
                int cropWidth = first.rotated ? frameRect.height : frameRect.width;
                int cropHeight = first.rotated ? frameRect.width : frameRect.height;

                int safeX = Math.max(0, Math.min(startX, atlasWidth - 1));
                int safeY = Math.max(0, Math.min(startY, atlasHeight - 1));
                int safeWidth = Math.min(cropWidth, atlasWidth - safeX);
                int safeHeight = Math.min(cropHeight, atlasHeight - safeY);

                BufferedImage extracted = atlas.getSubimage(safeX, safeY, safeWidth, safeHeight);
                BufferedImage finalFrame;
                if (first.rotated) {
                    BufferedImage tempCopy = new BufferedImage(extracted.getWidth(), extracted.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D tg = tempCopy.createGraphics();
                    tg.drawImage(extracted, 0, 0, null);
                    tg.dispose();
                    finalFrame = rotateImage(tempCopy, -90);
                } else {
                    finalFrame = new BufferedImage(extracted.getWidth(), extracted.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D cg = finalFrame.createGraphics();
                    cg.drawImage(extracted, 0, 0, null);
                    cg.dispose();
                }

                int canvasWidth = first.sourceSize != null ? first.sourceSize.width : finalFrame.getWidth();
                int canvasHeight = first.sourceSize != null ? first.sourceSize.height : finalFrame.getHeight();
                BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                int drawX = 0, drawY = 0;
                if (first.sourceColorRect != null) {
                    drawX += first.sourceColorRect.x;
                    drawY += first.sourceColorRect.y;
                }
                g.drawImage(finalFrame, drawX, drawY, null);
                g.dispose();
                preview = canvas;
            } else {
                // 没有帧信息则使用整张图集缩略图
                preview = atlas;
            }

            // 缩放到 thumbSize，并居中放入固定100x100（thumbSize×thumbSize）画布
            int w = preview.getWidth();
            int h = preview.getHeight();
            double scale = (double) thumbSize / Math.max(w, h);
            int nw = (int) Math.max(1, Math.round(w * scale));
            int nh = (int) Math.max(1, Math.round(h * scale));

            BufferedImage canvas = new BufferedImage(thumbSize, thumbSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = canvas.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int dx = (thumbSize - nw) / 2;
            int dy = (thumbSize - nh) / 2;
            sg.drawImage(preview, dx, dy, nw, nh, null);
            sg.dispose();
            return canvas;
        } catch (Exception ex) {
            return null;
        }
    }
    
    private void loadPlistFile(DefaultMutableTreeNode node) {
        // 获取文件路径和显示名称
        String plistPath;
        String displayName;
        
        Object userObject = node.getUserObject();
        if (userObject instanceof PlistFileNode) {
            PlistFileNode plistNode = (PlistFileNode) userObject;
            plistPath = plistNode.getFilePath();
            displayName = plistNode.getDisplayName();
        } else {
            // 兼容旧的字符串格式
            plistPath = userObject.toString();
            String fileName = new File(plistPath).getName();
            displayName = fileName.endsWith(".plist") ? 
                fileName.substring(0, fileName.length() - 6) : fileName;
        }
        
        // 停止当前播放
        if (isPlaying) {
            pause();
        }
        
        try {
            // 加载新的plist文件
            File plistFile = new File(plistPath);
            File parentDir = plistFile.getParentFile();
            
            // 设置当前文件路径
            currentPlistPath = plistPath;
            
            // 解析plist文件
            PListParser.TextureAtlasInfo atlasInfo = PListParser.parsePList(plistFile);
            frames = atlasInfo.getFramesInOrder();
            
            // 查找对应的纹理图集文件
            String baseName = plistFile.getName().substring(0, plistFile.getName().lastIndexOf('.'));
            File[] possibleTextures = parentDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return (lowerName.startsWith(baseName.toLowerCase()) && 
                       (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || 
                        lowerName.endsWith(".jpeg") || lowerName.endsWith(".dds")));
            });
            
            if (possibleTextures != null && possibleTextures.length > 0) {
                String texturePath = possibleTextures[0].getAbsolutePath();
                if (texturePath.toLowerCase().endsWith(".dds")) {
                    textureAtlas = DDSImageLoader.loadDDS(possibleTextures[0]);
                } else {
                    textureAtlas = javax.imageio.ImageIO.read(new File(texturePath));
                }
            }
            
            // 重置播放状态
            currentFrameIndex = 0;
            isPlaying = false;
            
            // 更新UI
            updateFrameLabel();
            animationPanel.repaint();
            
            statusLabel.setText("已加载: " + displayName + " (" + frames.size() + " 帧)");
            
            // 自动开始播放动画
            if (frames != null && !frames.isEmpty()) {
                play();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("加载失败: " + e.getMessage());
        }
    }
    
    private void loadSinglePlistFile(File plistFile) {
        // 停止当前播放
        if (isPlaying) {
            pause();
        }
        
        try {
            // 获取文件信息
            String plistPath = plistFile.getAbsolutePath();
            String fileName = plistFile.getName();
            String displayName = fileName.endsWith(".plist") ? 
                fileName.substring(0, fileName.length() - 6) : fileName;
            File parentDir = plistFile.getParentFile();
            
            // 设置当前文件路径
            currentPlistPath = plistPath;
            
            // 解析plist文件
            PListParser.TextureAtlasInfo atlasInfo = PListParser.parsePList(plistFile);
            frames = atlasInfo.getFramesInOrder();
            
            // 查找对应的纹理图集文件
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            File[] possibleTextures = parentDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return (lowerName.startsWith(baseName.toLowerCase()) && 
                       (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || 
                        lowerName.endsWith(".jpeg") || lowerName.endsWith(".dds")));
            });
            
            if (possibleTextures != null && possibleTextures.length > 0) {
                String texturePath = possibleTextures[0].getAbsolutePath();
                if (texturePath.toLowerCase().endsWith(".dds")) {
                    textureAtlas = DDSImageLoader.loadDDS(possibleTextures[0]);
                } else {
                    textureAtlas = javax.imageio.ImageIO.read(new File(texturePath));
                }
            }
            
            // 重置播放状态
            currentFrameIndex = 0;
            isPlaying = false;
            
            // 更新UI
            updateFrameLabel();
            animationPanel.repaint();
            
            statusLabel.setText("已加载: " + displayName + " (" + frames.size() + " 帧)");
            
            // 自动开始播放动画
            if (frames != null && !frames.isEmpty()) {
                play();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("加载失败: " + e.getMessage());
        }
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        
        // 创建播放/暂停切换按钮
        playPauseButton = new JButton("播放");
        playPauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPlaying) {
                    pause();
                } else {
                    play();
                }
            }
        });
        
        stopButton = new JButton("停止");
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stop();
            }
        });
        
        prevFrameButton = new JButton("上一帧");
        prevFrameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                previousFrame();
            }
        });
        
        nextFrameButton = new JButton("下一帧");
        nextFrameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                nextFrame();
            }
        });
        
        speedField = new JTextField(String.format("%.1f", 1000.0 / animationSpeed), 5); // 显示帧率而不是毫秒
        speedField.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    try {
                        double fps = Double.parseDouble(speedField.getText());
                        if (fps > 0 && fps <= 100) {
                            animationSpeed = (int) Math.round(1000.0 / fps); // 将帧率转换为毫秒间隔
                            if (animationTimer != null) {
                                animationTimer.setDelay(animationSpeed);
                            }
                        } else {
                            speedField.setText(String.format("%.1f", 1000.0 / animationSpeed)); // 恢复原值
                        }
                    } catch (NumberFormatException ex) {
                        speedField.setText(String.format("%.1f", 1000.0 / animationSpeed)); // 恢复原值
                    }
                    // 让输入框失去焦点，使主窗口重新获得焦点以便快捷键生效
                    AnimationPlayer.this.requestFocus();
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        panel.add(playPauseButton);
        panel.add(stopButton);
        panel.add(Box.createHorizontalStrut(10)); // 添加间距
        panel.add(prevFrameButton);
        panel.add(nextFrameButton);
        panel.add(Box.createHorizontalStrut(10)); // 添加间距
        panel.add(new JLabel("帧率:"));
        panel.add(speedField);
        panel.add(new JLabel("FPS"));
        
        return panel;
    }
    
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        
        frameLabel = new JLabel("帧: 0/0");
        statusLabel = new JLabel("状态: 已停止");
        
        panel.add(frameLabel);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(statusLabel);
        
        return panel;
    }
    
    private void loadAnimation() {
        try {
            // 加载plist文件
            File plistFile = new File("attack.plist");
            if (!plistFile.exists()) {
                statusLabel.setText("错误: 找不到attack.plist文件");
                return;
            }
            
            PListParser.TextureAtlasInfo atlasInfo = PListParser.parsePList(plistFile);
            frames = atlasInfo.getFramesInOrder();
            
            // 尝试加载纹理图像（支持多种格式）
            String baseName = "attack";
            File[] possibleTextures = new File(".").listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return (lowerName.startsWith(baseName.toLowerCase()) && 
                       (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || 
                        lowerName.endsWith(".jpeg") || lowerName.endsWith(".dds")));
            });
            
            boolean textureLoaded = false;
            if (possibleTextures != null && possibleTextures.length > 0) {
                // 优先级：PNG > JPG > JPEG > DDS
                File textureFile = null;
                for (File file : possibleTextures) {
                    String lowerName = file.getName().toLowerCase();
                    if (lowerName.endsWith(".png")) {
                        textureFile = file;
                        break;
                    }
                }
                if (textureFile == null) {
                    for (File file : possibleTextures) {
                        String lowerName = file.getName().toLowerCase();
                        if (lowerName.endsWith(".jpg")) {
                            textureFile = file;
                            break;
                        }
                    }
                }
                if (textureFile == null) {
                    for (File file : possibleTextures) {
                        String lowerName = file.getName().toLowerCase();
                        if (lowerName.endsWith(".jpeg")) {
                            textureFile = file;
                            break;
                        }
                    }
                }
                if (textureFile == null) {
                    for (File file : possibleTextures) {
                        String lowerName = file.getName().toLowerCase();
                        if (lowerName.endsWith(".dds")) {
                            textureFile = file;
                            break;
                        }
                    }
                }
                
                if (textureFile != null) {
                    try {
                        String fileName = textureFile.getName().toLowerCase();
                        if (fileName.endsWith(".dds")) {
                            textureAtlas = DDSImageLoader.loadDDS(textureFile);
                            statusLabel.setText("已加载DDS纹理: " + textureAtlas.getWidth() + "x" + textureAtlas.getHeight());
                        } else {
                            textureAtlas = javax.imageio.ImageIO.read(textureFile);
                            String format = fileName.endsWith(".png") ? "PNG" : 
                                          fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ? "JPG" : "未知";
                            statusLabel.setText("已加载" + format + "纹理: " + textureAtlas.getWidth() + "x" + textureAtlas.getHeight());
                        }
                        textureLoaded = true;
                    } catch (Exception e) {
                        System.out.println("纹理加载失败: " + e.getMessage());
                    }
                }
            }
            
            if (!textureLoaded) {
                // 创建测试图像
                textureAtlas = DDSImageLoader.createTestImage(
                    atlasInfo.textureSize != null ? atlasInfo.textureSize.width : 1364,
                    atlasInfo.textureSize != null ? atlasInfo.textureSize.height : 124
                );
                statusLabel.setText("使用测试图像 (找不到纹理文件)");
            }
            
            updateFrameLabel();
            animationPanel.repaint();
            
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("加载失败: " + e.getMessage());
        }
    }
    
    private void drawCurrentFrame(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        
        if (frames == null || frames.isEmpty() || textureAtlas == null) {
            if (showInfoText) {
                g2d.setColor(Color.WHITE);
                g2d.drawString("没有可显示的动画帧", 20, 30);
                g2d.drawString(String.format("实际FPS: %.1f", actualFps), 10, 80);
            }
        } else {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        PListParser.FrameInfo currentFrame = frames.get(currentFrameIndex);
        
        if (currentFrame.frame != null) {
            // 从纹理图集中提取当前帧
            Rectangle frameRect = currentFrame.frame;
            
            try {
                BufferedImage frameImage;
                
                // 重写切图逻辑，按照测试程序的方式处理
                int atlasWidth = textureAtlas.getWidth();
                int atlasHeight = textureAtlas.getHeight();
                
                // 计算切图区域的起始坐标和尺寸
                int startX = frameRect.x;
                int startY = frameRect.y;
                int cropWidth, cropHeight;
                
                if (currentFrame.rotated) {
                    // 对于旋转的子图，结束坐标为 (x+h, y+w)
                    // 这意味着在atlas中，宽度和高度已经交换了
                    cropWidth = frameRect.height;  // 使用height作为宽度
                    cropHeight = frameRect.width;  // 使用width作为高度
                } else {
                    // 对于不旋转的子图，结束坐标为 (x+w, y+h)
                    cropWidth = frameRect.width;
                    cropHeight = frameRect.height;
                }
                
                // 边界检查，确保不超出atlas范围
                int safeX = Math.max(0, Math.min(startX, atlasWidth - 1));
                int safeY = Math.max(0, Math.min(startY, atlasHeight - 1));
                int safeWidth = Math.min(cropWidth, atlasWidth - safeX);
                int safeHeight = Math.min(cropHeight, atlasHeight - safeY);
                
                // 从atlas中提取图片
                BufferedImage extractedImage = textureAtlas.getSubimage(safeX, safeY, safeWidth, safeHeight);
                
                // 创建独立的图片副本并处理旋转
                BufferedImage finalFrame;
                if (currentFrame.rotated) {
                    // 旋转图片：先复制提取的图像，然后旋转-90度
                    BufferedImage tempCopy = new BufferedImage(extractedImage.getWidth(), extractedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D tempG = tempCopy.createGraphics();
                    tempG.drawImage(extractedImage, 0, 0, null);
                    tempG.dispose();
                    
                    // 旋转-90度恢复原始方向
                    finalFrame = rotateImage(tempCopy, -90);
                } else {
                    // 非旋转图片：直接复制
                    finalFrame = new BufferedImage(extractedImage.getWidth(), extractedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D copyG = finalFrame.createGraphics();
                    copyG.drawImage(extractedImage, 0, 0, null);
                    copyG.dispose();
                }
                
                BufferedImage extractedFrame = finalFrame;
                
                // 正确处理TexturePacker的坐标系统
                // sourceSize: 原始图像的完整尺寸
                // sourceColorRect: 有效像素区域在原始图像中的位置和尺寸
                // offset: 图像中心相对于原始图像中心的偏移
                
                int canvasWidth = currentFrame.sourceSize != null ? currentFrame.sourceSize.width : extractedFrame.getWidth();
                int canvasHeight = currentFrame.sourceSize != null ? currentFrame.sourceSize.height : extractedFrame.getHeight();
                frameImage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D frameGraphics = frameImage.createGraphics();
                frameGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                frameGraphics.setComposite(AlphaComposite.SrcOver);
                // 不需要清除画布，BufferedImage默认创建时已经是透明的
                
                // 计算绘制位置
                // TexturePacker的offset是相对于sourceSize中心的偏移
                // sourceColorRect定义了实际内容在sourceSize画布中的位置
                int drawX = 0, drawY = 0;
                if (currentFrame.sourceColorRect != null) {
                    drawX += currentFrame.sourceColorRect.x;
                    drawY += currentFrame.sourceColorRect.y;
                }
                
                // 应用offset偏移
                if (currentFrame.offset != null) {
                    // drawX += currentFrame.offset.x;
                    // drawY += currentFrame.offset.y;
                }
                
                
                frameGraphics.drawImage(extractedFrame, drawX, drawY, null);
                frameGraphics.dispose();
                
                // 计算居中显示的位置
                int panelWidth = animationPanel.getWidth();
                int panelHeight = animationPanel.getHeight();
                int imageWidth = frameImage.getWidth();
                int imageHeight = frameImage.getHeight();
                
                // 计算缩放比例以适应面板
                double scaleX = (double) panelWidth / imageWidth;
                double scaleY = (double) panelHeight / imageHeight;
                double scale = Math.min(scaleX, scaleY) * 0.8; // 留一些边距
                scale = 1.0;
                
                int scaledWidth = (int) (imageWidth * scale);
                int scaledHeight = (int) (imageHeight * scale);
                
                int x = (panelWidth - scaledWidth) / 2;
                int y = (panelHeight - scaledHeight) / 2;
                
                
                // offset已经在帧创建时处理，这里不需要再次应用
                g2d.drawImage(frameImage, x, y, scaledWidth, scaledHeight, null);
                
                // 为frameImage添加边框（根据设置控制显示）
                if (showSpriteBorder) {
                    g2d.setColor(Color.RED);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawRect(x, y, scaledWidth, scaledHeight);
                }
                
                // 绘制帧信息（根据设置控制显示）
                if (showInfoText) {
                    g2d.setColor(Color.WHITE);
                    // 显示文件路径
                    g2d.drawString("文件: " + currentPlistPath, 10, 20);
                    g2d.drawString("帧: " + currentFrame.name, 10, 40);
                    g2d.drawString("尺寸: " + frameRect.width + "x" + frameRect.height, 10, 60);
                    // 显示精灵尺寸（sourceSize）
                    if (currentFrame.sourceSize != null) {
                        g2d.drawString("精灵尺寸: " + currentFrame.sourceSize.width + "x" + currentFrame.sourceSize.height, 10, 80);
                    }
                    g2d.drawString(String.format("实际FPS: %.1f", actualFps), 10, 100);
                }
                
            } catch (Exception e) {
                g2d.setColor(Color.RED);
                g2d.drawString("帧显示错误: " + e.getMessage(), 10, 30);
            }
        }
        }
        
        g2d.dispose();
    }
    
    private BufferedImage rotateImage(BufferedImage image, int degrees) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 对于90度的倍数旋转，需要交换宽高
        boolean swapDimensions = (Math.abs(degrees) % 180) == 90;
        int newWidth = swapDimensions ? height : width;
        int newHeight = swapDimensions ? width : height;
        
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        
        // 设置高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 清除背景为透明
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, newWidth, newHeight);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // 移动到旋转中心
        g2d.translate(newWidth / 2.0, newHeight / 2.0);
        // 根据传入的角度进行旋转
        g2d.rotate(Math.toRadians(degrees));
        // 移动图像使其居中
        g2d.translate(-width / 2.0, -height / 2.0);
        
        // 绘制原图像
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        return rotated;
    }
    
    private void play() {
        if (frames != null && !frames.isEmpty()) {
            isPlaying = true;
            // 重置fps计算相关变量
            lastFrameTime = 0;
            fpsStartTime = 0;
            frameCount = 0;
            actualFps = 1000.0 / animationSpeed; // 显示理论FPS
            animationTimer.start();
            statusLabel.setText("状态: 播放中");
            playPauseButton.setText("暂停"); // 更新按钮文字
        }
    }
    
    private void pause() {
        isPlaying = false;
        animationTimer.stop();
        statusLabel.setText("状态: 已暂停");
        actualFps = 0.0;
        animationPanel.repaint();
        playPauseButton.setText("播放"); // 更新按钮文字
    }
    
    private void stop() {
        isPlaying = false;
        animationTimer.stop();
        currentFrameIndex = 0;
        updateFrameLabel();
        animationPanel.repaint();
        statusLabel.setText("状态: 已停止");
        actualFps = 0.0;
        playPauseButton.setText("播放"); // 更新按钮文字
    }
    
    private void nextFrame() {
        if (frames != null && !frames.isEmpty()) {
            // 改进的FPS计算 - 使用滑动窗口平均值
            if (isPlaying) {
                long currentTime = System.currentTimeMillis();
                
                if (fpsStartTime == 0) {
                    fpsStartTime = currentTime;
                    frameCount = 0;
                }
                
                frameCount++;
                
                // 每秒更新一次FPS显示，使用平均值
                long elapsedTime = currentTime - fpsStartTime;
                if (elapsedTime >= 1000) { // 每1秒计算一次平均FPS
                    actualFps = (frameCount * 1000.0) / elapsedTime;
                    fpsStartTime = currentTime;
                    frameCount = 0;
                } else if (frameCount == 1) {
                    // 第一帧时显示理论FPS
                    actualFps = 1000.0 / animationSpeed;
                }
            }
            
            currentFrameIndex = (currentFrameIndex + 1) % frames.size();
            updateFrameLabel();
            animationPanel.repaint();
        }
    }
    
    private void previousFrame() {
        if (frames != null && !frames.isEmpty()) {
            currentFrameIndex = (currentFrameIndex - 1 + frames.size()) % frames.size();
            updateFrameLabel();
            animationPanel.repaint();
        }
    }
    
    private void updateFrameLabel() {
        if (frames != null) {
            frameLabel.setText("帧: " + (currentFrameIndex + 1) + "/" + frames.size());
        } else {
            frameLabel.setText("帧: 0/0");
        }
    }
    

    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                new AnimationPlayer().setVisible(true);
            }
        });
    }
    
    private void createThumbnailPanel() {
        thumbnailsPanel = new JPanel(new TileWrapLayout(10, 10));
        thumbnailsPanel.setBackground(new Color(245, 245, 245));
        thumbnailsScrollPane = new JScrollPane(thumbnailsPanel);
        thumbnailsScrollPane.setPreferredSize(new Dimension(250, 400));
        thumbnailsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // 根据视口大小变化触发布局重算（窗口或分割条调整时）
        thumbnailsScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                thumbnailsPanel.revalidate();
                thumbnailsPanel.repaint();
                scheduleVisibleThumbnails();
            }
        });

        // 视口滚动或位置变化时，懒加载可视区域内的缩略图
        thumbnailsScrollPane.getViewport().addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent e) {
                scheduleVisibleThumbnails();
            }
        });

        // 绑定右键菜单到缩略图面板
        thumbnailsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    viewModePopup.show(thumbnailsPanel, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    viewModePopup.show(thumbnailsPanel, e.getX(), e.getY());
                }
            }
        });
    }

    private void switchToTreeView() {
        useThumbnailView = false;
        mainSplitPane.setLeftComponent(treeScrollPane);
        mainSplitPane.setDividerLocation(250);
        syncDisplayModeSelection();
    }

    private void switchToThumbnailView() {
        useThumbnailView = true;
        useThumbnailSummaryView = false;
        mainSplitPane.setLeftComponent(thumbnailsScrollPane);
        mainSplitPane.setDividerLocation(250);
        // 切换到缩略图视图时，用全量plist列表重建缩略图
        populateThumbnails(plistNodeList);
        syncDisplayModeSelection();
    }

    private void switchToThumbnailSummaryView() {
        useThumbnailView = false;
        useThumbnailSummaryView = true;
        mainSplitPane.setLeftComponent(thumbnailsScrollPane);
        mainSplitPane.setDividerLocation(250);
        rebuildThumbnailsForSummaryMode();
        syncDisplayModeSelection();
        scheduleVisibleThumbnails();
    }

    private void syncDisplayModeSelection() {
        if (viewTreeModeItem != null) viewTreeModeItem.setSelected(!useThumbnailView && !useThumbnailSummaryView);
        if (viewThumbModeItem != null) viewThumbModeItem.setSelected(useThumbnailView);
        if (viewSummaryModeItem != null) viewSummaryModeItem.setSelected(useThumbnailSummaryView);
        if (contextTreeModeItem != null) contextTreeModeItem.setSelected(!useThumbnailView && !useThumbnailSummaryView);
        if (contextThumbModeItem != null) contextThumbModeItem.setSelected(useThumbnailView);
        if (contextSummaryModeItem != null) contextSummaryModeItem.setSelected(useThumbnailSummaryView);
    }

    private void createLeftPaneContextMenu() {
        viewModePopup = new JPopupMenu();
        JMenu modeMenu = new JMenu("显示方式");
        ButtonGroup group = new ButtonGroup();
        contextTreeModeItem = new JRadioButtonMenuItem("树状目录", !useThumbnailView && !useThumbnailSummaryView);
        contextThumbModeItem = new JRadioButtonMenuItem("缩略图", useThumbnailView);
        contextSummaryModeItem = new JRadioButtonMenuItem("缩略图简要", useThumbnailSummaryView);
        group.add(contextTreeModeItem);
        group.add(contextThumbModeItem);
        group.add(contextSummaryModeItem);
        modeMenu.add(contextTreeModeItem);
        modeMenu.add(contextThumbModeItem);
        modeMenu.add(contextSummaryModeItem);

        contextTreeModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchToTreeView();
            }
        });
        contextThumbModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchToThumbnailView();
            }
        });
        contextSummaryModeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchToThumbnailSummaryView();
            }
        });

        viewModePopup.add(modeMenu);
    }

    /**
     * 一个简单的可换行布局：按容器（视口）宽度自动计算一行可放置的缩略图数量，超出则换行。
     * 使用每个子组件的 preferredSize 作为瓦片尺寸。
     */
    public static class TileWrapLayout implements LayoutManager {
        private final int hgap;
        private final int vgap;

        public TileWrapLayout(int hgap, int vgap) {
            this.hgap = hgap;
            this.vgap = vgap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {}

        @Override
        public void removeLayoutComponent(Component comp) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets ins = parent.getInsets();
            int targetWidth = getTargetWidth(parent) - ins.left - ins.right;
            int x = 0;
            int rowHeight = 0;
            int usedHeight = 0;

            for (Component comp : parent.getComponents()) {
                if (!comp.isVisible()) continue;
                Dimension d = comp.getPreferredSize();
                if (x > 0 && x + d.width > targetWidth) {
                    usedHeight += rowHeight + vgap;
                    x = 0;
                    rowHeight = 0;
                }
                x += d.width + (x > 0 ? hgap : 0);
                rowHeight = Math.max(rowHeight, d.height);
            }
            usedHeight += rowHeight;

            if (usedHeight < 0) usedHeight = 0;
            return new Dimension(Math.max(targetWidth, 0) + ins.left + ins.right,
                    usedHeight + ins.top + ins.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets ins = parent.getInsets();
            int avail = Math.max(parent.getWidth() - ins.left - ins.right, 0);
            int x = ins.left;
            int y = ins.top;
            int rowHeight = 0;

            for (Component comp : parent.getComponents()) {
                if (!comp.isVisible()) continue;
                Dimension d = comp.getPreferredSize();
                if (x > ins.left && (x - ins.left) + d.width > avail) {
                    x = ins.left;
                    y += rowHeight + vgap;
                    rowHeight = 0;
                }
                comp.setBounds(x, y, d.width, d.height);
                x += d.width + hgap;
                rowHeight = Math.max(rowHeight, d.height);
            }
        }

        private int getTargetWidth(Container parent) {
            Container p = parent.getParent();
            if (p instanceof JViewport) {
                int w = p.getWidth();
                if (w > 0) return w;
            }
            int w = parent.getWidth();
            return w > 0 ? w : 250; // 初始值回退
        }
    }
    private void rebuildThumbnailsForSummaryMode() {
        resetThumbnailLoadingState();
        // 遍历树，仅选择每个目录下第一个plist
        java.util.List<PlistFileNode> summaryList = new java.util.ArrayList<>();
        collectFirstPlistPerDirectory(rootNode, summaryList);
        for (PlistFileNode node : summaryList) {
            addThumbnailItemIncremental(node);
        }
        thumbnailsPanel.revalidate();
        thumbnailsPanel.repaint();
    }

    private void collectFirstPlistPerDirectory(DefaultMutableTreeNode dirNode, java.util.List<PlistFileNode> out) {
        if (dirNode == null) return;
        // 在当前层级找到第一个plist
        PlistFileNode first = null;
        for (int i = 0; i < dirNode.getChildCount(); i++) {
            Object uo = ((DefaultMutableTreeNode) dirNode.getChildAt(i)).getUserObject();
            if (uo instanceof PlistFileNode) {
                first = (PlistFileNode) uo;
                break;
            }
        }
        if (first != null) out.add(first);
        // 递归子目录
        for (int i = 0; i < dirNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) dirNode.getChildAt(i);
            if (!(child.getUserObject() instanceof PlistFileNode)) {
                collectFirstPlistPerDirectory(child, out);
            }
        }
    }
}