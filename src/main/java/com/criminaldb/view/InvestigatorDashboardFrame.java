package com.criminaldb.view;

import com.criminaldb.config.DBConnection;
import com.criminaldb.util.AuditLogger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvestigatorDashboardFrame extends JFrame {

    private final String investigatorUsername;
    private String investigatorFullName = "Field Officer";
    private String investigatorBadgeId = "INV-0000";

    private JLabel welcomeLbl;
    private JLabel myCasesCountVal;
    private JLabel pendingReqCountVal;
    private JLabel availablePoolCountVal;
    private JLabel completedCasesCountVal;

    private CardLayout workspaceCardLayout;
    private JPanel workspaceContainer;

    private JPanel slidingSidebar;
    private Timer drawerTimer;
    private float drawerProgress = 0f;
    private boolean isDrawerOpen = false;
    private final int DRAWER_TARGET_WIDTH = 250;
    private MenuVectorButton menuToggleBtn;

    private final List<ZoomSidebarItem> sidebarItems = new ArrayList<>();

    // Persistent Notification Engine
    private VectorBellIcon notifBell;
    private final List<InvestigatorNotificationsPopup.PopupItem> currentNotifications = Collections.synchronizedList(new ArrayList<>());
    private Timer pollingTimer;

    // Workspaces
    private JPanel dashboardWorkspace;
    private JPanel availableCasesWorkspace;
    private JPanel myRequestsWorkspace;
    private JPanel myCasesWorkspace;
    private JPanel faceRecWorkspace;
    private JPanel profileWorkspace;

    // Flagged Warning Banner in My Cases
    private JPanel flaggedAlertBanner;
    private JLabel flaggedAlertLbl;

    // Available Pool Buttons and State
    private JButton claimBtn;
    private JButton viewAvailableCaseBtn;
    private JTable availableTable;
    private DefaultTableModel availableTableModel;
    private final List<String> poolCaseRequesters = new ArrayList<>();
    private final List<String> poolCaseRejectedList = new ArrayList<>();

    // My Cases Table and Components
    private DefaultTableModel myRequestsTableModel;
    private JTable myRequestsTable;
    private JTable myCasesTable;
    private DefaultTableModel myCasesTableModel;
    private DefaultTableModel dashboardDocketTableModel;

    private AnimatedRequestBadge animatedReqBadge;

    // Persistent Biometric Face Recognition State
    private File selectedFaceQueryFile = null;
    private BufferedImage loadedQueryBufferedImage = null;
    private InPlaceLaserScanViewport faceScanViewport = null;
    private VerticalConfidenceBar verticalConfidenceBar = null;
    private JButton scanBtn = null;
    private JButton exportPdfReportBtn = null;
    private JLabel resultStatusLbl = null;

    private String lastMatchedSuspectName = "N/A";
    private String lastMatchedCaseNum = "N/A";
    private double lastMatchConfidence = 0.0;
    private String lastBboxStr = "x=0, y=0, w=0, h=0";
    private double lastDetConf = 98.5;

    public InvestigatorDashboardFrame(String username) {
        this.investigatorUsername = username;

        setTitle("Criminal Database Management System - Investigator Field Terminal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 750);
        setResizable(false);
        setLocationRelativeTo(null);

        fetchOfficerMetadata();
        initUI();
        loadDashboardMetrics();

        fetchInvestigatorNotifications();
        pollingTimer = new Timer(4000, e -> fetchInvestigatorNotifications());
        pollingTimer.start();
    }

    private void fetchOfficerMetadata() {
        String sql = "SELECT full_name, id_number FROM users WHERE username = ? LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, investigatorUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String fn = rs.getString("full_name");
                    String id = rs.getString("id_number");
                    if (fn != null && !fn.trim().isEmpty()) investigatorFullName = fn;
                    if (id != null && !id.trim().isEmpty()) investigatorBadgeId = id;
                }
            }
        } catch (Exception ex) {
            System.err.println("Officer metadata load error: " + ex.getMessage());
        }
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint bg = new GradientPaint(0, 0, UITheme.BG_DARK, getWidth(), getHeight(), new Color(17, 24, 39));
                g2.setPaint(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };

        root.add(createTopBar(), BorderLayout.NORTH);

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);

        slidingSidebar = createSlidingSidebar();
        centerContainer.add(slidingSidebar, BorderLayout.WEST);

        workspaceCardLayout = new CardLayout();
        workspaceContainer = new JPanel(workspaceCardLayout);
        workspaceContainer.setOpaque(false);

        dashboardWorkspace = createDashboardWorkspace();
        availableCasesWorkspace = createAvailableCasesWorkspace();
        myRequestsWorkspace = createMyRequestsWorkspace();
        myCasesWorkspace = createMyCasesWorkspace();
        faceRecWorkspace = createFaceRecognitionWorkspace();
        profileWorkspace = createProfileWorkspace();

        workspaceContainer.add(dashboardWorkspace, "Dashboard");
        workspaceContainer.add(availableCasesWorkspace, "Available Cases");
        workspaceContainer.add(myRequestsWorkspace, "My Case Requests");
        workspaceContainer.add(myCasesWorkspace, "My Cases");
        workspaceContainer.add(faceRecWorkspace, "Face Recognition");
        workspaceContainer.add(profileWorkspace, "Profile");

        centerContainer.add(workspaceContainer, BorderLayout.CENTER);
        root.add(centerContainer, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JPanel createTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(1080, 58));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_GLASS),
                new EmptyBorder(0, 18, 0, 20)
        ));

        JPanel leftArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        leftArea.setOpaque(false);

        menuToggleBtn = new MenuVectorButton();
        menuToggleBtn.addActionListener(e -> toggleDrawer(!isDrawerOpen));

        JLabel title = new JLabel("Criminal Database Management System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(UITheme.TEXT_WHITE);

        leftArea.add(menuToggleBtn);
        leftArea.add(title);
        bar.add(leftArea, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 13));
        rightActions.setOpaque(false);

        notifBell = new VectorBellIcon();
        notifBell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openNotificationsDialog();
            }
        });

        JLabel officerPill = new JLabel("  " + investigatorFullName + " [" + investigatorBadgeId + "] [FIELD INVESTIGATOR]  ");
        officerPill.setFont(new Font("Segoe UI", Font.BOLD, 11));
        officerPill.setForeground(new Color(52, 211, 153));
        officerPill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 211, 153, 100), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));

        rightActions.add(officerPill);
        rightActions.add(notifBell);
        bar.add(rightActions, BorderLayout.EAST);
        return bar;
    }

    private void openNotificationsDialog() {
        List<InvestigatorNotificationsPopup.PopupItem> toShow;
        synchronized (currentNotifications) {
            toShow = new ArrayList<>(currentNotifications);
        }

        new Thread(() -> {
            String sql = "UPDATE users SET last_notifications_read_at = NOW() WHERE username = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, investigatorUsername);
                ps.executeUpdate();
            } catch (Exception ex) {
                System.err.println("Failed to mark notifications read: " + ex.getMessage());
            }
        }).start();

        if (notifBell != null) notifBell.setUnreadCount(0);

        Point bellLocation = null;
        if (notifBell != null && notifBell.isShowing()) {
            bellLocation = notifBell.getLocationOnScreen();
        }

        InvestigatorNotificationsPopup popup = new InvestigatorNotificationsPopup(
                this,
                toShow,
                bellLocation,
                this::switchWorkspaceView
        );
        popup.setVisible(true);
    }

    private void fetchInvestigatorNotifications() {
        new Thread(() -> {
            List<InvestigatorNotificationsPopup.PopupItem> freshList = new ArrayList<>();

            try (Connection conn = DBConnection.getConnection()) {
                if (conn != null) {
                    Timestamp lastReadTime = null;
                    String readTimeSql = "SELECT last_notifications_read_at FROM users WHERE username = ?";
                    try (PreparedStatement ps = conn.prepareStatement(readTimeSql)) {
                        ps.setString(1, investigatorUsername);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) lastReadTime = rs.getTimestamp("last_notifications_read_at");
                        }
                    }

                    Timestamp filterTimestamp = (lastReadTime != null) ? lastReadTime : Timestamp.valueOf("1970-01-01 00:00:00");

                    // 1. Evidence Review Flags
                    String evSql = "SELECT e.evidence_number, e.status, COALESCE(c.case_number, e.case_number) AS c_num, e.notes, e.created_at FROM case_evidence e " +
                            "LEFT JOIN cases c ON e.case_id = c.id " +
                            "WHERE (e.collected_by = ? OR e.collected_by = ?) " +
                            "  AND UPPER(e.status) = 'FLAGGED' " +
                            "  AND e.created_at > ? ORDER BY e.id DESC LIMIT 4";
                    try (PreparedStatement ps = conn.prepareStatement(evSql)) {
                        ps.setString(1, investigatorUsername);
                        ps.setString(2, investigatorFullName);
                        ps.setTimestamp(3, filterTimestamp);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String reason = rs.getString("notes");
                                if (reason != null && reason.contains("[ADMIN FLAGGED]:")) {
                                    reason = reason.substring(reason.indexOf("[ADMIN FLAGGED]:") + 16).trim();
                                }
                                freshList.add(new InvestigatorNotificationsPopup.PopupItem(
                                        InvestigatorNotificationsPopup.NotificationType.CASE_REQUEST,
                                        "⚠️ Evidence Discrepancy Flagged: " + rs.getString("c_num"),
                                        (reason != null && !reason.isEmpty()) ? reason : "Forensic discrepancy flagged on " + rs.getString("evidence_number"),
                                        rs.getString("created_at"),
                                        rs.getString("c_num")
                                ));
                            }
                        }
                    }

                    // 2. New Pool Cases
                    String poolSql = "SELECT case_number, title, created_at FROM cases " +
                            "WHERE UPPER(status) IN ('PENDING', 'CLAIM_PENDING') " +
                            "  AND (assigned_officer IS NULL OR assigned_officer = '' OR assigned_officer = 'Unassigned') " +
                            "  AND created_at > ? ORDER BY id DESC LIMIT 4";
                    try (PreparedStatement ps = conn.prepareStatement(poolSql)) {
                        ps.setTimestamp(1, filterTimestamp);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                freshList.add(new InvestigatorNotificationsPopup.PopupItem(
                                        InvestigatorNotificationsPopup.NotificationType.CASE_REQUEST,
                                        "New Pool Incident: " + rs.getString("case_number"),
                                        "Available for claim: " + rs.getString("title"),
                                        rs.getString("created_at"),
                                        rs.getString("case_number")
                                ));
                            }
                        }
                    }

                    // 3. Case Approvals & Closures
                    String reqSql = "SELECT case_number, title, status, updated_at FROM cases " +
                            "WHERE (created_by = ? OR created_by = ?) OR (assigned_officer = ? OR assigned_officer = ? OR assigned_officer LIKE ?) " +
                            "  AND UPPER(status) IN ('ACTIVE', 'REJECTED', 'CLOSED') " +
                            "  AND updated_at > ? ORDER BY updated_at DESC LIMIT 6";
                    try (PreparedStatement ps = conn.prepareStatement(reqSql)) {
                        ps.setString(1, investigatorUsername);
                        ps.setString(2, investigatorFullName);
                        ps.setString(3, investigatorUsername);
                        ps.setString(4, investigatorFullName);
                        ps.setString(5, "%" + investigatorFullName + "%");
                        ps.setTimestamp(6, filterTimestamp);

                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String num = rs.getString("case_number");
                                String title = rs.getString("title");
                                String st = rs.getString("status").toUpperCase();
                                String time = rs.getString("updated_at") != null ? rs.getString("updated_at") : "Recent";

                                String header;
                                if ("CLOSED".equals(st)) header = "Case Solved & Closed: " + num;
                                else if ("ACTIVE".equals(st)) header = "Case Approved & Dispatched: " + num;
                                else header = "Case Request Rejected: " + num;

                                freshList.add(new InvestigatorNotificationsPopup.PopupItem(
                                        InvestigatorNotificationsPopup.NotificationType.CASE_REQUEST,
                                        header,
                                        "Docket " + num + ": " + title,
                                        time,
                                        num
                                ));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Investigator poll error: " + ex.getMessage());
            }

            final int unreadCount = freshList.size();
            SwingUtilities.invokeLater(() -> {
                synchronized (currentNotifications) {
                    currentNotifications.clear();
                    currentNotifications.addAll(freshList);
                }
                if (notifBell != null) notifBell.setUnreadCount(unreadCount);
            });
        }).start();
    }

    private JPanel createSlidingSidebar() {
        JPanel sidebar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(15, 20, 31, 252));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UITheme.BORDER_GLASS);
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.dispose();
            }
        };
        sidebar.setLayout(new BorderLayout());
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(0, 692));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 16, 12, 14));

        JLabel navTitle = new JLabel("INVESTIGATOR CONSOLE");
        navTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        navTitle.setForeground(new Color(148, 163, 184));

        VectorCloseButton closeBtn = new VectorCloseButton();
        closeBtn.addActionListener(e -> toggleDrawer(false));

        header.add(navTitle, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        sidebar.add(header, BorderLayout.NORTH);

        JPanel itemsContainer = new JPanel();
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
        itemsContainer.setOpaque(false);
        itemsContainer.setBorder(new EmptyBorder(6, 8, 14, 8));

        // Settings removed
        String[] menuItems = {
                "Dashboard", "Available Cases", "My Case Requests",
                "My Cases", "Face Recognition", "Profile", "Log out"
        };

        for (String item : menuItems) {
            boolean isDanger = "Log out".equals(item);
            ZoomSidebarItem navBtn = new ZoomSidebarItem(item, item.equals("Dashboard"), isDanger);
            navBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    switchWorkspaceView(item);
                    toggleDrawer(false);
                }
            });
            sidebarItems.add(navBtn);
            itemsContainer.add(navBtn);
            itemsContainer.add(Box.createVerticalStrut(4));
        }

        sidebar.add(itemsContainer, BorderLayout.CENTER);
        return sidebar;
    }

    private void switchWorkspaceView(String viewName) {
        if ("Log out".equalsIgnoreCase(viewName)) {
            boolean confirmed = InvestigatorsPanel.GlassConfirmDialog.confirm(
                    this,
                    "TERMINATE SESSION",
                    "Are you sure you want to log out of the Field Investigator Terminal?"
            );
            if (confirmed) {
                if (pollingTimer != null) pollingTimer.stop();
                AuditLogger.log(investigatorUsername, "SECURITY SESSION: Investigator terminated field session.");
                dispose();
                new LoginFrame().setVisible(true);
            }
            return;
        }

        for (ZoomSidebarItem it : sidebarItems) {
            it.setSelected(it.getTitle().equalsIgnoreCase(viewName));
        }

        if ("Dashboard".equalsIgnoreCase(viewName)) loadDashboardMetrics();
        else if ("Available Cases".equalsIgnoreCase(viewName)) loadAvailableCasesTable();
        else if ("My Case Requests".equalsIgnoreCase(viewName)) loadMyCaseRequestsTable();
        else if ("My Cases".equalsIgnoreCase(viewName)) loadMyCasesTable();

        workspaceCardLayout.show(workspaceContainer, viewName);
    }

    private void toggleDrawer(boolean open) {
        if (drawerTimer != null && drawerTimer.isRunning()) drawerTimer.stop();

        final int duration = 220;
        final long startTime = System.currentTimeMillis();
        final float startProg = drawerProgress;
        final float targetProg = open ? 1.0f : 0.0f;

        drawerTimer = new Timer(15, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            float t = Math.min(1.0f, (float) elapsed / duration);
            float eased = 1.0f - (float) Math.pow(1.0f - t, 3);
            drawerProgress = startProg + (targetProg - startProg) * eased;

            int currentWidth = Math.round(DRAWER_TARGET_WIDTH * drawerProgress);
            slidingSidebar.setPreferredSize(new Dimension(currentWidth, getHeight()));
            slidingSidebar.revalidate();
            slidingSidebar.repaint();

            if (t >= 1.0f) {
                drawerProgress = targetProg;
                isDrawerOpen = open;
                menuToggleBtn.setOpenState(isDrawerOpen);
                ((Timer) e.getSource()).stop();
            }
        });
        drawerTimer.start();
    }

    // =========================================================================
    // 1. DASHBOARD OVERVIEW
    // =========================================================================
    private JPanel createDashboardWorkspace() {
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 22, 14, 22));

        welcomeLbl = new JLabel("Welcome back, Detective " + investigatorFullName);
        welcomeLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        welcomeLbl.setForeground(Color.WHITE);
        p.add(welcomeLbl, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        JPanel statsRow = new JPanel(new GridLayout(1, 4, 12, 0));
        statsRow.setOpaque(false);
        statsRow.setPreferredSize(new Dimension(0, 95));

        myCasesCountVal = new JLabel("0", SwingConstants.CENTER);
        pendingReqCountVal = new JLabel("0", SwingConstants.CENTER);
        availablePoolCountVal = new JLabel("0", SwingConstants.CENTER);
        completedCasesCountVal = new JLabel("0", SwingConstants.CENTER);

        statsRow.add(buildKpiCard("ACTIVE CASES", myCasesCountVal, UITheme.ACCENT_CYAN));
        statsRow.add(buildKpiCard("MY REQUESTS", pendingReqCountVal, new Color(251, 191, 36)));
        statsRow.add(buildKpiCard("AVAILABLE POOL", availablePoolCountVal, new Color(147, 197, 253)));
        statsRow.add(buildKpiCard("COMPLETED CASES", completedCasesCountVal, new Color(52, 211, 153)));

        center.add(statsRow, BorderLayout.NORTH);

        UITheme.GlassCardPanel lowerCard = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
        lowerCard.setLayout(new BorderLayout(0, 10));
        lowerCard.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel docketTitle = new JLabel("OPERATIONAL CASE ASSIGNMENTS");
        docketTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        docketTitle.setForeground(UITheme.ACCENT_CYAN);
        lowerCard.add(docketTitle, BorderLayout.NORTH);

        String[] cols = {"CASE NUMBER", "TITLE", "CATEGORY", "LOCATION", "PRIORITY", "STATUS"};
        dashboardDocketTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = buildStyledTable(dashboardDocketTableModel);
        table.getColumnModel().getColumn(4).setCellRenderer(new PriorityBadgeRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new AnimatedStatusBadgeRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        lowerCard.add(scroll, BorderLayout.CENTER);

        center.add(lowerCard, BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    public void loadDashboardMetrics() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            int myCases = 0, myReqs = 0, avail = 0, completed = 0;
            final List<Object[]> caseRows = new ArrayList<>();
            final List<String> flaggedDockets = new ArrayList<>();
            String latestFlagReason = "";

            @Override
            protected Void doInBackground() {
                try (Connection con = DBConnection.getConnection()) {
                    if (con == null) return null;

                    String sql1 = "SELECT COUNT(*) FROM cases " +
                            "WHERE (assigned_officer = ? OR assigned_officer = ? OR assigned_officer LIKE ?) " +
                            "  AND UPPER(status) = 'ACTIVE'";
                    try (PreparedStatement ps = con.prepareStatement(sql1)) {
                        ps.setString(1, investigatorUsername);
                        ps.setString(2, investigatorFullName);
                        ps.setString(3, "%" + investigatorFullName + "%");
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) myCases = rs.getInt(1);
                        }
                    }

                    String sql2 = "SELECT COUNT(*) FROM cases " +
                            "WHERE (created_by = ? OR created_by = ?) " +
                            "  AND UPPER(status) IN ('PENDING', 'CLAIM_PENDING', 'REJECTED')";
                    try (PreparedStatement ps = con.prepareStatement(sql2)) {
                        ps.setString(1, investigatorUsername);
                        ps.setString(2, investigatorFullName);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) myReqs = rs.getInt(1);
                        }
                    }

                    String sql3 = "SELECT COUNT(*) FROM cases " +
                            "WHERE UPPER(status) IN ('PENDING', 'CLAIM_PENDING') " +
                            "  AND (assigned_officer IS NULL OR assigned_officer = '' OR assigned_officer = 'Unassigned')";
                    try (PreparedStatement ps = con.prepareStatement(sql3);
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) avail = rs.getInt(1);
                    }

                    String sql4 = "SELECT COUNT(*) FROM cases " +
                            "WHERE (assigned_officer = ? OR assigned_officer = ? OR assigned_officer LIKE ?) " +
                            "  AND UPPER(status) = 'CLOSED'";
                    try (PreparedStatement ps = con.prepareStatement(sql4)) {
                        ps.setString(1, investigatorUsername);
                        ps.setString(2, investigatorFullName);
                        ps.setString(3, "%" + investigatorFullName + "%");
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) completed = rs.getInt(1);
                        }
                    }

                    String sql5 = "SELECT c.id, c.case_number, c.title, c.category, c.location_jurisdiction, c.priority, c.status, " +
                            " (SELECT COUNT(*) FROM case_evidence e WHERE (e.case_id = c.id OR e.case_number = c.case_number) AND UPPER(e.status) = 'FLAGGED') AS flagged_count, " +
                            " (SELECT COUNT(*) FROM case_evidence e WHERE (e.case_id = c.id OR e.case_number = c.case_number) AND UPPER(e.status) LIKE '%PENDING%') AS pending_count, " +
                            " (SELECT e.notes FROM case_evidence e WHERE (e.case_id = c.id OR e.case_number = c.case_number) AND UPPER(e.status) = 'FLAGGED' ORDER BY e.id DESC LIMIT 1) AS flag_note " +
                            "FROM cases c " +
                            "WHERE (c.assigned_officer = ? OR c.assigned_officer = ? OR c.assigned_officer LIKE ?) " +
                            "  AND UPPER(c.status) IN ('ACTIVE', 'CLOSED') ORDER BY c.id DESC LIMIT 15";

                    try (PreparedStatement ps = con.prepareStatement(sql5)) {
                        ps.setString(1, investigatorUsername);
                        ps.setString(2, investigatorFullName);
                        ps.setString(3, "%" + investigatorFullName + "%");
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String cNum = rs.getString("case_number");
                                String rawStatus = rs.getString("status");
                                int flaggedCount = rs.getInt("flagged_count");
                                int pendingCount = rs.getInt("pending_count");
                                String note = rs.getString("flag_note");

                                String displayStatus = rawStatus;
                                if (!"CLOSED".equalsIgnoreCase(rawStatus)) {
                                    if (flaggedCount > 0) {
                                        displayStatus = "FLAGGED - RE-UPLOAD NEEDED";
                                        flaggedDockets.add(cNum);
                                        if (note != null && !note.isEmpty()) {
                                            latestFlagReason = note;
                                        }
                                    } else if (pendingCount > 0) {
                                        displayStatus = "PENDING REVIEW";
                                    }
                                }

                                caseRows.add(new Object[]{
                                        cNum,
                                        rs.getString("title"),
                                        rs.getString("category"),
                                        rs.getString("location_jurisdiction"),
                                        rs.getString("priority"),
                                        displayStatus
                                });
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Investigator metric load err: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                myCasesCountVal.setText(String.valueOf(myCases));
                pendingReqCountVal.setText(String.valueOf(myReqs));
                availablePoolCountVal.setText(String.valueOf(avail));
                completedCasesCountVal.setText(String.valueOf(completed));

                dashboardDocketTableModel.setRowCount(0);
                for (Object[] r : caseRows) dashboardDocketTableModel.addRow(r);

                if (myCasesTableModel != null) {
                    myCasesTableModel.setRowCount(0);
                    for (Object[] r : caseRows) myCasesTableModel.addRow(r);
                }

                if (flaggedAlertBanner != null) {
                    if (!flaggedDockets.isEmpty()) {
                        String cleanReason = latestFlagReason.replace("\n", " ");
                        if (cleanReason.contains("[ADMIN FLAGGED]:")) {
                            cleanReason = cleanReason.substring(cleanReason.indexOf("[ADMIN FLAGGED]:") + 16).trim();
                        }
                        if (cleanReason.length() > 95) cleanReason = cleanReason.substring(0, 92) + "...";
                        flaggedAlertLbl.setText("⚠️ FORENSIC DISCREPANCY on " + flaggedDockets + ": " + cleanReason);
                        flaggedAlertBanner.setVisible(true);
                    } else {
                        flaggedAlertBanner.setVisible(false);
                    }
                    flaggedAlertBanner.revalidate();
                    flaggedAlertBanner.repaint();
                }
            }
        };
        worker.execute();
    }

    // =========================================================================
    // 2. AVAILABLE CASES WORKSPACE
    // =========================================================================
    private JPanel createAvailableCasesWorkspace() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel title = new JLabel("AVAILABLE INCIDENT POOL (UNASSIGNED / READY FOR CLAIM)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(UITheme.ACCENT_CYAN);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);

        viewAvailableCaseBtn = createStyledButton("View Case Dossier", new Color(59, 130, 246));
        claimBtn = createStyledButton("Request Case Assignment", new Color(16, 185, 129));
        JButton refreshBtn = createStyledButton("Refresh", new Color(30, 41, 59));

        viewAvailableCaseBtn.addActionListener(e -> viewSelectedDossier(availableTable, availableTableModel));
        claimBtn.addActionListener(e -> claimSelectedAvailableCase());
        refreshBtn.addActionListener(e -> loadAvailableCasesTable());

        rightBtns.add(viewAvailableCaseBtn);
        rightBtns.add(claimBtn);
        rightBtns.add(refreshBtn);
        topBar.add(title, BorderLayout.WEST);
        topBar.add(rightBtns, BorderLayout.EAST);
        p.add(topBar, BorderLayout.NORTH);

        String[] cols = {"CASE NUMBER", "TITLE", "CATEGORY", "LOCATION", "PRIORITY", "CLAIM STATUS"};
        availableTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        availableTable = buildStyledTable(availableTableModel);
        availableTable.getColumnModel().getColumn(4).setCellRenderer(new PriorityBadgeRenderer());
        availableTable.getColumnModel().getColumn(5).setCellRenderer(new AnimatedStatusBadgeRenderer());

        availableTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateClaimButtonState();
            }
        });

        JScrollPane scroll = new JScrollPane(availableTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        p.add(scroll, BorderLayout.CENTER);

        return p;
    }

    private void updateClaimButtonState() {
        int sel = availableTable.getSelectedRow();
        if (sel == -1 || sel >= poolCaseRequesters.size()) {
            claimBtn.setEnabled(true);
            claimBtn.setText("Request Case Assignment");
            claimBtn.setBackground(new Color(16, 185, 129));
            return;
        }

        String requester = poolCaseRequesters.get(sel);
        String rejectedList = poolCaseRejectedList.get(sel);

        boolean hasPendingRequest = requester != null && !requester.isEmpty() && !"admin".equalsIgnoreCase(requester) && !"Unassigned".equalsIgnoreCase(requester);
        boolean amIRejected = rejectedList != null && rejectedList.toLowerCase().contains(investigatorUsername.toLowerCase());

        if (amIRejected) {
            claimBtn.setEnabled(false);
            claimBtn.setText("Claim Rejected for You");
            claimBtn.setBackground(new Color(239, 68, 68));
        } else if (hasPendingRequest) {
            if (investigatorUsername.equalsIgnoreCase(requester)) {
                claimBtn.setEnabled(false);
                claimBtn.setText("Your Request is Pending Admin");
                claimBtn.setBackground(new Color(251, 191, 36));
            } else {
                claimBtn.setEnabled(false);
                claimBtn.setText("Claimed by " + requester + " (Pending)");
                claimBtn.setBackground(new Color(100, 116, 139));
            }
        } else {
            claimBtn.setEnabled(true);
            claimBtn.setText("Request Case Assignment");
            claimBtn.setBackground(new Color(16, 185, 129));
        }
    }

    private void loadAvailableCasesTable() {
        availableTableModel.setRowCount(0);
        poolCaseRequesters.clear();
        poolCaseRejectedList.clear();

        String sql = "SELECT case_number, title, category, location_jurisdiction, priority, status, created_by, rejected_officers " +
                "FROM cases WHERE UPPER(status) IN ('PENDING', 'CLAIM_PENDING') " +
                "AND (assigned_officer IS NULL OR assigned_officer = '' OR assigned_officer = 'Unassigned') ORDER BY id DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cNum = rs.getString("case_number");
                String title = rs.getString("title");
                String cat = rs.getString("category");
                String loc = rs.getString("location_jurisdiction");
                String prio = rs.getString("priority");
                String st = rs.getString("status");
                String creator = rs.getString("created_by");
                String rejected = rs.getString("rejected_officers");

                poolCaseRequesters.add(creator);
                poolCaseRejectedList.add(rejected);

                String displayClaimStatus = "AVAILABLE";
                if ("CLAIM_PENDING".equalsIgnoreCase(st)) {
                    if (investigatorUsername.equalsIgnoreCase(creator)) {
                        displayClaimStatus = "PENDING (YOU)";
                    } else {
                        displayClaimStatus = "CLAIMED (" + creator + ")";
                    }
                }

                if (rejected != null && rejected.toLowerCase().contains(investigatorUsername.toLowerCase())) {
                    displayClaimStatus = "REJECTED FOR YOU";
                }

                availableTableModel.addRow(new Object[]{
                        cNum, title, cat, loc, prio, displayClaimStatus
                });
            }
        } catch (Exception ex) {
            System.err.println("Load available pool error: " + ex.getMessage());
        }
        updateClaimButtonState();
    }

    private void claimSelectedAvailableCase() {
        int selectedRow = availableTable.getSelectedRow();
        if (selectedRow == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select a pool case to request.");
            return;
        }

        String caseNum = (String) availableTableModel.getValueAt(selectedRow, 0);
        String requester = poolCaseRequesters.get(selectedRow);
        String rejectedList = poolCaseRejectedList.get(selectedRow);

        if (rejectedList != null && rejectedList.toLowerCase().contains(investigatorUsername.toLowerCase())) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "CLAIM REJECTED", "Admin previously rejected your claim on this case. You cannot request it again.");
            return;
        }

        if (requester != null && !requester.isEmpty() && !"admin".equalsIgnoreCase(requester) && !"Unassigned".equalsIgnoreCase(requester)) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "ALREADY CLAIMED", "Another officer has already filed a pending claim for this case.");
            return;
        }

        boolean confirm = InvestigatorsPanel.GlassConfirmDialog.confirm(
                this,
                "REQUEST ASSIGNMENT",
                "Submit assignment request for Case " + caseNum + " to Head Administration for authorization?"
        );

        if (!confirm) return;

        String sql = "UPDATE cases SET status = 'CLAIM_PENDING', created_by = ?, updated_at = NOW() " +
                "WHERE case_number = ? AND UPPER(status) = 'PENDING'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, investigatorUsername);
            ps.setString(2, caseNum);
            int rowsUpdated = ps.executeUpdate();

            if (rowsUpdated > 0) {
                AuditLogger.log(investigatorUsername, "CASE CLAIM REQUEST: Officer requested lead on pool case [" + caseNum + "]");
                InvestigatorsPanel.GlassMessageDialog.showSuccess(this, "REQUEST TRANSMITTED", "Assignment request for " + caseNum + " submitted to Head Admin. Awaiting clearance.");
            } else {
                InvestigatorsPanel.GlassMessageDialog.showWarning(this, "ALREADY CLAIMED", "Case was just claimed by another officer.");
            }

            loadAvailableCasesTable();
            loadDashboardMetrics();
        } catch (Exception ex) {
            InvestigatorsPanel.GlassMessageDialog.showError(this, "REQUEST FAILED", ex.getMessage());
        }
    }

    // =========================================================================
    // 3. MY CASE REQUESTS
    // =========================================================================
    private JPanel createMyRequestsWorkspace() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftHeader.setOpaque(false);

        JLabel title = new JLabel("MY CASE SUBMISSIONS & DISPATCH APPROVAL STATUS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(new Color(251, 191, 36));

        animatedReqBadge = new AnimatedRequestBadge();
        leftHeader.add(title);
        leftHeader.add(animatedReqBadge);

        JButton refreshBtn = createStyledButton("Refresh Queue", new Color(30, 41, 59));
        refreshBtn.addActionListener(e -> loadMyCaseRequestsTable());

        topBar.add(leftHeader, BorderLayout.WEST);
        topBar.add(refreshBtn, BorderLayout.EAST);
        p.add(topBar, BorderLayout.NORTH);

        String[] cols = {"CASE NUMBER", "CASE TITLE", "CATEGORY", "DATE SUBMITTED", "APPROVAL STATUS", "PRIORITY"};
        myRequestsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        myRequestsTable = buildStyledTable(myRequestsTableModel);
        myRequestsTable.getColumnModel().getColumn(4).setCellRenderer(new AnimatedStatusBadgeRenderer());
        myRequestsTable.getColumnModel().getColumn(5).setCellRenderer(new PriorityBadgeRenderer());

        JScrollPane scroll = new JScrollPane(myRequestsTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(251, 191, 36, 60), 1));
        p.add(scroll, BorderLayout.CENTER);

        return p;
    }

    private void loadMyCaseRequestsTable() {
        myRequestsTableModel.setRowCount(0);
        String sql = "SELECT case_number, title, category, date_reported, status, priority " +
                "FROM cases WHERE (created_by = ? OR created_by = ?) OR (assigned_officer = ? AND UPPER(status) = 'REJECTED') " +
                "ORDER BY id DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, investigatorUsername);
            ps.setString(2, investigatorFullName);
            ps.setString(3, investigatorUsername);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rawStatus = rs.getString("status");
                    String displayStatus = "ACTIVE".equalsIgnoreCase(rawStatus) ? "APPROVED" :
                            ("CLAIM_PENDING".equalsIgnoreCase(rawStatus) ? "UNDER REVIEW" : rawStatus.toUpperCase());

                    myRequestsTableModel.addRow(new Object[]{
                            rs.getString("case_number"),
                            rs.getString("title"),
                            rs.getString("category"),
                            rs.getString("date_reported"),
                            displayStatus,
                            rs.getString("priority")
                    });
                }
            }

            if (animatedReqBadge != null) animatedReqBadge.setCount(myRequestsTableModel.getRowCount());

        } catch (Exception ex) {
            System.err.println("Load my requests error: " + ex.getMessage());
        }
    }

    // =========================================================================
    // 4. MY CASES
    // =========================================================================
    private JPanel createMyCasesWorkspace() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel topDeck = new JPanel(new BorderLayout(0, 8));
        topDeck.setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel title = new JLabel("ACTIVE JURISDICTION DOSSIERS (MY CASES)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(UITheme.ACCENT_CYAN);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton viewMyCaseDossierBtn = createStyledButton("View Case Dossier", new Color(59, 130, 246));
        JButton uploadEvidenceBtn = createStyledButton("Upload & Submit Evidence", new Color(16, 185, 129));
        JButton refreshBtn = createStyledButton("Refresh Catalog", new Color(30, 41, 59));

        viewMyCaseDossierBtn.addActionListener(e -> viewSelectedDossier(myCasesTable, myCasesTableModel));
        uploadEvidenceBtn.addActionListener(e -> openEvidenceIntakeModal());
        refreshBtn.addActionListener(e -> loadMyCasesTable());

        actions.add(viewMyCaseDossierBtn);
        actions.add(uploadEvidenceBtn);
        actions.add(refreshBtn);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(actions, BorderLayout.EAST);
        topDeck.add(topBar, BorderLayout.NORTH);

        flaggedAlertBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(239, 68, 68, 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(239, 68, 68, 140));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        flaggedAlertBanner.setOpaque(false);
        flaggedAlertBanner.setBorder(new EmptyBorder(4, 12, 4, 12));
        flaggedAlertBanner.setVisible(false);

        flaggedAlertLbl = new JLabel("⚠️ FORENSIC DISCREPANCY DETECTED: Admin has flagged evidence.");
        flaggedAlertLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        flaggedAlertLbl.setForeground(new Color(254, 202, 202));
        flaggedAlertBanner.add(flaggedAlertLbl);

        topDeck.add(flaggedAlertBanner, BorderLayout.SOUTH);
        p.add(topDeck, BorderLayout.NORTH);

        String[] cols = {"CASE NUMBER", "TITLE", "CATEGORY", "LOCATION", "PRIORITY", "STATUS"};
        myCasesTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        myCasesTable = buildStyledTable(myCasesTableModel);
        myCasesTable.getColumnModel().getColumn(4).setCellRenderer(new PriorityBadgeRenderer());
        myCasesTable.getColumnModel().getColumn(5).setCellRenderer(new AnimatedStatusBadgeRenderer());

        myCasesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) viewSelectedDossier(myCasesTable, myCasesTableModel);
            }
        });

        JScrollPane scroll = new JScrollPane(myCasesTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        p.add(scroll, BorderLayout.CENTER);

        return p;
    }

    private void loadMyCasesTable() {
        loadDashboardMetrics();
    }

    // =========================================================================
    // VIEW COMPLETE CASE DOSSIER & VICTIM DIRECTORY MODAL
    // =========================================================================
    private void viewSelectedDossier(JTable table, DefaultTableModel model) {
        int sel = table.getSelectedRow();
        if (sel == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select a case row to inspect its full dossier.");
            return;
        }

        String caseNum = (String) model.getValueAt(sel, 0);

        JDialog dialog = new JDialog(this, "Master Case Dossier: " + caseNum, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(14, 14));

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 22, 0, 22));

        JLabel h1 = new JLabel("INCIDENT DOSSIER & COMPREHENSIVE INTELLIGENCE");
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(UITheme.ACCENT_CYAN);

        JLabel h2 = new JLabel("Case Docket Identifier: " + caseNum);
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h2.setForeground(new Color(148, 163, 184));

        header.add(h1);
        header.add(h2);
        dialog.add(header, BorderLayout.NORTH);

        JTabbedPane dossierTabs = new JTabbedPane();
        dossierTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        dossierTabs.setBackground(new Color(25, 33, 48));
        dossierTabs.setForeground(Color.WHITE);

        // 1. Overview Tab
        JPanel overviewPanel = new JPanel(new BorderLayout(0, 10));
        overviewPanel.setOpaque(false);
        overviewPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel metaGrid = new JPanel(new GridLayout(3, 2, 12, 6));
        metaGrid.setOpaque(false);

        JLabel titleLbl = new JLabel("Title: Loading...");
        JLabel catLbl = new JLabel("Category: Loading...");
        JLabel locLbl = new JLabel("Location: Loading...");
        JLabel prioLbl = new JLabel("Priority: Loading...");
        JLabel dateLbl = new JLabel("Date Reported: Loading...");
        JLabel officerLbl = new JLabel("Assigned Lead: Loading...");

        for (JLabel l : new JLabel[]{titleLbl, catLbl, locLbl, prioLbl, dateLbl, officerLbl}) {
            l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            l.setForeground(Color.WHITE);
            metaGrid.add(l);
        }
        overviewPanel.add(metaGrid, BorderLayout.NORTH);

        JTextArea descArea = new JTextArea();
        descArea.setBackground(new Color(24, 32, 47));
        descArea.setForeground(new Color(226, 232, 240));
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1),
                " INCIDENT DESCRIPTION & FIELD DIRECTIVES ",
                0, 0,
                new Font("Segoe UI", Font.BOLD, 11),
                UITheme.ACCENT_CYAN
        ));
        overviewPanel.add(descScroll, BorderLayout.CENTER);
        dossierTabs.addTab("Incident Overview", overviewPanel);

        // 2. Victims Directory Tab
        JPanel victimsTabPanel = new JPanel(new BorderLayout());
        victimsTabPanel.setOpaque(false);
        victimsTabPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        String[] vCols = {"#", "FULL NAME", "GENDER", "AGE", "DOB", "PHONE", "ADDRESS"};
        DefaultTableModel vModel = new DefaultTableModel(vCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable vTable = buildStyledTable(vModel);
        vTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        vTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        vTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        vTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        vTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        vTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        vTable.getColumnModel().getColumn(6).setPreferredWidth(210);

        JScrollPane vScroll = new JScrollPane(vTable);
        vScroll.getViewport().setBackground(new Color(15, 23, 42));
        vScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        victimsTabPanel.add(vScroll, BorderLayout.CENTER);
        dossierTabs.addTab("Victims & Complainants", victimsTabPanel);

        dialog.add(dossierTabs, BorderLayout.CENTER);

        new Thread(() -> {
            String sqlCase = "SELECT title, category, location_jurisdiction, priority, date_reported, assigned_officer, incident_description, investigation_notes FROM cases WHERE case_number = ?";
            String sqlVictims = "SELECT full_name, gender, age, dob, phone, address FROM case_victims WHERE case_number = ? ORDER BY id ASC";

            try (Connection conn = DBConnection.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(sqlCase)) {
                        ps.setString(1, caseNum);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String t = rs.getString("title");
                                String c = rs.getString("category");
                                String l = rs.getString("location_jurisdiction");
                                String p = rs.getString("priority");
                                String d = rs.getString("date_reported");
                                String o = rs.getString("assigned_officer");
                                String desc = rs.getString("incident_description");
                                String notes = rs.getString("investigation_notes");

                                SwingUtilities.invokeLater(() -> {
                                    titleLbl.setText("Title: " + (t != null ? t : "--"));
                                    catLbl.setText("Category: " + (c != null ? c : "--"));
                                    locLbl.setText("Location: " + (l != null ? l : "--"));
                                    prioLbl.setText("Priority: " + (p != null ? p : "--"));
                                    dateLbl.setText("Date: " + (d != null ? d : "--"));
                                    officerLbl.setText("Lead: " + (o != null ? o : "Unassigned"));
                                    descArea.setText("--- INCIDENT SUMMARY ---\n" + (desc != null ? desc : "None provided.") +
                                            "\n\n--- FIELD DIRECTIVES ---\n" + (notes != null ? notes : "None logged."));
                                });
                            }
                        }
                    }

                    try (PreparedStatement psV = conn.prepareStatement(sqlVictims)) {
                        psV.setString(1, caseNum);
                        try (ResultSet rsV = psV.executeQuery()) {
                            int count = 1;
                            while (rsV.next()) {
                                final int idx = count++;
                                final String fn = rsV.getString("full_name");
                                final String g = rsV.getString("gender");
                                final String a = rsV.getString("age");
                                final String dob = rsV.getString("dob");
                                final String ph = rsV.getString("phone");
                                final String addr = rsV.getString("address");

                                SwingUtilities.invokeLater(() -> {
                                    vModel.addRow(new Object[]{
                                            idx, fn, g, (a != null ? a : "--"), (dob != null ? dob : "--"), (ph != null ? ph : "--"), (addr != null ? addr : "--")
                                    });
                                });
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Load dossier error: " + ex.getMessage());
            }
        }).start();

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 14));
        footer.setOpaque(false);
        JButton closeBtn = createStyledButton("Close Dossier", new Color(30, 41, 59));
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(780, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // =========================================================================
    // MULTI-FILE EVIDENCE INTAKE MODAL
    // =========================================================================
    private void openEvidenceIntakeModal() {
        int row = myCasesTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an assigned case from the table to inspect/log evidence.");
            return;
        }

        String caseNum = (String) myCasesTableModel.getValueAt(row, 0);
        String caseTitle = (String) myCasesTableModel.getValueAt(row, 1);
        String caseStatus = (String) myCasesTableModel.getValueAt(row, 5);

        if ("CLOSED".equalsIgnoreCase(caseStatus)) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "CASE CLOSED", "This case has been solved and closed by Head Admin. No further evidence can be filed.");
            return;
        }

        boolean isPendingReview = caseStatus != null && caseStatus.toUpperCase().contains("PENDING REVIEW");
        boolean isFlagged = caseStatus != null && caseStatus.toUpperCase().contains("FLAGGED");

        String adminReason = "";
        if (isFlagged) {
            String noteSql = "SELECT notes FROM case_evidence WHERE (case_number = ? OR case_id = (SELECT id FROM cases WHERE case_number = ? LIMIT 1)) AND UPPER(status) = 'FLAGGED' ORDER BY id DESC LIMIT 1";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement psNote = conn.prepareStatement(noteSql)) {
                psNote.setString(1, caseNum);
                psNote.setString(2, caseNum);
                try (ResultSet rsNote = psNote.executeQuery()) {
                    if (rsNote.next()) {
                        adminReason = rsNote.getString("notes");
                    }
                }
            } catch (Exception ignored) {}
        }

        JDialog dialog = new JDialog(this, "Evidence Intake Console: " + caseNum, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(14, 14));

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(18, 24, 0, 24));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBlock.setOpaque(false);

        JLabel h1 = new JLabel(isFlagged ? "CORRECTIVE EVIDENCE RE-SUBMISSION (OVERWRITING FLAGGED DOSSIER)" :
                (isPendingReview ? "EVIDENCE DOSSIER UNDER ADMINISTRATIVE AUDIT" : "MULTI-MEDIA EVIDENCE INTAKE & CHAIN OF CUSTODY"));
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(isFlagged ? new Color(248, 113, 113) : (isPendingReview ? new Color(251, 191, 36) : UITheme.ACCENT_CYAN));

        JLabel h2 = new JLabel("Case Docket: " + caseNum + " — " + caseTitle);
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h2.setForeground(new Color(148, 163, 184));

        titleBlock.add(h1);
        titleBlock.add(h2);
        header.add(titleBlock, BorderLayout.NORTH);

        if (isFlagged && adminReason != null && !adminReason.trim().isEmpty()) {
            String displayAdminNote = adminReason;
            if (displayAdminNote.contains("[ADMIN FLAGGED]:")) {
                displayAdminNote = displayAdminNote.substring(displayAdminNote.indexOf("[ADMIN FLAGGED]:") + 16).trim();
            }

            JPanel adminReasonCard = new JPanel(new BorderLayout(8, 4)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(40, 20, 25, 240));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(239, 68, 68, 140));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            adminReasonCard.setOpaque(false);
            adminReasonCard.setBorder(new EmptyBorder(8, 12, 8, 12));

            JLabel titleReason = new JLabel("ADMINISTRATIVE AUDIT DIRECTIVE & DEFICIENCY REASON:");
            titleReason.setFont(new Font("Segoe UI", Font.BOLD, 11));
            titleReason.setForeground(new Color(248, 113, 113));

            JTextArea noteArea = new JTextArea(displayAdminNote);
            noteArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            noteArea.setForeground(Color.WHITE);
            noteArea.setOpaque(false);
            noteArea.setEditable(false);
            noteArea.setLineWrap(true);
            noteArea.setWrapStyleWord(true);

            adminReasonCard.add(titleReason, BorderLayout.NORTH);
            adminReasonCard.add(noteArea, BorderLayout.CENTER);
            header.add(adminReasonCard, BorderLayout.SOUTH);
        } else if (isPendingReview) {
            JLabel pendingNotice = new JLabel(" ⏳ BATCH TRANSMITTED • UNDER REVIEW BY HEAD ADMINISTRATION — You cannot submit duplicate files until the audit is resolved.");
            pendingNotice.setFont(new Font("Segoe UI", Font.BOLD, 11));
            pendingNotice.setForeground(new Color(254, 243, 199));
            pendingNotice.setOpaque(true);
            pendingNotice.setBackground(new Color(120, 53, 15, 180));
            pendingNotice.setBorder(new EmptyBorder(6, 10, 6, 10));
            header.add(pendingNotice, BorderLayout.SOUTH);
        }

        dialog.add(header, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(new EmptyBorder(6, 24, 6, 24));

        String[] qCols = {"#", "FILE NAME", "CLASSIFICATION", "FILE SIZE", "FILE PATH"};
        DefaultTableModel queueModel = new DefaultTableModel(qCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable queueTable = buildStyledTable(queueModel);
        queueTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        queueTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        queueTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        queueTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        queueTable.getColumnModel().getColumn(4).setPreferredWidth(260);

        JScrollPane queueScroll = new JScrollPane(queueTable);
        queueScroll.setPreferredSize(new Dimension(0, 190));
        queueScroll.getViewport().setBackground(new Color(15, 23, 42));
        queueScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(56, 189, 248, 70), 1),
                        " STAGED MEDIA ASSETS QUEUE ",
                        0, 0,
                        new Font("Segoe UI", Font.BOLD, 11),
                        UITheme.ACCENT_CYAN
                ),
                new EmptyBorder(4, 4, 4, 4)
        ));

        JPanel queueToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        queueToolbar.setOpaque(false);

        List<File> stagedFiles = new ArrayList<>();

        JButton addFilesBtn = createStyledButton("+ Add Files (Multi-Select: Photos, Videos, Audio, PDFs)", new Color(59, 130, 246));
        JButton removeSelectedBtn = createStyledButton("Remove Selected File", new Color(239, 68, 68));
        JButton clearAllBtn = createStyledButton("Clear Queue", new Color(30, 41, 59));

        if (isPendingReview) {
            addFilesBtn.setEnabled(false);
            removeSelectedBtn.setEnabled(false);
            clearAllBtn.setEnabled(false);
        }

        addFilesBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setDialogTitle("Select Evidence Files to Stage");
            fc.setFileFilter(new FileNameExtensionFilter("All Supported Media & Docs",
                    "png", "jpg", "jpeg", "bmp", "gif", "wav", "mp3", "m4a", "mp4", "avi", "mov", "mkv", "pdf", "docx", "txt"));

            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                File[] selected = fc.getSelectedFiles();
                for (File f : selected) {
                    if (!stagedFiles.contains(f)) {
                        stagedFiles.add(f);
                        String extType = detectFileClassification(f.getName());
                        String sizeStr = formatFileSizeReadable(f.length());
                        queueModel.addRow(new Object[]{
                                stagedFiles.size(),
                                f.getName(),
                                extType,
                                sizeStr,
                                f.getAbsolutePath()
                        });
                    }
                }
            }
        });

        removeSelectedBtn.addActionListener(e -> {
            int sel = queueTable.getSelectedRow();
            if (sel != -1) {
                stagedFiles.remove(sel);
                queueModel.removeRow(sel);
                for (int i = 0; i < queueModel.getRowCount(); i++) {
                    queueModel.setValueAt(i + 1, i, 0);
                }
            }
        });

        clearAllBtn.addActionListener(e -> {
            stagedFiles.clear();
            queueModel.setRowCount(0);
        });

        queueToolbar.add(addFilesBtn);
        queueToolbar.add(removeSelectedBtn);
        queueToolbar.add(clearAllBtn);

        JPanel tableWrapper = new JPanel(new BorderLayout(0, 8));
        tableWrapper.setOpaque(false);
        tableWrapper.add(queueToolbar, BorderLayout.NORTH);
        tableWrapper.add(queueScroll, BorderLayout.CENTER);
        centerPanel.add(tableWrapper, BorderLayout.CENTER);

        JPanel notesPanel = new JPanel(new BorderLayout(0, 4));
        notesPanel.setOpaque(false);

        JLabel notesLbl = new JLabel(isFlagged ? "Detective Narrative Explaining How Discrepancy Was Resolved:" : "Investigator Narrative & Chain of Custody Notes:");
        notesLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        notesLbl.setForeground(new Color(148, 163, 184));

        JTextArea notesArea = new JTextArea(3, 20);
        notesArea.setBackground(new Color(25, 33, 48));
        notesArea.setForeground(Color.WHITE);
        notesArea.setCaretColor(UITheme.ACCENT_CYAN);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        if (isPendingReview) {
            notesArea.setEditable(false);
            notesArea.setText("Package under review by Head Administration.");
        }

        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(0, 80));
        notesScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));

        notesPanel.add(notesLbl, BorderLayout.NORTH);
        notesPanel.add(notesScroll, BorderLayout.CENTER);
        centerPanel.add(notesPanel, BorderLayout.SOUTH);

        dialog.add(centerPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 16));
        actions.setOpaque(false);

        JButton cancelBtn = createStyledButton("Close", new Color(30, 41, 59));
        JButton transmitBatchBtn = createStyledButton(isFlagged ? "Re-Transmit Corrected Evidence Batch" : "Transmit Staged Evidence Batch to Admin", new Color(16, 185, 129));

        if (isPendingReview) {
            transmitBatchBtn.setEnabled(false);
        }

        cancelBtn.addActionListener(e -> dialog.dispose());
        transmitBatchBtn.addActionListener(e -> {
            if (stagedFiles.isEmpty()) {
                InvestigatorsPanel.GlassMessageDialog.showWarning(dialog, "NO EVIDENCE STAGED", "Please click '+ Add Files' and stage at least one replacement file.");
                return;
            }

            String notes = notesArea.getText().trim();
            if (notes.isEmpty()) {
                notes = isFlagged ? "Corrected replacement package submitted for Case " + caseNum : "Field evidence package submitted under Case " + caseNum;
            }

            try (Connection conn = DBConnection.getConnection()) {
                conn.setAutoCommit(false);

                int currentRevision = 1;
                String revSql = "SELECT COALESCE(MAX(revision_number), 1) AS max_rev FROM case_evidence " +
                        "WHERE case_number = ? OR case_id = (SELECT id FROM cases WHERE case_number = ? LIMIT 1)";
                try (PreparedStatement psRev = conn.prepareStatement(revSql)) {
                    psRev.setString(1, caseNum);
                    psRev.setString(2, caseNum);
                    try (ResultSet rsRev = psRev.executeQuery()) {
                        if (rsRev.next()) {
                            currentRevision = rsRev.getInt("max_rev");
                        }
                    }
                }

                if (isFlagged) {
                    currentRevision++;
                    String overwriteOldSql = "UPDATE case_evidence SET status = 'SUPERSEDED' " +
                            "WHERE (case_number = ? OR case_id = (SELECT id FROM cases WHERE case_number = ? LIMIT 1)) " +
                            "  AND UPPER(status) = 'FLAGGED'";
                    try (PreparedStatement psOld = conn.prepareStatement(overwriteOldSql)) {
                        psOld.setString(1, caseNum);
                        psOld.setString(2, caseNum);
                        psOld.executeUpdate();
                    }
                }

                String insertSql = "INSERT INTO case_evidence (case_id, case_number, evidence_number, title, file_type, file_name, file_path, collected_by, status, notes, revision_number, is_resubmission, uploaded_at) " +
                        "VALUES ((SELECT id FROM cases WHERE case_number = ? LIMIT 1), ?, ?, ?, ?, ?, ?, ?, 'PENDING REVIEW', ?, ?, ?, NOW())";

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    long baseSeed = System.currentTimeMillis() % 100000;
                    int count = 0;

                    for (File f : stagedFiles) {
                        String evNo = "EV-" + (baseSeed + (count++));
                        String autoClass = detectFileClassification(f.getName());

                        ps.setString(1, caseNum);
                        ps.setString(2, caseNum);
                        ps.setString(3, evNo);
                        ps.setString(4, f.getName());
                        ps.setString(5, autoClass);
                        ps.setString(6, f.getName());
                        ps.setString(7, f.getAbsolutePath());
                        ps.setString(8, investigatorUsername);
                        ps.setString(9, notes);
                        ps.setInt(10, currentRevision);
                        ps.setInt(11, isFlagged ? 1 : 0);
                        ps.addBatch();
                    }

                    ps.executeBatch();
                    conn.commit();

                    String auditMsg = isFlagged ?
                            "RE-TRANSMISSION (REV #" + currentRevision + "): Re-uploaded " + stagedFiles.size() + " corrected evidence file(s) for Case [" + caseNum + "]" :
                            "BATCH EVIDENCE TRANSMITTED: Submitted " + stagedFiles.size() + " forensic file(s) for Case [" + caseNum + "]";
                    AuditLogger.log(investigatorUsername, auditMsg);

                    dialog.dispose();
                    InvestigatorsPanel.GlassMessageDialog.showSuccess(
                            this,
                            isFlagged ? "RE-SUBMISSION TRANSMITTED" : "BATCH UPLOAD SUCCESS",
                            stagedFiles.size() + " evidence asset(s) successfully logged (Revision #" + currentRevision + ") and queued for Head Administration review."
                    );
                    loadMyCasesTable();

                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (Exception ex) {
                InvestigatorsPanel.GlassMessageDialog.showError(dialog, "SUBMISSION FAILED", ex.getMessage());
            }
        });

        actions.add(cancelBtn);
        actions.add(transmitBatchBtn);
        dialog.add(actions, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(840, isFlagged ? 650 : 590);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static String detectFileClassification(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp") || lower.endsWith(".gif")) {
            return "Image";
        } else if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".wmv")) {
            return "Video";
        } else if (lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg")) {
            return "Audio";
        } else if (lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".txt") || lower.endsWith(".xlsx")) {
            return "Document";
        }
        return "Physical / Other";
    }

    private static String formatFileSizeReadable(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // =========================================================================
    // 5. IN-PLACE FACE RECOGNITION (PERSISTENT STATE & NO BLANK SCREEN)
    // =========================================================================
    private JPanel createFaceRecognitionWorkspace() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 24, 20, 24));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("FORENSIC NEURAL BIOMETRIC IDENTIFICATION (FACENET 512-D)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(UITheme.ACCENT_CYAN);
        titlePanel.add(title);
        root.add(titlePanel, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        UITheme.GlassCardPanel card = new UITheme.GlassCardPanel(16, UITheme.CARD_BG);
        card.setLayout(new BorderLayout(24, 16));
        card.setBorder(new EmptyBorder(22, 32, 22, 32));

        // Center Area: Photo Viewport + Bottom Controls
        JPanel leftCenterPanel = new JPanel(new BorderLayout(0, 14));
        leftCenterPanel.setOpaque(false);

        faceScanViewport = new InPlaceLaserScanViewport();
        faceScanViewport.setPreferredSize(new Dimension(320, 320));
        if (loadedQueryBufferedImage != null) {
            faceScanViewport.setImage(loadedQueryBufferedImage);
        }
        leftCenterPanel.add(faceScanViewport, BorderLayout.CENTER);

        JPanel bottomControls = new JPanel(new BorderLayout(0, 10));
        bottomControls.setOpaque(false);

        resultStatusLbl = new JLabel(selectedFaceQueryFile != null ? "STAGED: " + selectedFaceQueryFile.getName() : "AWAITING TARGET STAGING", SwingConstants.CENTER);
        resultStatusLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        resultStatusLbl.setForeground(selectedFaceQueryFile != null ? new Color(56, 189, 248) : new Color(148, 163, 184));
        bottomControls.add(resultStatusLbl, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnBar.setOpaque(false);
        JButton uploadBtn = createStyledButton("Upload Photo", new Color(59, 130, 246));
        scanBtn = createStyledButton("Scan & Match Database", new Color(16, 185, 129));
        exportPdfReportBtn = createStyledButton("Generate PDF Dossier", new Color(139, 92, 246));
        exportPdfReportBtn.setEnabled(lastMatchConfidence >= 68.0);

        btnBar.add(uploadBtn);
        btnBar.add(scanBtn);
        btnBar.add(exportPdfReportBtn);
        bottomControls.add(btnBar, BorderLayout.SOUTH);

        leftCenterPanel.add(bottomControls, BorderLayout.SOUTH);
        card.add(leftCenterPanel, BorderLayout.CENTER);

        // Sideways Bottom-to-Top Vertical Confidence Bar
        verticalConfidenceBar = new VerticalConfidenceBar();
        if (lastMatchConfidence > 0) {
            verticalConfidenceBar.setCurrentPct((float) lastMatchConfidence);
        }
        card.add(verticalConfidenceBar, BorderLayout.EAST);

        centerWrapper.add(card);
        root.add(centerWrapper, BorderLayout.CENTER);

        // Upload Button Action: Automatically triggers scan & reveals score
        uploadBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select Target Facial Image");
            fc.setFileFilter(new FileNameExtensionFilter("Face Images (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));

            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedFaceQueryFile = fc.getSelectedFile();
                try {
                    loadedQueryBufferedImage = ImageIO.read(selectedFaceQueryFile);
                    if (loadedQueryBufferedImage != null) {
                        faceScanViewport.setImage(loadedQueryBufferedImage);
                        verticalConfidenceBar.reset();
                        exportPdfReportBtn.setEnabled(false);
                        lastMatchConfidence = 0.0;
                        resultStatusLbl.setText("STAGED: " + selectedFaceQueryFile.getName());
                        resultStatusLbl.setForeground(new Color(56, 189, 248));

                        // AUTO-TRIGGER SCAN IMMEDIATELY UPON UPLOAD
                        triggerInPlaceScanSequence();
                    }
                } catch (Exception ex) {
                    InvestigatorsPanel.GlassMessageDialog.showError(this, "IMAGE ERROR", "Could not render target image.");
                }
            }
        });

        // Scan Action (Manual Re-scan Trigger)
        scanBtn.addActionListener(e -> {
            if (selectedFaceQueryFile == null || loadedQueryBufferedImage == null) {
                InvestigatorsPanel.GlassMessageDialog.showWarning(this, "IMAGE REQUIRED", "Please upload a target suspect photo first.");
                return;
            }
            triggerInPlaceScanSequence();
        });

        // PDF Dossier Export Action
        exportPdfReportBtn.addActionListener(e -> {
            if (selectedFaceQueryFile != null) {
                generateBiometricPdfReport(lastMatchedSuspectName, lastMatchedCaseNum, lastMatchConfidence, selectedFaceQueryFile);
            }
        });

        return root;
    }

    private void triggerInPlaceScanSequence() {
        exportPdfReportBtn.setEnabled(false);
        scanBtn.setEnabled(false);
        verticalConfidenceBar.reset();
        resultStatusLbl.setText("SCANNING SUSPECT BIOMETRICS IN REAL-TIME...");
        resultStatusLbl.setForeground(new Color(251, 191, 36));

        // Start in-place laser scan animation directly on this page
        faceScanViewport.startScanning();

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                List<Map<String, Object>> candidateList = new ArrayList<>();
                String sql = "SELECT suspect_name, case_number, embedding FROM suspect_faces WHERE embedding IS NOT NULL AND embedding != '' AND embedding != '0.0'";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", rs.getString("suspect_name"));
                        map.put("case_number", rs.getString("case_number"));

                        String rawVector = rs.getString("embedding");
                        String[] parts = rawVector.split(",");
                        List<Double> vec = new ArrayList<>();
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                vec.add(Double.parseDouble(trimmed));
                            }
                        }
                        if (vec.size() == 512) {
                            map.put("embedding", vec);
                            candidateList.add(map);
                        }
                    }
                }

                if (candidateList.isEmpty()) {
                    System.err.println("[BIOMETRICS] No candidates with 512-D embeddings found in database!");
                    return "EMPTY_DB";
                }

                File tempJson = File.createTempFile("facenet_candidates_", ".json");
                tempJson.deleteOnExit();

                try (FileWriter writer = new FileWriter(tempJson)) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < candidateList.size(); i++) {
                        Map<String, Object> c = candidateList.get(i);
                        sb.append("{\"name\":\"").append(c.get("name")).append("\",");
                        sb.append("\"case_number\":\"").append(c.get("case_number")).append("\",");
                        sb.append("\"embedding\":").append(c.get("embedding").toString()).append("}");
                        if (i < candidateList.size() - 1) sb.append(",");
                    }
                    sb.append("]");
                    writer.write(sb.toString());
                }

                ProcessBuilder pb = new ProcessBuilder(
                        "python",
                        "facenet_engine.py",
                        "match",
                        selectedFaceQueryFile.getAbsolutePath(),
                        tempJson.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder fullOutput = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[PYTHON ENGINE OUTPUT]: " + line);
                        fullOutput.append(line).append("\n");
                    }
                }
                int exitCode = process.waitFor();
                System.out.println("[PYTHON EXIT CODE]: " + exitCode);
                return fullOutput.toString();
            }

            @Override
            protected void done() {
                scanBtn.setEnabled(true);
                faceScanViewport.stopScanning();

                try {
                    String rawOutput = get();
                    if (rawOutput == null || rawOutput.trim().isEmpty()) {
                        handleScanFailure("Empty response received from biometric engine.");
                        return;
                    }

                    if (rawOutput.contains("EMPTY_DB")) {
                        resultStatusLbl.setText("ERROR: NO 512-D SUSPECT EMBEDDINGS FOUND");
                        resultStatusLbl.setForeground(new Color(239, 68, 68));
                        InvestigatorsPanel.GlassMessageDialog.showWarning(
                                InvestigatorDashboardFrame.this,
                                "DATABASE EMPTY",
                                "No valid 512-D facial vectors found in MySQL.\nPlease run enroll_all_6.py to enroll suspect faces."
                        );
                        return;
                    }

                    String jsonString = extractJsonFromOutput(rawOutput);

                    if (jsonString.contains("NO_FACE_DETECTED")) {
                        verticalConfidenceBar.reset();
                        exportPdfReportBtn.setEnabled(false);
                        lastMatchConfidence = 0.0;
                        lastMatchedSuspectName = "N/A";
                        lastMatchedCaseNum = "N/A";

                        resultStatusLbl.setText("⚠️ NO FACE DETECTED IN TARGET MEDIA");
                        resultStatusLbl.setForeground(new Color(239, 68, 68));

                        InvestigatorsPanel.GlassMessageDialog.showWarning(
                                InvestigatorDashboardFrame.this,
                                "BIOMETRIC ACQUISITION FAILED",
                                "No human facial geometry detected in the uploaded image.\n\nPlease upload a clear, front-facing portrait."
                        );
                        return;
                    }

                    if (jsonString.contains("\"bbox\":")) {
                        try {
                            int bStart = jsonString.indexOf("\"bbox\":") + 7;
                            int bEnd = jsonString.indexOf("]", bStart) + 1;
                            String bStr = jsonString.substring(bStart, bEnd).replaceAll("[\\[\\]\\s]", "");
                            String[] p = bStr.split(",");
                            if (p.length == 4) {
                                lastBboxStr = "x=" + p[0] + ", y=" + p[1] + ", w=" + p[2] + ", h=" + p[3];
                            }
                        } catch (Exception ignored) {}
                    }
                    if (jsonString.contains("\"det_confidence\":")) {
                        try {
                            lastDetConf = Double.parseDouble(parseJsonField(jsonString, "det_confidence"));
                        } catch (Exception ignored) {}
                    }

                    if (jsonString.contains("MATCH_FOUND")) {
                        String name = parseJsonField(jsonString, "name");
                        String caseNum = parseJsonField(jsonString, "case_number");
                        String confStr = parseJsonField(jsonString, "confidence");
                        double conf = 0.0;
                        try { conf = Double.parseDouble(confStr); } catch (Exception ignored) {}

                        lastMatchedSuspectName = name;
                        lastMatchedCaseNum = caseNum;
                        lastMatchConfidence = conf;

                        verticalConfidenceBar.animateTo(conf);
                        exportPdfReportBtn.setEnabled(true);

                        resultStatusLbl.setText("CONFIRMED MATCH: " + name.toUpperCase() + " [" + caseNum + "]");
                        resultStatusLbl.setForeground(new Color(52, 211, 153));
                    } else if (jsonString.contains("NO_MATCH")) {
                        String topConf = parseJsonField(jsonString, "top_confidence");
                        double top = 0.0;
                        try { top = Double.parseDouble(topConf); } catch (Exception ignored) {}

                        lastMatchedSuspectName = "None (Below Cutoff)";
                        lastMatchedCaseNum = "N/A";
                        lastMatchConfidence = top;

                        verticalConfidenceBar.animateTo(top);
                        exportPdfReportBtn.setEnabled(false);

                        resultStatusLbl.setText("NO MATCH (PEAK SCORE: " + String.format("%.1f", top) + "% < 68% CUTOFF)");
                        resultStatusLbl.setForeground(new Color(239, 68, 68));
                    } else {
                        handleScanFailure("Unrecognized response: " + jsonString);
                    }
                } catch (Exception ex) {
                    handleScanFailure(ex.getMessage());
                }
            }

            private void handleScanFailure(String message) {
                verticalConfidenceBar.reset();
                exportPdfReportBtn.setEnabled(false);
                resultStatusLbl.setText("ENGINE ERROR: " + message);
                resultStatusLbl.setForeground(new Color(239, 68, 68));
                System.err.println("[SCAN ERROR]: " + message);
            }
        };

        worker.execute();
    }

    private static String extractJsonFromOutput(String fullOutput) {
        int firstBrace = fullOutput.indexOf('{');
        int lastBrace = fullOutput.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return fullOutput.substring(firstBrace, lastBrace + 1).trim();
        }
        return fullOutput.trim();
    }

    private String parseJsonField(String json, String field) {
        try {
            String key = "\"" + field + "\":";
            int idx = json.indexOf(key);
            if (idx == -1) return "N/A";
            int start = idx + key.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        } catch (Exception e) {
            return "N/A";
        }
    }

    // =========================================================================
    // NATIVE PDF REPORT GENERATION (QUERY IMAGE & DATABASE ENROLLED MUGSHOT)
    // =========================================================================
    private void generateBiometricPdfReport(String suspectName, String caseNumber, double confidence, File queryFile) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String safeCase = (caseNumber != null ? caseNumber.replace("-", "_") : "CASE");
            String filename = "FORENSIC_BIOMETRIC_REPORT_" + safeCase + "_" + timestamp + ".pdf";
            File pdfFile = new File(filename);

            File dbSuspectImageFile = null;
            String rawDbImagePath = null;

            // 1. Query the enrolled mugshot path directly from MySQL
            String sql = "SELECT image_path FROM suspect_faces " +
                    "WHERE LOWER(TRIM(suspect_name)) = LOWER(TRIM(?)) " +
                    "   OR (case_number IS NOT NULL AND TRIM(case_number) = TRIM(?)) LIMIT 1";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, suspectName != null ? suspectName.trim() : "");
                ps.setString(2, caseNumber != null ? caseNumber.trim() : "");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rawDbImagePath = rs.getString("image_path");
                    }
                }
            } catch (Exception ex) {
                System.err.println("[PDF DB ERROR] Could not query suspect image: " + ex.getMessage());
            }

            // 2. Resolve image path from disk
            if (rawDbImagePath != null && !rawDbImagePath.trim().isEmpty()) {
                dbSuspectImageFile = resolveImageFile(rawDbImagePath.trim());
            }

            // 3. Fallback specifically to Documents\Mug Shots if needed
            if (dbSuspectImageFile == null || !dbSuspectImageFile.exists()) {
                dbSuspectImageFile = findMugShotInDocuments(suspectName, caseNumber);
            }

            System.out.println("--------------------------------------------------");
            System.out.println("[PDF DEBUG] Query Candidate File : " + (queryFile != null ? queryFile.getAbsolutePath() : "NULL"));
            System.out.println("[PDF DEBUG] Database Mugshot File: " + (dbSuspectImageFile != null ? dbSuspectImageFile.getAbsolutePath() : "NOT FOUND"));
            System.out.println("--------------------------------------------------");

            try (OutputStream os = new FileOutputStream(pdfFile)) {
                writeForensicPdfDocument(os, suspectName, caseNumber, confidence, queryFile, dbSuspectImageFile);
            }

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO activity_logs (user, action_text) VALUES (?, ?)")) {
                ps.setString(1, this.investigatorUsername);
                ps.setString(2, "Exported Biometric Forensic PDF Dossier for Suspect [" + suspectName + "] (Docket: " + caseNumber + ", Confidence: " + String.format("%.2f", confidence) + "%)");
                ps.executeUpdate();
            }

            InvestigatorsPanel.GlassMessageDialog.showSuccess(
                    this,
                    "PDF REPORT GENERATED",
                    "Forensic Biometric PDF Dossier generated with Database Mugshot:\n" + pdfFile.getName()
            );

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(pdfFile);
            }

        } catch (Exception ex) {
            InvestigatorsPanel.GlassMessageDialog.showError(this, "PDF GENERATION ERROR", "Failed to generate PDF: " + ex.getMessage());
        }
    }

    private File resolveImageFile(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return null;

        String clean = pathStr.trim().replace("\"", "").replace("\\", "/");
        File f = new File(clean);
        if (f.exists() && f.isFile()) return f;

        f = new File(clean.replace("/", "\\"));
        if (f.exists() && f.isFile()) return f;

        return null;
    }

    private File findMugShotInDocuments(String suspectName, String caseNumber) {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile == null) userProfile = "C:/Users/Nitin";

        File mugShotsDir = new File(userProfile, "Documents/Mug Shots");
        if (!mugShotsDir.exists() || !mugShotsDir.isDirectory()) return null;

        int index = -1;
        String sLower = suspectName != null ? suspectName.toLowerCase() : "";
        String cLower = caseNumber != null ? caseNumber.toLowerCase() : "";

        if (sLower.contains("marcus") || cLower.contains("0891")) index = 1;
        else if (sLower.contains("elena") || cLower.contains("0412")) index = 2;
        else if (sLower.contains("david") || cLower.contains("1104")) index = 3;
        else if (sLower.contains("tariq") || cLower.contains("0733")) index = 4;
        else if (sLower.contains("sophia") || cLower.contains("0925")) index = 5;
        else if (sLower.contains("vikram") || cLower.contains("0318")) index = 6;

        if (index != -1) {
            String[] possibleNames = {
                    "MugShort-" + index, "MugShot-" + index,
                    "mugshort-" + index, "mugshot-" + index
            };
            String[] exts = {".jpeg", ".jpg", ".png", ".JPEG", ".JPG", ".PNG"};

            for (String base : possibleNames) {
                for (String ext : exts) {
                    File candidate = new File(mugShotsDir, base + ext);
                    if (candidate.exists() && candidate.isFile()) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private void writeForensicPdfDocument(OutputStream os, String suspectName, String caseNumber, double confidence, File queryFile, File dbSuspectImageFile) throws Exception {
        byte[] queryJpgBytes = null;
        if (queryFile != null && queryFile.exists()) {
            BufferedImage bimg = ImageIO.read(queryFile);
            if (bimg != null) queryJpgBytes = toStandardJpegBytes(bimg);
        }

        byte[] dbJpgBytes = null;
        if (dbSuspectImageFile != null && dbSuspectImageFile.exists()) {
            BufferedImage bimg = ImageIO.read(dbSuspectImageFile);
            if (bimg != null) dbJpgBytes = toStandardJpegBytes(bimg);
        }

        StringBuilder stream = new StringBuilder();

        // Title Header
        stream.append("BT\n");
        stream.append("/F2 15 Tf\n");
        stream.append("50 790 Td\n");
        stream.append("(CRIMINAL DATABASE MANAGEMENT SYSTEM) Tj\n");
        stream.append("ET\n");

        stream.append("BT\n");
        stream.append("/F1 10 Tf\n");
        stream.append("50 774 Td\n");
        stream.append("(AUTOMATED FACIAL BIOMETRIC IDENTIFICATION REPORT) Tj\n");
        stream.append("ET\n");

        stream.append("50 762 m 560 762 l S\n");

        // Section 1: QUERY IMAGE DETAILS
        String imgDimensions = (loadedQueryBufferedImage != null) ? (loadedQueryBufferedImage.getWidth() + " x " + loadedQueryBufferedImage.getHeight() + " px") : "Unknown";
        String ext = queryFile.getName().substring(queryFile.getName().lastIndexOf('.') + 1).toUpperCase();
        String fileSizeStr = formatFileSizeReadable(queryFile.length());

        stream.append("BT\n");
        stream.append("/F2 11 Tf\n");
        stream.append("50 742 Td\n");
        stream.append("(QUERY IMAGE DETAILS) Tj\n");
        stream.append("/F1 9 Tf\n");
        stream.append("0 -14 Td\n");
        stream.append("(File Name        : ").append(escapePdf(queryFile.getName())).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(File Type        : ").append(escapePdf(ext)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Image Size       : ").append(escapePdf(imgDimensions)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(File Size        : ").append(escapePdf(fileSizeStr)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Face Detected    : Yes) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Faces Detected   : 1) Tj\n");

        // Section 2: SYSTEM AUDIT
        String analysisId = "AUD-" + (System.currentTimeMillis() % 1000000);
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date());

        stream.append("0 -20 Td\n");
        stream.append("/F2 11 Tf\n");
        stream.append("(SYSTEM AUDIT) Tj\n");
        stream.append("/F1 9 Tf\n");
        stream.append("0 -14 Td\n");
        stream.append("(Analysis ID      : ").append(escapePdf(analysisId)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Performed By     : ").append(escapePdf(this.investigatorFullName)).append(" [").append(escapePdf(this.investigatorBadgeId)).append("]) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(User Role        : FIELD INVESTIGATOR) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Analysis Date    : ").append(escapePdf(dateStr)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Analysis Time    : ").append(escapePdf(timeStr)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(System Version   : v2.4.0-FORENSIC-RELEASE) Tj\n");

        // Section 3: MATCH DECISION
        boolean matchOk = confidence >= 68.0;
        stream.append("0 -20 Td\n");
        stream.append("/F2 11 Tf\n");
        stream.append("(MATCH DECISION) Tj\n");
        stream.append("/F1 9 Tf\n");
        stream.append("0 -14 Td\n");
        stream.append("(Best Candidate   : ").append(escapePdf(suspectName.toUpperCase())).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Similarity Score : ").append(String.format("%.2f%%", confidence)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Decision Threshold: 68.00%) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Result           : ").append(matchOk ? "MATCH IDENTIFIED" : "NO MATCH").append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Verification     : ").append(matchOk ? "CONFIRMED POSITIVE" : "UNVERIFIED").append(") Tj\n");

        // Section 4: MODEL INFORMATION
        stream.append("0 -20 Td\n");
        stream.append("/F2 11 Tf\n");
        stream.append("(MODEL INFORMATION) Tj\n");
        stream.append("/F1 9 Tf\n");
        stream.append("0 -14 Td\n");
        stream.append("(Model             : FaceNet InceptionResnetV1) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Model Version     : VGGFace2-Pretrained-v1) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Model Format      : PyTorch TorchScript StateDict) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Embedding Size    : 512 dimension) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Framework/Runtime : PyTorch 2.4 / CUDA Python 3.12) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Model Source      : facenet-pytorch / Oxford VGGFace2) Tj\n");

        // Section 5: FACE DETECTION
        stream.append("0 -20 Td\n");
        stream.append("/F2 11 Tf\n");
        stream.append("(FACE DETECTION) Tj\n");
        stream.append("/F1 9 Tf\n");
        stream.append("0 -14 Td\n");
        stream.append("(Detection Status     : Successful) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Detected Face Count : 1) Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Face Bounding Box   : ").append(escapePdf(lastBboxStr)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Detection Confidence: ").append(String.format("%.2f%%", lastDetConf)).append(") Tj\n");
        stream.append("0 -13 Td\n");
        stream.append("(Face Quality        : HIGH - SHARP ORIENTATION (MTCNN ALIGNED)) Tj\n");

        stream.append("ET\n");

        // Embed Both Photos on Right Column: Query & Enrolled Database Mugshot
        stream.append("BT\n/F2 9 Tf\n380 722 Td\n([QUERY SUBMISSION]) Tj\nET\n");
        stream.append("q\n150 0 0 150 380 560 cm\n/ImQuery Do\nQ\n");

        stream.append("BT\n/F2 9 Tf\n380 522 Td\n([DATABASE ENROLLED MUGSHOT]) Tj\nET\n");
        stream.append("q\n150 0 0 150 380 360 cm\n/ImDb Do\nQ\n");

        byte[] streamBytes = stream.toString().getBytes("US-ASCII");

        List<Long> offsets = new ArrayList<>();
        CountingOutputStream cos = new CountingOutputStream(os);

        cos.write("%PDF-1.4\n".getBytes("US-ASCII"));

        // 1 0 obj: Catalog
        offsets.add(cos.getCount());
        cos.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes("US-ASCII"));

        // 2 0 obj: Pages
        offsets.add(cos.getCount());
        cos.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes("US-ASCII"));

        // 3 0 obj: Page (Registers /ImQuery 7 0 R and /ImDb 8 0 R)
        offsets.add(cos.getCount());
        cos.write("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> /XObject << /ImQuery 7 0 R /ImDb 8 0 R >> >> >>\nendobj\n".getBytes("US-ASCII"));

        // 4 0 obj: Stream
        offsets.add(cos.getCount());
        cos.write(("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n").getBytes("US-ASCII"));
        cos.write(streamBytes);
        cos.write("\nendstream\nendobj\n".getBytes("US-ASCII"));

        // 5 0 obj: Font Regular
        offsets.add(cos.getCount());
        cos.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes("US-ASCII"));

        // 6 0 obj: Font Bold
        offsets.add(cos.getCount());
        cos.write("6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n".getBytes("US-ASCII"));

        // 7 0 obj: Query Image
        offsets.add(cos.getCount());
        if (queryJpgBytes != null) {
            writePdfImageObject(cos, 7, queryJpgBytes, 160, 160);
        } else {
            byte[] dummy = createDummyPlaceholderJpeg();
            writePdfImageObject(cos, 7, dummy, 160, 160);
        }

        // 8 0 obj: Database Mugshot
        offsets.add(cos.getCount());
        if (dbJpgBytes != null) {
            writePdfImageObject(cos, 8, dbJpgBytes, 160, 160);
        } else {
            byte[] dummy = createDummyPlaceholderJpeg();
            writePdfImageObject(cos, 8, dummy, 160, 160);
        }

        long xrefPos = cos.getCount();
        cos.write("xref\n0 9\n0000000000 65535 f \n".getBytes("US-ASCII"));
        for (long off : offsets) {
            cos.write(String.format("%010d 00000 n \n", off).getBytes("US-ASCII"));
        }

        cos.write(("trailer\n<< /Size 9 /Root 1 0 R >>\nstartxref\n" + xrefPos + "\n%%EOF\n").getBytes("US-ASCII"));
    }

    private static void writePdfImageObject(CountingOutputStream cos, int objNum, byte[] jpgBytes, int width, int height) throws Exception {
        cos.write((objNum + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpgBytes.length + " >>\nstream\n").getBytes("US-ASCII"));
        cos.write(jpgBytes);
        cos.write("\nendstream\nendobj\n".getBytes("US-ASCII"));
    }

    private static byte[] toStandardJpegBytes(BufferedImage img) throws Exception {
        BufferedImage rgbImage = new BufferedImage(160, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 160, 160, null);
        g.dispose();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ImageIO.write(rgbImage, "jpg", baos);
        return baos.toByteArray();
    }

    private static byte[] createDummyPlaceholderJpeg() {
        try {
            BufferedImage bi = new BufferedImage(160, 160, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.setColor(new Color(25, 33, 48));
            g.fillRect(0, 0, 160, 160);
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("NO IMAGE RECORD", 20, 85);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(bi, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    // =========================================================================
    // HELPER STREAM TO TRACK BYTE OFFSETS FOR NATIVE PDF GENERATION
    // =========================================================================
    private static class CountingOutputStream extends java.io.FilterOutputStream {
        private long count = 0;

        public CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws java.io.IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            out.write(b, off, len);
            count += len;
        }

        public long getCount() {
            return count;
        }
    }

    // =========================================================================
    // IN-PLACE LASER SCAN VIEWPORT (ANIMATES ON THE SAME PAGE)
    // =========================================================================
    private static class InPlaceLaserScanViewport extends JPanel {
        private BufferedImage displayImage = null;
        private boolean isScanning = false;
        private float scanY = 0f;
        private boolean scanDown = true;
        private final Timer laserTimer;

        public InPlaceLaserScanViewport() {
            setOpaque(false);
            laserTimer = new Timer(16, e -> {
                if (isScanning) {
                    if (scanDown) {
                        scanY += 0.035f;
                        if (scanY >= 1.0f) { scanY = 1.0f; scanDown = false; }
                    } else {
                        scanY -= 0.035f;
                        if (scanY <= 0.0f) { scanY = 0.0f; scanDown = true; }
                    }
                    repaint();
                }
            });
        }

        public void setImage(BufferedImage img) {
            this.displayImage = img;
            this.isScanning = false;
            repaint();
        }

        public void startScanning() {
            this.isScanning = true;
            this.scanY = 0f;
            this.scanDown = true;
            laserTimer.start();
        }

        public void stopScanning() {
            this.isScanning = false;
            if (laserTimer.isRunning()) laserTimer.stop();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(6, 9, 16));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);

            if (displayImage != null) {
                int pad = 12;
                int maxImgW = w - (pad * 2);
                int maxImgH = h - (pad * 2);

                double scale = Math.min((double) maxImgW / displayImage.getWidth(), (double) maxImgH / displayImage.getHeight());
                int imgW = (int) (displayImage.getWidth() * scale);
                int imgH = (int) (displayImage.getHeight() * scale);
                int imgX = (w - imgW) / 2;
                int imgY = (h - imgH) / 2;

                g2.drawImage(displayImage, imgX, imgY, imgW, imgH, null);

                // Viewport border
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(imgX - 2, imgY - 2, imgW + 4, imgH + 4, 8, 8);

                // Sweeping laser animation
                if (isScanning) {
                    int beamY = imgY + (int) (scanY * imgH);

                    GradientPaint beamTrail = new GradientPaint(
                            imgX, beamY - 16, new Color(56, 189, 248, 0),
                            imgX, beamY, new Color(56, 189, 248, 90)
                    );
                    g2.setPaint(beamTrail);
                    g2.fillRect(imgX, beamY - 16, imgW, 16);

                    g2.setColor(new Color(56, 189, 248, 240));
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.drawLine(imgX, beamY, imgX + imgW, beamY);
                }
            } else {
                int cx = w / 2;
                int cy = h / 2;
                g2.setColor(new Color(56, 189, 248, 80));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(cx - 45, cy - 45, 90, 90);
                g2.drawLine(cx - 60, cy, cx + 60, cy);
                g2.drawLine(cx, cy - 60, cx, cy + 60);

                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.setColor(new Color(148, 163, 184));
                String prompt = "Click 'Upload Photo' to Stage Target";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(prompt, cx - fm.stringWidth(prompt) / 2, cy + 78);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // SIDEWAYS VERTICAL CONFIDENCE BAR (BOTTOM TO TOP FILL)
    // =========================================================================
    private static class VerticalConfidenceBar extends JComponent {
        private float currentPct = 0f;
        private float targetPct = 0f;
        private Timer meterTimer;

        public VerticalConfidenceBar() {
            setPreferredSize(new Dimension(50, 320));
            setOpaque(false);

            meterTimer = new Timer(15, e -> {
                if (currentPct < targetPct) {
                    currentPct = Math.min(targetPct, currentPct + 2.0f);
                    repaint();
                    if (currentPct >= targetPct) {
                        ((Timer) e.getSource()).stop();
                    }
                } else {
                    ((Timer) e.getSource()).stop();
                }
            });
        }

        public void reset() {
            if (meterTimer.isRunning()) meterTimer.stop();
            this.currentPct = 0f;
            this.targetPct = 0f;
            repaint();
        }

        public void setCurrentPct(float pct) {
            this.currentPct = pct;
            this.targetPct = pct;
            repaint();
        }

        public void animateTo(double target) {
            if (meterTimer.isRunning()) meterTimer.stop();
            this.currentPct = 0f;
            this.targetPct = (float) Math.max(0.0, Math.min(100.0, target));
            meterTimer.start();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int trackW = 28;
            int trackX = (w - trackW) / 2;
            int trackY = 24;
            int trackH = h - 34;

            // Track Background
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(trackX, trackY, trackW, trackH, 8, 8);
            g2.setColor(new Color(56, 189, 248, 80));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(trackX, trackY, trackW, trackH, 8, 8);

            // Bottom-to-Top Fill
            int fillH = (int) ((currentPct / 100.0f) * (trackH - 4));
            if (fillH > 0) {
                Color barColor;
                if (currentPct < 50.0f) barColor = new Color(239, 68, 68);
                else if (currentPct < 68.0f) barColor = new Color(251, 191, 36);
                else barColor = new Color(52, 211, 153);

                int fillY = (trackY + trackH - 2) - fillH;
                GradientPaint gp = new GradientPaint(
                        trackX, trackY + trackH, new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 120),
                        trackX, fillY, barColor
                );
                g2.setPaint(gp);
                g2.fillRoundRect(trackX + 2, fillY, trackW - 4, fillH, 6, 6);
            }

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            String str = String.format("%.0f%%", currentPct);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(str, (w - fm.stringWidth(str)) / 2, 16);

            g2.dispose();
        }
    }

    // =========================================================================
    // 6. PROFILE WORKSPACE
    // =========================================================================
    private JPanel createProfileWorkspace() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);

        UITheme.GlassCardPanel card = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
        card.setLayout(new GridLayout(6, 1, 0, 8));
        card.setBorder(new EmptyBorder(22, 28, 22, 28));
        card.setPreferredSize(new Dimension(460, 280));

        JLabel title = new JLabel("OFFICER CREDENTIALS & CLEARANCE", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(UITheme.ACCENT_CYAN);

        card.add(title);
        card.add(createProfileRow("Full Name:", investigatorFullName));
        card.add(createProfileRow("Badge / ID:", investigatorBadgeId));
        card.add(createProfileRow("Terminal Username:", investigatorUsername));
        card.add(createProfileRow("Clearance Status:", "ACTIVE / COMMISSIONED"));
        card.add(createProfileRow("Department Role:", "FIELD INVESTIGATOR"));

        p.add(card);
        return p;
    }

    private JPanel createProfileRow(String label, String val) {
        JPanel r = new JPanel(new BorderLayout());
        r.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(148, 163, 184));

        JLabel v = new JLabel(val);
        v.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        v.setForeground(Color.WHITE);

        r.add(l, BorderLayout.WEST);
        r.add(v, BorderLayout.EAST);
        return r;
    }

    private JTable buildStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setOpaque(false);
        table.setBackground(new Color(15, 23, 42));
        table.setForeground(UITheme.TEXT_WHITE);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setSelectionBackground(new Color(56, 189, 248, 45));
        table.setSelectionForeground(Color.WHITE);

        table.getTableHeader().setBackground(new Color(20, 27, 40));
        table.getTableHeader().setForeground(new Color(148, 163, 184));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.getTableHeader().setPreferredSize(new Dimension(0, 32));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_GLASS));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, isSel, hasFocus, r, c);
                setBorder(new EmptyBorder(0, 10, 0, 10));

                int statusColIdx = t.getColumnCount() - 1;
                Object statusVal = t.getValueAt(r, statusColIdx);
                String stStr = statusVal != null ? statusVal.toString().trim().toUpperCase() : "";
                boolean isClosed = stStr.contains("CLOSED");
                boolean isFlagged = stStr.contains("FLAGGED");

                if (isClosed) {
                    if (!isSel) {
                        comp.setBackground(r % 2 == 0 ? new Color(7, 10, 15) : new Color(4, 6, 10));
                        comp.setForeground(new Color(100, 116, 139));
                    } else {
                        comp.setBackground(new Color(30, 41, 59, 160));
                        comp.setForeground(new Color(148, 163, 184));
                    }
                } else if (isFlagged) {
                    if (!isSel) {
                        comp.setBackground(r % 2 == 0 ? new Color(38, 18, 22) : new Color(30, 14, 18));
                        comp.setForeground(c == 0 ? new Color(248, 113, 113) : Color.WHITE);
                    } else {
                        comp.setBackground(new Color(239, 68, 68, 60));
                        comp.setForeground(Color.WHITE);
                    }
                } else {
                    if (!isSel) {
                        comp.setBackground(r % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140));
                        comp.setForeground(c == 0 ? UITheme.ACCENT_CYAN : Color.WHITE);
                    } else {
                        comp.setBackground(new Color(56, 189, 248, 45));
                        comp.setForeground(Color.WHITE);
                    }
                }
                return comp;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
        return table;
    }

    private JPanel buildKpiCard(String title, JLabel valLbl, Color accent) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(24, 32, 47, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.setForeground(new Color(148, 163, 184));

        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valLbl.setForeground(accent);

        card.add(t, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        return card;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        return btn;
    }

    private static class AnimatedRequestBadge extends JComponent {
        private int count = 0;
        public AnimatedRequestBadge() { setPreferredSize(new Dimension(86, 26)); }
        public void setCount(int c) { this.count = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color baseColor = count > 0 ? new Color(251, 191, 36) : new Color(148, 163, 184);
            g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 35));
            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
            g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 160));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(baseColor);
            String text = count + " Requests";
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    private static class AnimatedStatusBadgeRenderer extends DefaultTableCellRenderer {
        public AnimatedStatusBadgeRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim().toUpperCase() : "PENDING";
            boolean isClosed = text.contains("CLOSED");
            boolean isFlagged = text.contains("FLAGGED") || text.contains("REJECTED");

            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            wrap.setOpaque(true);

            if (isClosed) {
                wrap.setBackground(isSelected ? new Color(30, 41, 59, 160) : (row % 2 == 0 ? new Color(7, 10, 15) : new Color(4, 6, 10)));
            } else if (isFlagged) {
                wrap.setBackground(isSelected ? new Color(239, 68, 68, 60) : (row % 2 == 0 ? new Color(38, 18, 22) : new Color(30, 14, 18)));
            } else {
                wrap.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));
            }

            Color bg;
            Color fg;

            if (isClosed) {
                bg = new Color(20, 27, 40, 220);
                fg = new Color(100, 116, 139);
            } else if (isFlagged) {
                bg = new Color(239, 68, 68, 45);
                fg = new Color(248, 113, 113);
            } else if (text.contains("APPROVED") || text.contains("ACTIVE") || text.contains("AVAILABLE")) {
                bg = new Color(16, 185, 129, 40);
                fg = new Color(52, 211, 153);
            } else if (text.contains("REVIEW") || text.contains("PENDING")) {
                bg = new Color(56, 189, 248, 40);
                fg = UITheme.ACCENT_CYAN;
            } else {
                bg = new Color(251, 191, 36, 40);
                fg = new Color(251, 191, 36);
            }

            final Color fBg = bg;
            final Color fFg = fg;

            JLabel pill = new JLabel("  " + text + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(fBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(fFg.getRed(), fFg.getGreen(), fFg.getBlue(), isClosed ? 80 : 150));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(fFg);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));
            wrap.add(pill);
            return wrap;
        }
    }

    private static class PriorityBadgeRenderer extends DefaultTableCellRenderer {
        public PriorityBadgeRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim() : "HIGH";

            int statusColIdx = table.getColumnCount() - 1;
            Object statusVal = table.getValueAt(row, statusColIdx);
            String stStr = statusVal != null ? statusVal.toString().trim().toUpperCase() : "";
            boolean isClosed = stStr.contains("CLOSED");
            boolean isFlagged = stStr.contains("FLAGGED") || stStr.contains("REJECTED");

            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            wrap.setOpaque(true);

            if (isClosed) {
                wrap.setBackground(isSelected ? new Color(30, 41, 59, 160) : (row % 2 == 0 ? new Color(7, 10, 15) : new Color(4, 6, 10)));
            } else if (isFlagged) {
                wrap.setBackground(isSelected ? new Color(239, 68, 68, 60) : (row % 2 == 0 ? new Color(38, 18, 22) : new Color(30, 14, 18)));
            } else {
                wrap.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));
            }

            Color bg;
            Color fg;

            if (isClosed) {
                bg = new Color(20, 27, 40, 180);
                fg = new Color(100, 116, 139);
            } else {
                bg = "HIGH".equalsIgnoreCase(text) ? new Color(239, 68, 68, 35) : ("MEDIUM".equalsIgnoreCase(text) ? new Color(251, 191, 36, 35) : new Color(56, 189, 248, 35));
                fg = "HIGH".equalsIgnoreCase(text) ? new Color(248, 113, 113) : ("MEDIUM".equalsIgnoreCase(text) ? new Color(251, 191, 36) : UITheme.ACCENT_CYAN);
            }

            final Color fBg = bg;
            final Color fFg = fg;

            JLabel pill = new JLabel("  " + text.toUpperCase() + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(fBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(fFg.getRed(), fFg.getGreen(), fFg.getBlue(), isClosed ? 80 : 120));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(fFg);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));
            wrap.add(pill);
            return wrap;
        }
    }

    private static class VectorBellIcon extends JComponent {
        private int unreadCount = 0;
        private float ringAngle = 0f;
        private float pulseAlpha = 0f;
        private Timer ringTimer;
        private boolean isHovered = false;

        public VectorBellIcon() {
            setPreferredSize(new Dimension(34, 34));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
            });
        }

        public void setUnreadCount(int count) {
            this.unreadCount = count;
            if (count > 0 && ringTimer == null) startRingAnimation();
            else if (count == 0 && ringTimer != null) {
                ringTimer.stop();
                ringTimer = null;
                ringAngle = 0f;
                pulseAlpha = 0f;
            }
            repaint();
        }

        private void startRingAnimation() {
            ringTimer = new Timer(35, e -> {
                long now = System.currentTimeMillis();
                double cycle = (now % 2400) / 2400.0;
                if (cycle < 0.3) ringAngle = (float) (Math.sin(cycle * Math.PI * 10) * 14.0);
                else ringAngle = 0f;
                pulseAlpha = (float) ((Math.sin(now / 220.0) + 1.0) / 2.0);
                repaint();
            });
            ringTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = 15;
            int cy = 16;

            if (isHovered) {
                g2.setColor(new Color(56, 189, 248, 30));
                g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);
            }

            g2.rotate(Math.toRadians(ringAngle), cx, 6);

            Color bellColor = unreadCount > 0 ? new Color(251, 191, 36) : UITheme.TEXT_MUTED;
            g2.setColor(bellColor);
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            g2.drawArc(cx - 6, cy - 8, 12, 11, 0, 180);
            g2.drawLine(cx - 6, cy - 3, cx - 9, cy + 5);
            g2.drawLine(cx + 6, cy - 3, cx + 9, cy + 5);
            g2.drawLine(cx - 10, cy + 5, cx + 10, cy + 5);

            g2.drawArc(cx - 3, cy + 5, 6, 4, 180, 180);
            g2.drawOval(cx - 1, cy - 10, 2, 2);

            if (unreadCount > 0) {
                g2.setTransform(((Graphics2D) g).getTransform());
                int bx = getWidth() - 11;
                int by = 5;
                int glowAlpha = (int) (pulseAlpha * 140);
                g2.setColor(new Color(239, 68, 68, glowAlpha));
                g2.fillOval(bx - 3, by - 3, 13, 13);
                g2.setColor(new Color(239, 68, 68));
                g2.fillOval(bx, by, 7, 7);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawOval(bx, by, 7, 7);
            }

            g2.dispose();
        }
    }

    private static class ZoomSidebarItem extends JPanel {
        private final String title;
        private final boolean isDanger;
        private boolean isSelected;
        private float hoverScale = 0f;
        private Timer hoverTimer;

        public ZoomSidebarItem(String title, boolean isSelected, boolean isDanger) {
            this.title = title;
            this.isSelected = isSelected;
            this.isDanger = isDanger;
            setOpaque(false);
            setPreferredSize(new Dimension(230, 38));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { animateHover(true); }
                @Override public void mouseExited(MouseEvent e) { animateHover(false); }
            });
        }

        public String getTitle() { return title; }
        public void setSelected(boolean sel) { this.isSelected = sel; repaint(); }

        private void animateHover(boolean forward) {
            if (hoverTimer != null && hoverTimer.isRunning()) hoverTimer.stop();
            final long startTime = System.currentTimeMillis();
            final float startScale = hoverScale;
            final float targetScale = forward ? 1.0f : 0.0f;

            hoverTimer = new Timer(15, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(1.0f, elapsed / 120f);
                hoverScale = startScale + (targetScale - startScale) * t;
                repaint();
                if (t >= 1.0f) {
                    hoverScale = targetScale;
                    ((Timer) e.getSource()).stop();
                }
            });
            hoverTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int zoomOffset = (int) (hoverScale * 4);
            int w = getWidth() - 8;
            int h = getHeight() - 4;

            Color accent = isDanger ? new Color(239, 68, 68) : new Color(52, 211, 153);

            if (isSelected) {
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35));
                g2.fillRoundRect(4 + zoomOffset, 2, w - zoomOffset, h, 8, 8);
                g2.setColor(accent);
                g2.fillRect(4 + zoomOffset, 8, 3, h - 12);
            } else if (hoverScale > 0.01f) {
                int alpha = isDanger ? (int) (hoverScale * 40) : (int) (hoverScale * 18);
                g2.setColor(isDanger ? new Color(239, 68, 68, alpha) : new Color(255, 255, 255, alpha));
                g2.fillRoundRect(4 + zoomOffset, 2, w - zoomOffset, h, 8, 8);
            }

            int textX = 22 + zoomOffset;

            if (isDanger) {
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int iconX = 14 + zoomOffset;
                int iconY = getHeight() / 2 - 6;
                g2.drawRect(iconX, iconY, 7, 12);
                g2.drawLine(iconX + 4, iconY + 6, iconX + 11, iconY + 6);
                g2.drawLine(iconX + 9, iconY + 3, iconX + 12, iconY + 6);
                g2.drawLine(iconX + 9, iconY + 9, iconX + 12, iconY + 6);
                textX = 34 + zoomOffset;
            }

            g2.setFont(new Font("Segoe UI", (isSelected || isDanger) ? Font.BOLD : Font.PLAIN, 13));
            g2.setColor(isSelected ? accent : (isDanger ? new Color(248, 113, 113) : Color.WHITE));
            FontMetrics fm = g2.getFontMetrics();
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(title, textX, textY);
            g2.dispose();
        }
    }

    private static class MenuVectorButton extends JButton {
        private boolean isOpen = false;
        private boolean isHovered = false;

        public MenuVectorButton() {
            setPreferredSize(new Dimension(86, 32));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
            });
        }

        public void setOpenState(boolean open) { this.isOpen = open; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(isHovered ? new Color(52, 211, 153, 35) : new Color(25, 33, 48));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(new Color(52, 211, 153, isHovered ? 140 : 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            g2.setColor(new Color(52, 211, 153));
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if (!isOpen) {
                g2.drawLine(12, 10, 24, 10);
                g2.drawLine(12, 16, 24, 16);
                g2.drawLine(12, 22, 24, 22);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString("MENU", 34, 20);
            } else {
                g2.drawLine(12, 10, 24, 22);
                g2.drawLine(24, 10, 12, 22);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString("CLOSE", 32, 20);
            }
            g2.dispose();
        }
    }

    private static class VectorCloseButton extends JButton {
        public VectorCloseButton() {
            setPreferredSize(new Dimension(24, 24));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UITheme.TEXT_MUTED);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(6, 6, getWidth() - 6, getHeight() - 6);
            g2.drawLine(getWidth() - 6, 6, 6, getHeight() - 6);
            g2.dispose();
        }
    }
}