package com.criminaldb.view;

import com.criminaldb.config.DBConnection;
import com.criminaldb.util.AuditLogger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LoginFrame extends JFrame {

    private CardLayout authCardLayout;
    private JPanel authFormsContainer;
    private ShakingGlassCardPanel mainCard;

    // Login inputs
    private JTextField adminUserField;
    private JPasswordField adminPassField;
    private JTextField invUserField;
    private JPasswordField invPassField;

    // Status checker components
    private JTextField checkStatusIdField;
    private StatusBadgePanel statusBadge;
    private JTextArea statusMessageArea;

    // Sliding drawer components
    private JPanel slidingDrawer;
    private Timer slideTimer;
    private float animationProgress = 0f;
    private boolean isDrawerOpen = false;
    private final int TARGET_DRAWER_WIDTH = 460;

    // Registration inputs & Preview
    private JTextField regNameField;
    private JTextField regUserField;
    private JPasswordField regPassField;
    private JLabel selectedFilePathLabel;
    private JLabel imagePreviewLabel;
    private String selectedIdProofPath = "";

    // Tab buttons for global toggle
    private JButton adminTabBtn;
    private JButton invTabBtn;
    private JButton statusTabBtn;
    private int currentTab = 0;
    private float tabIndicatorX = 0f;
    private float targetTabIndicatorX = 0f;
    private Timer tabAnimationTimer;

    // Ambient Cyber Particles
    private final List<CyberParticle> particles = new ArrayList<>();
    private Timer particleTimer;
    private float radarAngle = 0f;

    // Success Animation Overlay
    private JLayeredPane layeredPane;
    private JPanel rootPanel;
    private SuccessOverlayPanel successOverlay;

    public LoginFrame() {
        setTitle("Criminal Database Management System - Gate Terminal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 750);
        setLocationRelativeTo(null);
        setResizable(false);

        initParticles();
        initUI();
        startAmbientAnimationLoop();
    }

    private void initParticles() {
        Random rand = new Random();
        for (int i = 0; i < 40; i++) {
            particles.add(new CyberParticle(
                    rand.nextInt(1080),
                    rand.nextInt(750),
                    rand.nextFloat() * 1.1f + 0.3f,
                    rand.nextFloat() * 0.6f + 0.1f,
                    rand.nextInt(3) + 2
            ));
        }
    }

    private void startAmbientAnimationLoop() {
        particleTimer = new Timer(25, e -> {
            for (CyberParticle p : particles) {
                p.y -= p.speed;
                if (p.y < -10) {
                    p.y = 760;
                    p.x = new Random().nextInt(1080);
                }
            }
            radarAngle += 0.015f;
            if (radarAngle > Math.PI * 2) radarAngle = 0f;
            rootPanel.repaint();
        });
        particleTimer.start();
    }

    private void initUI() {
        layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(1080, 750));

        rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint bgGradient = new GradientPaint(
                        0, 0, UITheme.BG_DARK,
                        getWidth(), getHeight(), new Color(17, 24, 39)
                );
                g2.setPaint(bgGradient);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Ambient Radial Light
                g2.setPaint(new RadialGradientPaint(getWidth() * 0.85f, getHeight() * 0.2f, 420f,
                        new float[]{0.0f, 1.0f},
                        new Color[]{new Color(56, 189, 248, 22), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Floating cyber particles
                for (CyberParticle p : particles) {
                    g2.setColor(new Color(56, 189, 248, (int) (p.alpha * 150)));
                    g2.fillOval((int) p.x, (int) p.y, p.size, p.size);
                }

                // Radar sweeps
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.setColor(new Color(56, 189, 248, 10));
                g2.drawOval(cx - 320, cy - 320, 640, 640);
                g2.drawOval(cx - 190, cy - 190, 380, 380);

                int rx = cx + (int) (Math.cos(radarAngle) * 320);
                int ry = cy + (int) (Math.sin(radarAngle) * 320);
                g2.drawLine(cx, cy, rx, ry);

                g2.dispose();
            }
        };
        rootPanel.setBounds(0, 0, 1080, 750);

        // Centered Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(28, 20, 6, 20));

        JLabel titleLabel = new JLabel("Criminal Database Management System", SwingConstants.CENTER);
        titleLabel.setFont(UITheme.TITLE_FONT);
        titleLabel.setForeground(UITheme.TEXT_WHITE);
        headerPanel.add(titleLabel);

        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // Center Area with Animated Shaking Glass Card
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        mainCard = new ShakingGlassCardPanel(20, UITheme.CARD_BG);
        mainCard.setPreferredSize(new Dimension(510, 490));
        mainCard.setLayout(new BorderLayout());
        mainCard.setBorder(new EmptyBorder(22, 28, 22, 28));

        // 3-Tab Switcher with sliding underline
        JPanel tabWrapper = createAnimatedTabBar();
        mainCard.add(tabWrapper, BorderLayout.NORTH);

        // Form Cards
        authCardLayout = new CardLayout();
        authFormsContainer = new JPanel(authCardLayout);
        authFormsContainer.setOpaque(false);
        authFormsContainer.setBorder(new EmptyBorder(18, 0, 0, 0));

        authFormsContainer.add(createAdminPanel(), "ADMIN");
        authFormsContainer.add(createInvestigatorPanel(), "INVESTIGATOR");
        authFormsContainer.add(createStatusCheckerPanel(), "STATUS");

        mainCard.add(authFormsContainer, BorderLayout.CENTER);
        centerWrapper.add(mainCard);

        rootPanel.add(centerWrapper, BorderLayout.CENTER);

        // Slide-Out Drawer
        slidingDrawer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(18, 24, 38, 252));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.BORDER_GLASS);
                g2.drawLine(0, 0, 0, getHeight());
                g2.dispose();
            }
        };
        slidingDrawer.setLayout(new BorderLayout());
        slidingDrawer.setOpaque(false);
        slidingDrawer.setPreferredSize(new Dimension(0, 750));

        buildRegisterDrawerContent();
        rootPanel.add(slidingDrawer, BorderLayout.EAST);

        // Success Overlay Layer
        successOverlay = new SuccessOverlayPanel();
        successOverlay.setBounds(0, 0, 1080, 750);
        successOverlay.setVisible(false);

        layeredPane.add(rootPanel, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(successOverlay, JLayeredPane.POPUP_LAYER);

        setContentPane(layeredPane);
    }

    private JPanel createAnimatedTabBar() {
        JPanel bar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(15, 23, 42));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(56, 189, 248, 50));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                int segWidth = getWidth() / 3;
                int indX = (int) tabIndicatorX;
                Color glowCol = (currentTab == 1) ? new Color(52, 211, 153) : UITheme.ACCENT_CYAN;
                g2.setColor(new Color(glowCol.getRed(), glowCol.getGreen(), glowCol.getBlue(), 55));
                g2.fillRoundRect(indX + 2, 2, segWidth - 4, getHeight() - 4, 8, 8);
                g2.setColor(glowCol);
                g2.fillRect(indX + 16, getHeight() - 3, segWidth - 32, 2);

                g2.dispose();
            }
        };
        bar.setLayout(new GridLayout(1, 3, 4, 0));
        bar.setPreferredSize(new Dimension(454, 38));
        bar.setOpaque(false);

        adminTabBtn = createTabButton("Admin Login", 0);
        invTabBtn = createTabButton("Investigator", 1);
        statusTabBtn = createTabButton("Check Status", 2);

        bar.add(adminTabBtn);
        bar.add(invTabBtn);
        bar.add(statusTabBtn);
        return bar;
    }

    private JButton createTabButton(String text, int index) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> switchTab(index));
        return btn;
    }

    private void switchTab(int index) {
        this.currentTab = index;
        int segWidth = 454 / 3;
        targetTabIndicatorX = index * segWidth;

        if (tabAnimationTimer != null && tabAnimationTimer.isRunning()) tabAnimationTimer.stop();

        tabAnimationTimer = new Timer(15, ev -> {
            float diff = targetTabIndicatorX - tabIndicatorX;
            tabIndicatorX += diff * 0.28f;
            mainCard.repaint();
            if (Math.abs(diff) < 0.8f) {
                tabIndicatorX = targetTabIndicatorX;
                tabAnimationTimer.stop();
                mainCard.repaint();
            }
        });
        tabAnimationTimer.start();

        clearLoginInputs();
        if (index == 0) {
            authCardLayout.show(authFormsContainer, "ADMIN");
            // Auto pre-fill admin credentials
            if (adminUserField != null) adminUserField.setText("admin");
            if (adminPassField != null) adminPassField.setText("admin123");
            if (isDrawerOpen) animateDrawer(false);
        } else if (index == 1) {
            authCardLayout.show(authFormsContainer, "INVESTIGATOR");
        } else {
            resetStatusCheckerFields();
            authCardLayout.show(authFormsContainer, "STATUS");
            if (isDrawerOpen) animateDrawer(false);
        }
    }

    private void clearLoginInputs() {
        if (invUserField != null) invUserField.setText("");
        if (invPassField != null) invPassField.setText("");
    }

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 0, 8));
        panel.setOpaque(false);

        JLabel userLbl = new JLabel("Admin ID");
        userLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userLbl.setForeground(UITheme.TEXT_WHITE);
        adminUserField = createReliableTextField();
        adminUserField.setText("admin"); // PRE-FILLED

        JLabel passLbl = new JLabel("Password");
        passLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        passLbl.setForeground(UITheme.TEXT_WHITE);
        adminPassField = createReliablePasswordField();
        adminPassField.setText("admin123"); // PRE-FILLED

        JButton loginBtn = createAccentButton("Authorize Admin Login");
        loginBtn.addActionListener(e -> authenticateUser(loginBtn, adminUserField.getText().trim(), new String(adminPassField.getPassword()), "ADMIN", adminUserField, adminPassField));

        panel.add(userLbl);
        panel.add(adminUserField);
        panel.add(passLbl);
        panel.add(adminPassField);
        panel.add(Box.createVerticalStrut(4));
        panel.add(loginBtn);

        return panel;
    }

    private JPanel createInvestigatorPanel() {
        JPanel panel = new JPanel(new GridLayout(8, 1, 0, 6));
        panel.setOpaque(false);

        JLabel userLbl = new JLabel("Investigator ID (Format: INV-XXXX)");
        userLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userLbl.setForeground(UITheme.TEXT_WHITE);
        invUserField = createReliableTextField();

        setupInvUsernameFilter(invUserField);

        JLabel passLbl = new JLabel("Password");
        passLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        passLbl.setForeground(UITheme.TEXT_WHITE);
        invPassField = createReliablePasswordField();

        JButton loginBtn = createAccentButton("Sign In");
        loginBtn.addActionListener(e -> authenticateUser(loginBtn, invUserField.getText().trim(), new String(invPassField.getPassword()), "INVESTIGATOR", invUserField, invPassField));

        JLabel createNewLbl = new JLabel("Create New Investigator →", SwingConstants.CENTER);
        createNewLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        createNewLbl.setForeground(UITheme.ACCENT_CYAN);
        createNewLbl.setCursor(new Cursor(Cursor.HAND_CURSOR));

        createNewLbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isDrawerOpen) {
                    resetDrawerFields();
                }
                animateDrawer(!isDrawerOpen);
            }
        });

        panel.add(userLbl);
        panel.add(invUserField);
        panel.add(passLbl);
        panel.add(invPassField);
        panel.add(Box.createVerticalStrut(4));
        panel.add(loginBtn);
        panel.add(Box.createVerticalStrut(2));
        panel.add(createNewLbl);

        return panel;
    }

    private JPanel createStatusCheckerPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        JLabel promptLbl = new JLabel("Enter Investigator ID (Format: INV-XXXX)");
        promptLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        promptLbl.setForeground(UITheme.TEXT_WHITE);
        promptLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(promptLbl);

        panel.add(Box.createVerticalStrut(8));

        checkStatusIdField = createReliableTextField();
        checkStatusIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        checkStatusIdField.setAlignmentX(Component.LEFT_ALIGNMENT);
        setupInvUsernameFilter(checkStatusIdField);
        panel.add(checkStatusIdField);

        panel.add(Box.createVerticalStrut(10));

        JButton verifyBtn = createAccentButton("Verify Approval Status");
        verifyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        verifyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        verifyBtn.addActionListener(e -> verifyApprovalStatus());
        panel.add(verifyBtn);

        panel.add(Box.createVerticalStrut(16));

        UITheme.GlassCardPanel resultCard = new UITheme.GlassCardPanel(14, new Color(13, 17, 23, 210));
        resultCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        resultCard.setPreferredSize(new Dimension(450, 170));
        resultCard.setLayout(new BorderLayout());
        resultCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        resultCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusBadge = new StatusBadgePanel();
        resultCard.add(statusBadge, BorderLayout.NORTH);

        statusMessageArea = new JTextArea("Enter your registered ID above and verify to check authorization clearance.");
        statusMessageArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusMessageArea.setForeground(UITheme.TEXT_MUTED);
        statusMessageArea.setOpaque(false);
        statusMessageArea.setEditable(false);
        statusMessageArea.setFocusable(false);
        if (statusMessageArea.getCaret() != null) {
            statusMessageArea.getCaret().setVisible(false);
        }
        statusMessageArea.setLineWrap(true);
        statusMessageArea.setWrapStyleWord(true);
        statusMessageArea.setBorder(new EmptyBorder(12, 0, 0, 0));
        resultCard.add(statusMessageArea, BorderLayout.CENTER);

        panel.add(resultCard);

        return panel;
    }

    private void resetStatusCheckerFields() {
        if (checkStatusIdField != null) {
            checkStatusIdField.setText("");
        }
        if (statusBadge != null) {
            statusBadge.updateBadge("AWAITING QUERY", new Color(148, 163, 184));
        }
        if (statusMessageArea != null) {
            statusMessageArea.setText("Enter your registered ID above and verify to check authorization clearance.");
            statusMessageArea.setForeground(UITheme.TEXT_MUTED);
        }
    }

    private void verifyApprovalStatus() {
        String idQuery = checkStatusIdField.getText().trim();

        if (idQuery.isEmpty() || !idQuery.matches("^INV-[A-Z0-9]{4}$")) {
            statusBadge.updateBadge("AWAITING QUERY", new Color(148, 163, 184));
            statusMessageArea.setText("Please enter your complete 4-character Investigator ID (e.g. INV-7777).");
            statusMessageArea.setForeground(UITheme.TEXT_MUTED);
            return;
        }

        String sql = "SELECT full_name, status, created_at FROM users WHERE (username = ? OR id_number = ?) AND role = 'INVESTIGATOR'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idQuery);
            ps.setString(2, idQuery);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String status = rs.getString("status");

                if ("ACTIVE".equalsIgnoreCase(status)) {
                    statusBadge.updateBadge("APPROVED", new Color(52, 211, 153));
                    statusMessageArea.setForeground(new Color(209, 250, 229));
                    statusMessageArea.setText("Investigator: " + fullName + "\n" +
                            "Dossier approved by Head Admin. Access credentials are fully enabled.\n" +
                            "You may now proceed to Investigator Login.");
                } else if ("PENDING".equalsIgnoreCase(status)) {
                    statusBadge.updateBadge("PENDING", new Color(251, 191, 36));
                    statusMessageArea.setForeground(new Color(254, 243, 199));
                    statusMessageArea.setText("Investigator: " + fullName + "\n" +
                            "Application and ID card proof are currently queued for Head Admin review.\n" +
                            "Please check back shortly.");
                } else {
                    statusBadge.updateBadge("NOT APPROVED", new Color(248, 113, 113));
                    statusMessageArea.setForeground(new Color(254, 202, 202));
                    statusMessageArea.setText("Investigator: " + fullName + "\n" +
                            "Application has been rejected or clearance revoked by Headquarters.\n" +
                            "Please contact system administration.");
                }

            } else {
                statusBadge.updateBadge("NOT FOUND", new Color(156, 163, 175));
                statusMessageArea.setForeground(UITheme.TEXT_MUTED);
                statusMessageArea.setText("No investigator record found with ID '" + idQuery + "'.\n" +
                        "If you haven't applied yet, click 'Create New Investigator' in Investigator Login.");
            }

        } catch (Exception ex) {
            statusBadge.updateBadge("ERROR", new Color(239, 68, 68));
            statusMessageArea.setForeground(new Color(254, 202, 202));
            statusMessageArea.setText("Database connection error: " + ex.getMessage());
        }
    }

    private void buildRegisterDrawerContent() {
        JPanel drawerHeader = new JPanel(new BorderLayout());
        drawerHeader.setOpaque(false);
        drawerHeader.setBorder(new EmptyBorder(25, 25, 10, 25));

        JLabel title = new JLabel("New Investigator");
        title.setFont(new Font("Segoe UI", Font.BOLD, 21));
        title.setForeground(UITheme.TEXT_WHITE);

        HoverCloseButton closeBtn = new HoverCloseButton();
        closeBtn.addActionListener(e -> animateDrawer(false));

        drawerHeader.add(title, BorderLayout.WEST);
        drawerHeader.add(closeBtn, BorderLayout.EAST);
        slidingDrawer.add(drawerHeader, BorderLayout.NORTH);

        JPanel formContainer = new JPanel(new GridBagLayout());
        formContainer.setOpaque(false);
        formContainer.setBorder(new EmptyBorder(10, 25, 20, 25));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.insets = new Insets(4, 0, 4, 0);

        // 1. Full Name
        gbc.gridy = 0;
        formContainer.add(createFieldLabel("Full Name"), gbc);

        gbc.gridy = 1;
        regNameField = createReliableTextField();
        formContainer.add(regNameField, gbc);

        // 2. Username (Strict 4 characters after INV-)
        gbc.gridy = 2;
        gbc.insets = new Insets(10, 0, 4, 0);
        formContainer.add(createFieldLabel("Investigator ID (Format: INV-XXXX)"), gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(4, 0, 4, 0);
        regUserField = createReliableTextField();
        setupInvUsernameFilter(regUserField);
        formContainer.add(regUserField, gbc);

        // 3. Password
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 0, 4, 0);
        formContainer.add(createFieldLabel("Password"), gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(4, 0, 4, 0);
        regPassField = createReliablePasswordField();
        formContainer.add(regPassField, gbc);

        // 4. ID Proof Upload Row
        gbc.gridy = 6;
        gbc.insets = new Insets(10, 0, 4, 0);
        formContainer.add(createFieldLabel("Investigator Official ID Proof"), gbc);

        gbc.gridy = 7;
        gbc.insets = new Insets(4, 0, 4, 0);
        JPanel uploadRow = new JPanel(new BorderLayout(10, 0));
        uploadRow.setOpaque(false);

        JButton uploadBtn = new JButton("Select ID Card");
        uploadBtn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        uploadBtn.setBackground(new Color(30, 41, 59));
        uploadBtn.setForeground(UITheme.TEXT_WHITE);
        uploadBtn.setFocusPainted(false);
        uploadBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        uploadBtn.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1, true));
        uploadBtn.setPreferredSize(new Dimension(130, 38));

        selectedFilePathLabel = new JLabel("No file selected");
        selectedFilePathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        selectedFilePathLabel.setForeground(UITheme.TEXT_MUTED);

        uploadBtn.addActionListener(e -> chooseIdProofFile());

        uploadRow.add(uploadBtn, BorderLayout.WEST);
        uploadRow.add(selectedFilePathLabel, BorderLayout.CENTER);
        formContainer.add(uploadRow, gbc);

        // Preview Card
        gbc.gridy = 8;
        gbc.insets = new Insets(10, 0, 10, 0);
        UITheme.GlassCardPanel previewCard = new UITheme.GlassCardPanel(10, new Color(13, 17, 23, 220));
        previewCard.setPreferredSize(new Dimension(380, 130));
        previewCard.setLayout(new GridBagLayout());

        imagePreviewLabel = new JLabel("ID Card Preview", SwingConstants.CENTER);
        imagePreviewLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        imagePreviewLabel.setForeground(UITheme.TEXT_MUTED);
        previewCard.add(imagePreviewLabel);
        formContainer.add(previewCard, gbc);

        // 5. Ask For Approval Button
        gbc.gridy = 9;
        gbc.insets = new Insets(15, 0, 0, 0);
        JButton approvalBtn = createAccentButton("Ask For Approval");
        approvalBtn.setPreferredSize(new Dimension(380, 44));
        approvalBtn.addActionListener(e -> handleAskForApproval());
        formContainer.add(approvalBtn, gbc);

        JScrollPane scrollPane = new JScrollPane(formContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        slidingDrawer.add(scrollPane, BorderLayout.CENTER);
    }

    private JTextField createReliableTextField() {
        JTextField tf = new JTextField() {
            private boolean isFocused = false;
            {
                addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { isFocused = true; repaint(); }
                    @Override public void focusLost(FocusEvent e) { isFocused = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isFocused) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color glow = (currentTab == 1) ? new Color(52, 211, 153) : UITheme.ACCENT_CYAN;
                    g2.setColor(glow);
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                }
            }
        };
        tf.setOpaque(true);
        tf.setBackground(new Color(25, 33, 48));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(UITheme.ACCENT_CYAN);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setPreferredSize(new Dimension(200, 38));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248, 70), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        return tf;
    }

    private JPasswordField createReliablePasswordField() {
        JPasswordField pf = new JPasswordField() {
            private boolean isFocused = false;
            {
                addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { isFocused = true; repaint(); }
                    @Override public void focusLost(FocusEvent e) { isFocused = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isFocused) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color glow = (currentTab == 1) ? new Color(52, 211, 153) : UITheme.ACCENT_CYAN;
                    g2.setColor(glow);
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                }
            }
        };
        pf.setOpaque(true);
        pf.setBackground(new Color(25, 33, 48));
        pf.setForeground(Color.WHITE);
        pf.setCaretColor(UITheme.ACCENT_CYAN);
        pf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        pf.setPreferredSize(new Dimension(200, 38));
        pf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248, 70), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        return pf;
    }

    // STRICT "INV-XXXX" FILTER (EXACTLY 4 DIGITS / CHARACTERS)
    private void setupInvUsernameFilter(JTextField field) {
        Runnable ensurePrefix = () -> {
            if (field.getText().isEmpty()) {
                field.setText("INV-");
                field.setCaretPosition(4);
            }
        };

        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (field.getText().isEmpty()) {
                    SwingUtilities.invokeLater(ensurePrefix);
                }
            }
        });

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    SwingUtilities.invokeLater(ensurePrefix);
                }
            }
        });

        PlainDocument doc = (PlainDocument) field.getDocument();
        doc.setDocumentFilter(new DocumentFilter() {
            private final String PREFIX = "INV-";
            private final int EXACT_POSTFIX_LEN = 4; // Exactly 4 characters!

            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String future = current.substring(0, offset) + (text != null ? text : "") + current.substring(offset + length);

                if (future.isEmpty()) {
                    super.replace(fb, offset, length, "", attrs);
                    return;
                }

                if (!future.startsWith(PREFIX)) {
                    return;
                }

                String postfix = future.substring(PREFIX.length()).replaceAll("[^A-Za-z0-9]", "");
                if (postfix.length() <= EXACT_POSTFIX_LEN) {
                    super.replace(fb, 0, fb.getDocument().getLength(), (PREFIX + postfix).toUpperCase(), attrs);
                }
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                if (current.equals("INV-") && offset >= 0) {
                    super.remove(fb, 0, current.length());
                    return;
                }
                if (offset < PREFIX.length()) {
                    return;
                }
                super.remove(fb, offset, length);
            }
        });
    }

    private void chooseIdProofFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Investigator ID Card");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("All Supported Formats (*.jpg, *.png, *.pdf)", "jpg", "jpeg", "png", "pdf"));

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            selectedIdProofPath = file.getAbsolutePath();
            selectedFilePathLabel.setText(file.getName());
            selectedFilePathLabel.setForeground(UITheme.ACCENT_CYAN);

            String nameLower = file.getName().toLowerCase();
            if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                try {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        Image scaled = img.getScaledInstance(200, 110, Image.SCALE_SMOOTH);
                        imagePreviewLabel.setText("");
                        imagePreviewLabel.setIcon(new ImageIcon(scaled));
                    }
                } catch (Exception ex) {
                    imagePreviewLabel.setIcon(null);
                    imagePreviewLabel.setText("Preview unavailable");
                }
            } else {
                imagePreviewLabel.setIcon(null);
                imagePreviewLabel.setText("PDF Document: " + file.getName());
            }
            slidingDrawer.revalidate();
            slidingDrawer.repaint();
        }
    }

    private void resetDrawerFields() {
        regNameField.setText("");
        regUserField.setText("");
        regPassField.setText("");
        selectedIdProofPath = "";
        selectedFilePathLabel.setText("No file selected");
        selectedFilePathLabel.setForeground(UITheme.TEXT_MUTED);
        imagePreviewLabel.setIcon(null);
        imagePreviewLabel.setText("ID Card Preview");
    }

    private void handleAskForApproval() {
        String name = regNameField.getText().trim();
        String username = regUserField.getText().trim();
        String pass = new String(regPassField.getPassword()).trim();

        // Enforce strictly 4 characters after INV- (Total length = 8)
        if (name.isEmpty() || !username.matches("^INV-[A-Z0-9]{4}$") || pass.isEmpty()) {
            GlassNotificationDialog.showError(
                    this,
                    "INCOMPLETE INFORMATION",
                    "Please fill in your Full Name, valid 4-character ID (INV-XXXX), and password."
            );
            return;
        }

        if (selectedIdProofPath.isEmpty()) {
            GlassNotificationDialog.showError(
                    this,
                    "ID PROOF MISSING",
                    "Official departmental ID document or image is mandatory for credential verification."
            );
            return;
        }

        String idNumber = username.substring(4);

        String sql = "INSERT INTO users (username, password, full_name, role, id_number, id_proof_path, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'INVESTIGATOR', ?, ?, 'PENDING', NOW(), NOW())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, pass);
            ps.setString(3, name);
            ps.setString(4, username);
            ps.setString(5, selectedIdProofPath);

            ps.executeUpdate();

            AuditLogger.log(username, "ACCESS CLEARANCE: Registered new application dossier [" + username + "] - Status: PENDING");

            GlassNotificationDialog.showSuccess(
                    this,
                    "APPLICATION SUBMITTED",
                    "Your dossier and ID credential have been logged into the terminal.\n\n" +
                            "ID Number: [" + username + "]\n" +
                            "Status: [PENDING REVIEW]\n\n" +
                            "Access privileges will be granted upon Head Admin validation."
            );

            resetDrawerFields();
            animateDrawer(false);

        } catch (SQLIntegrityConstraintViolationException ex) {
            GlassNotificationDialog.showError(
                    this,
                    "REGISTRATION FAILED",
                    "This ID is already exist."
            );
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("duplicate entry")) {
                GlassNotificationDialog.showError(
                        this,
                        "REGISTRATION FAILED",
                        "This ID is already exist."
                );
            } else {
                GlassNotificationDialog.showError(
                        this,
                        "REGISTRATION FAILED",
                        "Database error: " + ex.getMessage()
                );
            }
        }
    }

    private void authenticateUser(JButton sourceBtn, String username, String password, String expectedRole, JTextField userFieldToClear, JPasswordField passFieldToClear) {
        if (username.isEmpty() || password.isEmpty() || "INV-".equalsIgnoreCase(username)) {
            triggerWrongCredentialsAnimation(sourceBtn, userFieldToClear, passFieldToClear, "MISSING CREDENTIALS", "Please enter both your assigned ID / Username and Password to proceed.");
            return;
        }

        // Validate 4 characters strictly for investigator login
        if ("INVESTIGATOR".equalsIgnoreCase(expectedRole) && !username.matches("^INV-[A-Z0-9]{4}$")) {
            triggerWrongCredentialsAnimation(sourceBtn, userFieldToClear, passFieldToClear, "INVALID ID FORMAT", "Investigator ID must be strictly 'INV-' followed by 4 characters (e.g. INV-7777).");
            return;
        }

        String query = "SELECT full_name, role, status, username, id_number FROM users WHERE (username = ? OR id_number = ?) AND password = ? AND role = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, username);
            ps.setString(2, username);
            ps.setString(3, password);
            ps.setString(4, expectedRole);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String status = rs.getString("status");
                String actualUsername = rs.getString("username");
                String badgeNumber = rs.getString("id_number");

                if ("PENDING".equalsIgnoreCase(status)) {
                    triggerWrongCredentialsAnimation(sourceBtn, userFieldToClear, passFieldToClear, "ACCOUNT PENDING", "Your account is currently under review by Head Admin.\nCheck the 'Check Status' tab for updates.");
                    return;
                } else if ("REJECTED".equalsIgnoreCase(status)) {
                    triggerWrongCredentialsAnimation(sourceBtn, userFieldToClear, passFieldToClear, "CLEARANCE REVOKED", "Your credentials have been rejected or revoked by Headquarters.");
                    return;
                }

                AuditLogger.log(actualUsername, "SECURITY AUTH: Verified clearance and successfully logged into terminal [" + expectedRole + "].");

                // Route through slot machine splash screen for Investigator, or standard sequence for Admin
                if ("INVESTIGATOR".equalsIgnoreCase(expectedRole)) {
                    if (particleTimer != null) particleTimer.stop();
                    dispose();
                    SwingUtilities.invokeLater(() -> {
                        new InvestigatorSlotSplash(actualUsername, badgeNumber != null ? badgeNumber : actualUsername).setVisible(true);
                    });
                } else {
                    playSuccessAuthorizationSequence(sourceBtn, fullName != null ? fullName : actualUsername, actualUsername, expectedRole);
                }

            } else {
                triggerWrongCredentialsAnimation(
                        sourceBtn,
                        userFieldToClear,
                        passFieldToClear,
                        "AUTHENTICATION FAILED",
                        "Invalid ID / Username or Password.\nAccess denied. Please check your credentials or verify clearance."
                );
            }
        } catch (Exception ex) {
            triggerWrongCredentialsAnimation(sourceBtn, userFieldToClear, passFieldToClear, "SYSTEM ERROR", "Connection failed: " + ex.getMessage());
        }
    }

    private void playSuccessAuthorizationSequence(JButton button, String displayName, String username, String role) {
        button.setEnabled(false);
        button.setText("AUTHORIZING CLEARANCE...");
        button.setBackground(new Color(16, 185, 129));
        button.setForeground(new Color(13, 17, 23));

        successOverlay.startSequence(displayName, role, () -> {
            SwingUtilities.invokeLater(() -> {
                if (particleTimer != null) particleTimer.stop();
                dispose();
                new AdminDashboardFrame(displayName).setVisible(true);
            });
        });
    }

    private void triggerWrongCredentialsAnimation(JButton sourceBtn, JTextField userField, JPasswordField passField, String title, String message) {
        String originalText = sourceBtn.getText();
        Color originalBg = sourceBtn.getBackground();
        sourceBtn.setBackground(new Color(239, 68, 68));
        sourceBtn.setForeground(Color.WHITE);
        sourceBtn.setText("ACCESS DENIED // 401");

        mainCard.shakeCard(() -> {
            sourceBtn.setBackground(originalBg);
            sourceBtn.setForeground(UITheme.BG_DARK);
            sourceBtn.setText(originalText);

            if (userField != null) {
                userField.setText("");
                userField.requestFocusInWindow();
            }
            if (passField != null) {
                passField.setText("");
            }

            GlassNotificationDialog.showError(LoginFrame.this, title, message);
        });
    }

    private void animateDrawer(boolean open) {
        if (slideTimer != null && slideTimer.isRunning()) {
            slideTimer.stop();
        }

        final int duration = 280;
        final int interval = 16;
        final long startTime = System.currentTimeMillis();
        final float startProg = animationProgress;
        final float targetProg = open ? 1.0f : 0.0f;

        slideTimer = new Timer(interval, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float t = Math.min(1.0f, (float) elapsed / duration);

            float eased = 1.0f - (float) Math.pow(1.0f - t, 3);
            animationProgress = startProg + (targetProg - startProg) * eased;

            int currentWidth = Math.round(TARGET_DRAWER_WIDTH * animationProgress);
            slidingDrawer.setPreferredSize(new Dimension(currentWidth, getHeight()));
            slidingDrawer.revalidate();
            slidingDrawer.repaint();

            if (t >= 1.0f) {
                animationProgress = targetProg;
                isDrawerOpen = open;
                if (!isDrawerOpen) {
                    resetDrawerFields();
                }
                ((Timer) e.getSource()).stop();
            }
        });

        slideTimer.start();
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(UITheme.TEXT_MUTED);
        return lbl;
    }

    private JButton createAccentButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(UITheme.ACCENT_CYAN);
        btn.setForeground(UITheme.BG_DARK);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(UITheme.ACCENT_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(UITheme.ACCENT_CYAN);
                }
            }
        });

        return btn;
    }

    public static class ShakingGlassCardPanel extends JPanel {
        private final int radius;
        private final Color bgColor;
        private int shakeOffsetX = 0;
        private float alertAlpha = 0f;
        private Timer shakeTimer;

        public ShakingGlassCardPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        public void shakeCard(Runnable onComplete) {
            if (shakeTimer != null && shakeTimer.isRunning()) {
                shakeTimer.stop();
            }

            final long startTime = System.currentTimeMillis();
            final int duration = 500;
            alertAlpha = 1.0f;

            shakeTimer = new Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = (float) elapsed / duration;

                if (progress >= 1.0f) {
                    shakeOffsetX = 0;
                    alertAlpha = 0f;
                    repaint();
                    ((Timer) e.getSource()).stop();
                    if (onComplete != null) onComplete.run();
                } else {
                    double amplitude = 14.0 * (1.0 - progress);
                    shakeOffsetX = (int) (amplitude * Math.sin(progress * Math.PI * 9));
                    alertAlpha = Math.max(0f, 1.0f - progress);
                    repaint();
                }
            });
            shakeTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.translate(shakeOffsetX, 0);

            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            if (alertAlpha > 0.01f) {
                g2.setColor(new Color(239, 68, 68, (int) (alertAlpha * 220)));
                g2.setStroke(new BasicStroke(2.2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius, radius);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class StatusBadgePanel extends JPanel {
        private String badgeText = "STATUS IDLE";
        private Color badgeColor = UITheme.TEXT_MUTED;

        public StatusBadgePanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(170, 34));
        }

        public void updateBadge(String text, Color color) {
            this.badgeText = text;
            this.badgeColor = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Font font = new Font("Segoe UI", Font.BOLD, 13);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int strW = fm.stringWidth(badgeText);
            int boxW = strW + 30;
            int boxH = 28;

            g2.setColor(new Color(badgeColor.getRed(), badgeColor.getGreen(), badgeColor.getBlue(), 35));
            g2.fillRoundRect(0, 3, boxW, boxH, 14, 14);

            g2.setColor(new Color(badgeColor.getRed(), badgeColor.getGreen(), badgeColor.getBlue(), 160));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 3, boxW, boxH, 14, 14);

            g2.setColor(badgeColor);
            g2.fillOval(9, 12, 8, 8);

            g2.drawString(badgeText, 24, 21);
            g2.dispose();
        }
    }

    private static class HoverCloseButton extends JButton {
        private boolean isHovered = false;

        public HoverCloseButton() {
            setPreferredSize(new Dimension(32, 32));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isHovered) {
                g2.setColor(new Color(56, 189, 248, 40));
                g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);
                g2.setColor(UITheme.ACCENT_CYAN);
            } else {
                g2.setColor(UITheme.TEXT_MUTED);
            }

            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = 10;
            g2.drawLine(pad, pad, getWidth() - pad, getHeight() - pad);
            g2.drawLine(getWidth() - pad, pad, pad, getHeight() - pad);
            g2.dispose();
        }
    }

    private static class SuccessOverlayPanel extends JPanel {
        private float scanAngle = 0f;
        private float pulseRadius = 0f;
        private boolean scanComplete = false;
        private String targetName = "";
        private String targetRole = "";
        private Timer animationTimer;

        public SuccessOverlayPanel() {
            setOpaque(false);
            setLayout(null);
        }

        public void startSequence(String name, String role, Runnable onComplete) {
            this.targetName = name;
            this.targetRole = role;
            this.scanAngle = 0f;
            this.pulseRadius = 0f;
            this.scanComplete = false;
            setVisible(true);

            long startTime = System.currentTimeMillis();

            animationTimer = new Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                scanAngle += 14f;
                pulseRadius += 3.5f;

                if (elapsed >= 900) {
                    scanComplete = true;
                }

                repaint();

                if (elapsed >= 1800) {
                    animationTimer.stop();
                    onComplete.run();
                }
            });
            animationTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(11, 15, 23, 210));
            g2.fillRect(0, 0, getWidth(), getHeight());

            int cx = getWidth() / 2;
            int cy = getHeight() / 2 - 20;

            g2.setColor(new Color(56, 189, 248, Math.max(0, 160 - (int) (pulseRadius * 0.9f))));
            g2.setStroke(new BasicStroke(2.0f));
            int r = (int) pulseRadius % 180;
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            int cardW = 440;
            int cardH = 220;
            g2.setColor(new Color(17, 24, 39, 245));
            g2.fillRoundRect(cx - cardW / 2, cy - cardH / 2, cardW, cardH, 22, 22);

            g2.setColor(new Color(16, 185, 129, 180));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawRoundRect(cx - cardW / 2, cy - cardH / 2, cardW, cardH, 22, 22);

            int iconY = cy - 45;
            if (!scanComplete) {
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(16, 185, 129, 60));
                g2.drawOval(cx - 24, iconY - 24, 48, 48);

                g2.setColor(new Color(52, 211, 153));
                g2.drawArc(cx - 24, iconY - 24, 48, 48, (int) scanAngle, 110);
            } else {
                g2.setColor(new Color(16, 185, 129, 45));
                g2.fillOval(cx - 24, iconY - 24, 48, 48);

                g2.setColor(new Color(52, 211, 153));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawOval(cx - 24, iconY - 24, 48, 48);

                g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int[] xPoints = {cx - 10, cx - 3, cx + 11};
                int[] yPoints = {iconY - 1, iconY + 7, iconY - 7};
                g2.drawPolyline(xPoints, yPoints, 3);
            }

            g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
            g2.setColor(new Color(52, 211, 153));
            String statusHeader = scanComplete ? "CLEARANCE VERIFIED" : "AUTHENTICATING SESSION...";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(statusHeader, cx - fm.stringWidth(statusHeader) / 2, cy + 28);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g2.setColor(new Color(241, 245, 249));
            String line1 = targetName + " • [" + targetRole + "]";
            FontMetrics fm2 = g2.getFontMetrics();
            g2.drawString(line1, cx - fm2.stringWidth(line1) / 2, cy + 54);

            g2.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            g2.setColor(new Color(148, 163, 184));
            String line2 = "Initializing secure terminal protocols...";
            FontMetrics fm3 = g2.getFontMetrics();
            g2.drawString(line2, cx - fm3.stringWidth(line2) / 2, cy + 74);

            g2.dispose();
        }
    }

    public static class GlassNotificationDialog extends JDialog {

        public static void showSuccess(JFrame parent, String title, String message) {
            GlassNotificationDialog dialog = new GlassNotificationDialog(parent, title, message, false);
            dialog.setVisible(true);
        }

        public static void showError(JFrame parent, String title, String message) {
            GlassNotificationDialog dialog = new GlassNotificationDialog(parent, title, message, true);
            dialog.setVisible(true);
        }

        private GlassNotificationDialog(JFrame parent, String title, String message, boolean isError) {
            super(parent, true);
            setUndecorated(true);
            setSize(490, 310);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            final Color primaryColor = isError ? new Color(239, 68, 68) : new Color(16, 185, 129);
            final Color glowColor = isError ? new Color(248, 113, 113) : new Color(52, 211, 153);
            final Color textColor = isError ? new Color(254, 226, 226) : new Color(220, 252, 231);
            final String buttonText = isError ? "DISMISS" : "ACKNOWLEDGE";

            JPanel contentCard = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.setColor(new Color(15, 23, 42, 248));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                    g2.setColor(new Color(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), 160));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                    g2.dispose();
                }
            };
            contentCard.setOpaque(false);
            contentCard.setBorder(new EmptyBorder(26, 28, 24, 28));

            JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
            topRow.setOpaque(false);

            JComponent vectorBadge = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.setColor(new Color(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), 45));
                    g2.fillOval(0, 0, getWidth(), getHeight());

                    g2.setColor(glowColor);
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);

                    g2.setColor(glowColor);

                    if (isError) {
                        g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(19, 11, 19, 21);
                        g2.fillOval(17, 26, 4, 4);
                    } else {
                        g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int[] xPoints = {11, 16, 27};
                        int[] yPoints = {19, 25, 13};
                        g2.drawPolyline(xPoints, yPoints, 3);
                    }

                    g2.dispose();
                }
            };
            vectorBadge.setPreferredSize(new Dimension(38, 38));

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
            titleLbl.setForeground(glowColor);

            topRow.add(vectorBadge);
            topRow.add(titleLbl);
            contentCard.add(topRow, BorderLayout.NORTH);

            JTextArea msgArea = new JTextArea(message);
            msgArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            msgArea.setForeground(textColor);
            msgArea.setOpaque(false);
            msgArea.setEditable(false);
            msgArea.setFocusable(false);
            if (msgArea.getCaret() != null) {
                msgArea.getCaret().setVisible(false);
            }
            msgArea.setLineWrap(true);
            msgArea.setWrapStyleWord(true);
            msgArea.setBorder(new EmptyBorder(22, 6, 14, 6));
            contentCard.add(msgArea, BorderLayout.CENTER);

            JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottomRow.setOpaque(false);

            JButton actionBtn = new JButton(buttonText);
            actionBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            actionBtn.setBackground(primaryColor);
            actionBtn.setForeground(new Color(15, 23, 42));
            actionBtn.setFocusPainted(false);
            actionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            actionBtn.setBorder(new EmptyBorder(10, 24, 10, 24));

            actionBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    actionBtn.setBackground(glowColor);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    actionBtn.setBackground(primaryColor);
                }
            });

            actionBtn.addActionListener(e -> dispose());
            bottomRow.add(actionBtn);
            contentCard.add(bottomRow, BorderLayout.SOUTH);

            setContentPane(contentCard);
            SwingUtilities.invokeLater(actionBtn::requestFocusInWindow);
        }
    }

    private static class CyberParticle {
        float x, y, speed, alpha;
        int size;

        CyberParticle(float x, float y, float speed, float alpha, int size) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.alpha = alpha;
            this.size = size;
        }
    }
}