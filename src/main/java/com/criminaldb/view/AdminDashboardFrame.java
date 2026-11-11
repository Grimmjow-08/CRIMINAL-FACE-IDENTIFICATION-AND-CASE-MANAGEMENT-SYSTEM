package com.criminaldb.view;

import com.criminaldb.config.DBConnection;
import com.criminaldb.util.AuditLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminDashboardFrame extends JFrame {

    private final String adminName;
    private AnimatedNumberLabel totalCasesVal;
    private AnimatedNumberLabel activeCasesVal;
    private AnimatedNumberLabel pendingRequestsVal;
    private AnimatedNumberLabel closedCasesVal;
    private JLabel pendingBadgeCounterLbl;
    private DefaultTableModel requestsTableModel;
    private JTable requestsTable;
    private JLabel welcomeLbl;

    private CardLayout workspaceCardLayout;
    private JPanel workspaceContainer;

    private JPanel slidingSidebar;
    private Timer drawerTimer;
    private float drawerProgress = 0f;
    private boolean isDrawerOpen = false;
    private final int DRAWER_TARGET_WIDTH = 250;
    private MenuVectorButton menuToggleBtn;

    private String currentActiveTab = "Dashboard";
    private final List<ZoomSidebarItem> sidebarItems = new ArrayList<>();

    private JLayeredPane layeredRoot;
    private JPanel appPanel;
    private CinematicIntroOverlay introOverlay;

    private InvestigatorsPanel investigatorsPanel;
    private CaseRequestsPanel caseRequestsPanel;
    private EvidencePanel evidencePanel;
    private ReportsPanel reportsPanel;
    private AuditLogsPanel auditLogsPanel;

    private VectorBellIcon notifBell;
    private final List<NotificationsDialog.NotificationItem> currentNotifications = Collections.synchronizedList(new ArrayList<>());
    private Timer pollingTimer;

    public AdminDashboardFrame(String adminName) {
        this.adminName = adminName != null ? adminName : "Head Administrator";
        setTitle("Criminal Database Management System - Admin Terminal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setSize(1080, 750);
        setResizable(false);
        setLocationRelativeTo(null);

        initUI();

        loadRealDashboardData();
        fetchNotifications();

        SwingUtilities.invokeLater(() -> introOverlay.startCinematicSequence());

        pollingTimer = new Timer(4000, e -> fetchNotifications());
        pollingTimer.start();
    }

    private void initUI() {
        layeredRoot = new JLayeredPane();
        layeredRoot.setPreferredSize(new Dimension(1080, 750));

        appPanel = new JPanel(new BorderLayout()) {
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
        appPanel.setBounds(0, 0, 1080, 750);

        appPanel.add(createTopBar(), BorderLayout.NORTH);

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);

        slidingSidebar = createSlidingSidebar();
        centerContainer.add(slidingSidebar, BorderLayout.WEST);

        workspaceCardLayout = new CardLayout();
        workspaceContainer = new JPanel(workspaceCardLayout);
        workspaceContainer.setOpaque(false);

        workspaceContainer.add(createDashboardOverviewWorkspace(), "Dashboard");

        CasesMainPanel casesMainView = new CasesMainPanel(adminName, this::loadRealDashboardData);
        workspaceContainer.add(casesMainView, "Cases");

        investigatorsPanel = new InvestigatorsPanel();
        workspaceContainer.add(investigatorsPanel, "Investigators");

        caseRequestsPanel = new CaseRequestsPanel(adminName, this::loadRealDashboardData);
        workspaceContainer.add(caseRequestsPanel, "Case Requests");

        evidencePanel = new EvidencePanel();
        workspaceContainer.add(evidencePanel, "Evidence");

        reportsPanel = new ReportsPanel();
        workspaceContainer.add(reportsPanel, "Reports");

        auditLogsPanel = new AuditLogsPanel();
        workspaceContainer.add(auditLogsPanel, "Audit Logs");

        centerContainer.add(workspaceContainer, BorderLayout.CENTER);
        appPanel.add(centerContainer, BorderLayout.CENTER);

        introOverlay = new CinematicIntroOverlay();
        introOverlay.setBounds(0, 0, 1080, 750);

        layeredRoot.add(appPanel, JLayeredPane.DEFAULT_LAYER);
        layeredRoot.add(introOverlay, JLayeredPane.POPUP_LAYER);

        setContentPane(layeredRoot);
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

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 13));
        rightActions.setOpaque(false);

        notifBell = new VectorBellIcon();
        notifBell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openNotificationsDialog();
            }
        });

        JLabel adminPill = new JLabel("  " + adminName + " [ADMIN]  ");
        adminPill.setFont(new Font("Segoe UI", Font.BOLD, 11));
        adminPill.setForeground(UITheme.ACCENT_CYAN);
        adminPill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248, 80), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));

        rightActions.add(adminPill);
        rightActions.add(notifBell);
        bar.add(rightActions, BorderLayout.EAST);

        return bar;
    }

    private void openNotificationsDialog() {
        NotificationsDialog dialog = new NotificationsDialog(this, new ArrayList<>(currentNotifications), this::switchWorkspaceView);
        dialog.setVisible(true);
    }

    private void fetchNotifications() {
        new Thread(() -> {
            List<NotificationsDialog.NotificationItem> freshList = new ArrayList<>();

            // 1. Access Requests
            String invSql = "SELECT full_name, username, id_number, created_at FROM users WHERE UPPER(status) = 'PENDING' ORDER BY created_at DESC";

            // 2. Incoming Investigator Requests
            String caseSql = "SELECT case_number, title, created_by, status, created_at FROM cases " +
                    "WHERE UPPER(status) = 'CLAIM_PENDING' " +
                    "   OR (UPPER(status) = 'PENDING' AND LOWER(TRIM(COALESCE(created_by, ''))) NOT IN ('admin', 'head administrator')) " +
                    "ORDER BY id DESC LIMIT 10";

            // 3. New Evidence Filings from Investigators
            String evSql = "SELECT e.evidence_number, e.title, COALESCE(c.case_number, e.case_number) AS c_num, e.collected_by, e.uploaded_at " +
                    "FROM case_evidence e LEFT JOIN cases c ON e.case_id = c.id " +
                    "WHERE UPPER(e.status) = 'PENDING REVIEW' ORDER BY e.id DESC LIMIT 6";

            try (Connection conn = DBConnection.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(invSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            freshList.add(new NotificationsDialog.NotificationItem(
                                    NotificationsDialog.NotificationItem.Type.ACCESS_REQUEST,
                                    "New Login Request",
                                    rs.getString("full_name") + " requested access (" + rs.getString("username") + ")",
                                    rs.getString("created_at"),
                                    rs.getString("username")
                            ));
                        }
                    }

                    try (PreparedStatement ps = conn.prepareStatement(caseSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            freshList.add(new NotificationsDialog.NotificationItem(
                                    NotificationsDialog.NotificationItem.Type.CASE_REQUEST,
                                    "Case Claim - " + rs.getString("case_number"),
                                    rs.getString("created_by") + ": " + rs.getString("title"),
                                    rs.getString("created_at"),
                                    rs.getString("case_number")
                            ));
                        }
                    }

                    try (PreparedStatement ps = conn.prepareStatement(evSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            freshList.add(new NotificationsDialog.NotificationItem(
                                    NotificationsDialog.NotificationItem.Type.CASE_REQUEST,
                                    "New Evidence Filed: " + rs.getString("evidence_number"),
                                    "Case " + rs.getString("c_num") + " by " + rs.getString("collected_by"),
                                    rs.getString("uploaded_at"),
                                    rs.getString("evidence_number")
                            ));
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Notification poller error: " + ex.getMessage());
            }

            SwingUtilities.invokeLater(() -> {
                synchronized (currentNotifications) {
                    currentNotifications.clear();
                    currentNotifications.addAll(freshList);
                }
                if (notifBell != null) notifBell.setUnreadCount(freshList.size());
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

        JPanel navTitleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        navTitleBox.setOpaque(false);

        VectorCompassIcon compassIcon = new VectorCompassIcon();
        JLabel navTitle = new JLabel("NAVIGATION");
        navTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        navTitle.setForeground(new Color(148, 163, 184));

        navTitleBox.add(compassIcon);
        navTitleBox.add(navTitle);

        VectorCloseButton closeBtn = new VectorCloseButton();
        closeBtn.addActionListener(e -> toggleDrawer(false));

        header.add(navTitleBox, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        sidebar.add(header, BorderLayout.NORTH);

        JPanel itemsContainer = new JPanel();
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
        itemsContainer.setOpaque(false);
        itemsContainer.setBorder(new EmptyBorder(6, 8, 14, 8));

        String[] menuItems = {
                "Dashboard", "Cases", "Investigators", "Case Requests",
                "Evidence", "Reports", "Audit Logs", "Log out"
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

    public void switchWorkspaceView(String viewName) {
        if ("Log out".equalsIgnoreCase(viewName)) {
            boolean confirmed = InvestigatorsPanel.GlassConfirmDialog.confirm(
                    this,
                    "TERMINATE SESSION",
                    "Are you sure you want to log out of the Head Administrator console?"
            );
            if (confirmed) {
                AuditLogger.log(adminName, "SECURITY SESSION: Administrator session terminated via console logout.");
                if (pollingTimer != null) pollingTimer.stop();
                dispose();
                new LoginFrame().setVisible(true);
            }
            return;
        }

        this.currentActiveTab = viewName;
        for (ZoomSidebarItem it : sidebarItems) {
            it.setSelected(it.getTitle().equalsIgnoreCase(viewName));
        }

        if ("Dashboard".equalsIgnoreCase(viewName)) loadRealDashboardData();
        else if ("Investigators".equalsIgnoreCase(viewName) && investigatorsPanel != null) {
            investigatorsPanel.loadAllInvestigators();
            investigatorsPanel.loadPendingApprovals();
        } else if ("Case Requests".equalsIgnoreCase(viewName) && caseRequestsPanel != null) {
            caseRequestsPanel.loadPendingCaseRequests();
        } else if ("Evidence".equalsIgnoreCase(viewName) && evidencePanel != null) {
            evidencePanel.loadEvidenceData();
        } else if ("Reports".equalsIgnoreCase(viewName) && reportsPanel != null) {
            reportsPanel.loadAllReportData();
        } else if ("Audit Logs".equalsIgnoreCase(viewName) && auditLogsPanel != null) {
            auditLogsPanel.loadAuditLogs();
        }

        workspaceCardLayout.show(workspaceContainer, viewName);
    }

    private void toggleDrawer(boolean open) {
        if (drawerTimer != null && drawerTimer.isRunning()) {
            drawerTimer.stop();
        }

        final int duration = 240;
        final long startTime = System.currentTimeMillis();
        final float startProg = drawerProgress;
        final float targetProg = open ? 1.0f : 0.0f;

        drawerTimer = new Timer(16, e -> {
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
    // DASHBOARD OVERVIEW (WITH 4 KPI CARDS INCLUDING "CASES CLOSED")
    // =========================================================================
    private JPanel createDashboardOverviewWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(14, 22, 14, 22));

        JPanel wsHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wsHeader.setOpaque(false);
        welcomeLbl = new JLabel("Welcome, " + adminName);
        welcomeLbl.setFont(new Font("Segoe UI", Font.BOLD, 19));
        welcomeLbl.setForeground(Color.WHITE);
        welcomeLbl.setVisible(false);
        wsHeader.add(welcomeLbl);

        workspace.add(wsHeader, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(10, 0, 0, 0));

        // 4 KPI Cards (Total, Active, Pending, Closed)
        JPanel statsRow = new JPanel(new GridLayout(1, 4, 12, 0));
        statsRow.setOpaque(false);
        statsRow.setPreferredSize(new Dimension(0, 95));

        totalCasesVal = new AnimatedNumberLabel(0, UITheme.ACCENT_CYAN);
        activeCasesVal = new AnimatedNumberLabel(0, new Color(52, 211, 153));
        pendingRequestsVal = new AnimatedNumberLabel(0, new Color(251, 191, 36));
        closedCasesVal = new AnimatedNumberLabel(0, new Color(168, 85, 247));

        statsRow.add(new ZoomCardPanel("Total Cases", totalCasesVal, UITheme.ACCENT_CYAN));
        statsRow.add(new ZoomCardPanel("Active Cases", activeCasesVal, new Color(52, 211, 153)));
        statsRow.add(new ZoomCardPanel("Pending Requests", pendingRequestsVal, new Color(251, 191, 36)));
        statsRow.add(new ZoomCardPanel("Cases Closed", closedCasesVal, new Color(168, 85, 247)));

        body.add(statsRow, BorderLayout.NORTH);

        UITheme.GlassCardPanel requestsCard = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
        requestsCard.setLayout(new BorderLayout(0, 10));
        requestsCard.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel reqHeaderPanel = new JPanel(new BorderLayout());
        reqHeaderPanel.setOpaque(false);

        JLabel reqHeader = new JLabel("PENDING ACTION QUEUE (UNASSIGNED / REQUIRES DISPATCH)");
        reqHeader.setFont(new Font("Segoe UI", Font.BOLD, 13));
        reqHeader.setForeground(new Color(251, 191, 36));

        pendingBadgeCounterLbl = new JLabel("0 Cases");
        pendingBadgeCounterLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        pendingBadgeCounterLbl.setForeground(new Color(251, 191, 36));
        pendingBadgeCounterLbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(251, 191, 36, 120), 1, true),
                new EmptyBorder(3, 10, 3, 10)
        ));

        reqHeaderPanel.add(reqHeader, BorderLayout.WEST);
        reqHeaderPanel.add(pendingBadgeCounterLbl, BorderLayout.EAST);
        requestsCard.add(reqHeaderPanel, BorderLayout.NORTH);

        String[] cols = {"CASE ID", "CASE TITLE", "CATEGORY", "QUEUE STATUS", "PRIORITY"};
        requestsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        requestsTable = new JTable(requestsTableModel);
        requestsTable.setOpaque(false);
        requestsTable.setBackground(new Color(0, 0, 0, 0));
        requestsTable.setForeground(UITheme.TEXT_WHITE);
        requestsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        requestsTable.setRowHeight(36);
        requestsTable.setShowGrid(false);
        requestsTable.setSelectionBackground(new Color(251, 191, 36, 35));
        requestsTable.setSelectionForeground(Color.WHITE);

        requestsTable.getTableHeader().setBackground(new Color(20, 27, 40));
        requestsTable.getTableHeader().setForeground(new Color(148, 163, 184));
        requestsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        requestsTable.getTableHeader().setPreferredSize(new Dimension(0, 32));
        requestsTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(251, 191, 36, 80)));

        requestsTable.setDefaultRenderer(Object.class, new DashboardRowRenderer());
        requestsTable.getColumnModel().getColumn(3).setCellRenderer(new PendingPillRenderer());
        requestsTable.getColumnModel().getColumn(4).setCellRenderer(new PriorityPillRenderer());

        JScrollPane tableScroll = new JScrollPane(requestsTable);
        tableScroll.setOpaque(false);
        tableScroll.getViewport().setOpaque(false);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(251, 191, 36, 60), 1));
        requestsCard.add(tableScroll, BorderLayout.CENTER);

        body.add(requestsCard, BorderLayout.CENTER);
        workspace.add(body, BorderLayout.CENTER);

        return workspace;
    }

    public void loadRealDashboardData() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            int total = 0, active = 0, pending = 0, closed = 0;
            List<Object[]> tableRows = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                try (Connection conn = DBConnection.getConnection()) {
                    if (conn == null) return null;

                    String countSql = "SELECT " +
                            "COUNT(*) AS total, " +
                            "COALESCE(SUM(CASE WHEN UPPER(TRIM(status)) = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_cases, " +
                            "COALESCE(SUM(CASE WHEN UPPER(TRIM(status)) IN ('PENDING', 'CLAIM_PENDING') THEN 1 ELSE 0 END), 0) AS pending_cases, " +
                            "COALESCE(SUM(CASE WHEN UPPER(TRIM(status)) = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_cases " +
                            "FROM cases WHERE UPPER(TRIM(status)) != 'REJECTED'";

                    try (PreparedStatement ps = conn.prepareStatement(countSql);
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            total = rs.getInt("total");
                            active = rs.getInt("active_cases");
                            pending = rs.getInt("pending_cases");
                            closed = rs.getInt("closed_cases");
                        }
                    }

                    String pendSql = "SELECT case_number, title, category, status, priority FROM cases " +
                            "WHERE UPPER(TRIM(status)) IN ('PENDING', 'CLAIM_PENDING') " +
                            "   OR (UPPER(TRIM(status)) = 'ACTIVE' AND (assigned_officer IS NULL OR assigned_officer = '' OR assigned_officer = 'Unassigned')) " +
                            "ORDER BY id DESC LIMIT 15";
                    try (PreparedStatement ps = conn.prepareStatement(pendSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String num = rs.getString("case_number");
                            String title = rs.getString("title");
                            String cat = rs.getString("category");
                            String st = rs.getString("status");
                            String prio = rs.getString("priority");

                            String queueLabel = "CLAIM_PENDING".equalsIgnoreCase(st) ? "CLAIM REQUEST" :
                                    ("PENDING".equalsIgnoreCase(st) ? "PENDING POOL" : "UNASSIGNED ACTIVE");

                            tableRows.add(new Object[]{
                                    num != null ? num : "--",
                                    title != null ? title : "--",
                                    cat != null ? cat : "--",
                                    queueLabel,
                                    prio != null ? prio : "HIGH"
                            });
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Dashboard query error: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    if (totalCasesVal != null) totalCasesVal.setTargetValue(total);
                    if (activeCasesVal != null) activeCasesVal.setTargetValue(active);
                    if (pendingRequestsVal != null) pendingRequestsVal.setTargetValue(pending);
                    if (closedCasesVal != null) closedCasesVal.setTargetValue(closed);
                    if (pendingBadgeCounterLbl != null) pendingBadgeCounterLbl.setText(tableRows.size() + " Cases");

                    if (requestsTableModel != null) {
                        requestsTableModel.setRowCount(0);
                        if (tableRows.isEmpty()) requestsTableModel.addRow(new Object[]{"--", "No Pending Cases in Queue", "--", "CLEARED", "NONE"});
                        else for (Object[] row : tableRows) requestsTableModel.addRow(row);
                    }

                    if (requestsTable != null) {
                        requestsTable.revalidate();
                        requestsTable.repaint();
                    }
                });
            }
        };
        worker.execute();
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

    public static class AnimatedNumberLabel extends JComponent {
        private int currentValue;
        private int previousValue;
        private final Color baseColor;

        private float animProgress = 1.0f;
        private boolean isIncreasing = true;
        private Timer animTimer;

        public AnimatedNumberLabel(int initialValue, Color baseColor) {
            this.currentValue = initialValue;
            this.previousValue = initialValue;
            this.baseColor = baseColor;
            setPreferredSize(new Dimension(100, 38));
        }

        public void setTargetValue(int newValue) {
            if (newValue == currentValue && animProgress >= 1.0f) {
                repaint();
                return;
            }

            if (animTimer != null && animTimer.isRunning()) animTimer.stop();

            this.previousValue = this.currentValue;
            this.currentValue = newValue;
            this.isIncreasing = (newValue >= previousValue);
            this.animProgress = 0.0f;

            final long startTime = System.currentTimeMillis();
            final int duration = 380;

            animTimer = new Timer(15, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(1.0f, (float) elapsed / duration);
                animProgress = 1.0f - (float) Math.pow(1.0f - t, 3);
                repaint();

                if (t >= 1.0f) {
                    animTimer.stop();
                    animProgress = 1.0f;
                    repaint();
                }
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            Font font = new Font("Segoe UI", Font.BOLD, 24);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();

            int baselineY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

            if (animProgress >= 1.0f) {
                g2.setColor(baseColor);
                g2.drawString(String.valueOf(currentValue), 0, baselineY);
            } else {
                int maxOffset = 14;
                int offsetY = isIncreasing
                        ? (int) ((1.0f - animProgress) * maxOffset)
                        : (int) ((1.0f - animProgress) * -maxOffset);

                Color flashColor = isIncreasing ? new Color(52, 211, 153) : new Color(248, 113, 113);
                int r = (int) (flashColor.getRed() * (1f - animProgress) + baseColor.getRed() * animProgress);
                int gr = (int) (flashColor.getGreen() * (1f - animProgress) + baseColor.getGreen() * animProgress);
                int b = (int) (flashColor.getBlue() * (1f - animProgress) + baseColor.getBlue() * animProgress);

                g2.setColor(new Color(Math.min(255, r), Math.min(255, gr), Math.min(255, b)));
                g2.drawString(String.valueOf(currentValue), 0, baselineY - offsetY);
            }
            g2.dispose();
        }
    }

    private static class DashboardRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setBorder(new EmptyBorder(0, 10, 0, 10));

            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140));
                c.setForeground(col == 0 ? UITheme.ACCENT_CYAN : Color.WHITE);
            }
            return c;
        }
    }

    private static class PendingPillRenderer extends DefaultTableCellRenderer {
        public PendingPillRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim() : "--";
            JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            badgeWrapper.setOpaque(true);
            badgeWrapper.setBackground(isSelected ? new Color(251, 191, 36, 35) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            Color badgeBg = text.contains("CLAIM") ? new Color(56, 189, 248, 35) : (text.contains("CLEARED") ? new Color(52, 211, 153, 35) : new Color(251, 191, 36, 35));
            Color badgeText = text.contains("CLAIM") ? UITheme.ACCENT_CYAN : (text.contains("CLEARED") ? new Color(52, 211, 153) : new Color(251, 191, 36));

            JLabel pill = new JLabel("  " + text.toUpperCase() + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(badgeBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(badgeText.getRed(), badgeText.getGreen(), badgeText.getBlue(), 130));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(badgeText);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));

            badgeWrapper.add(pill);
            return badgeWrapper;
        }
    }

    private static class PriorityPillRenderer extends DefaultTableCellRenderer {
        public PriorityPillRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim() : "--";
            JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            badgeWrapper.setOpaque(true);
            badgeWrapper.setBackground(isSelected ? new Color(251, 191, 36, 35) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            Color badgeBg = ("CRITICAL".equalsIgnoreCase(text) || "HIGH".equalsIgnoreCase(text)) ? new Color(239, 68, 68, 35) :
                    ("MEDIUM".equalsIgnoreCase(text) ? new Color(251, 191, 36, 35) : new Color(56, 189, 248, 35));
            Color badgeText = ("CRITICAL".equalsIgnoreCase(text) || "HIGH".equalsIgnoreCase(text)) ? new Color(248, 113, 113) :
                    ("MEDIUM".equalsIgnoreCase(text) ? new Color(251, 191, 36) : UITheme.ACCENT_CYAN);

            JLabel pill = new JLabel("  " + text.toUpperCase() + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(badgeBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(badgeText.getRed(), badgeText.getGreen(), badgeText.getBlue(), 120));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(badgeText);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));

            badgeWrapper.add(pill);
            return badgeWrapper;
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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int zoomOffset = (int) (hoverScale * 4);
            int w = getWidth() - 8;
            int h = getHeight() - 4;

            Color accent = isDanger ? new Color(239, 68, 68) : UITheme.ACCENT_CYAN;

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
            if (isSelected) {
                g2.setColor(accent);
            } else if (isDanger) {
                g2.setColor(new Color(248, 113, 113));
            } else {
                int textAlpha = 180 + (int) (hoverScale * 75);
                g2.setColor(new Color(255, 255, 255, Math.min(255, textAlpha)));
            }

            FontMetrics fm = g2.getFontMetrics();
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(title, textX, textY);
            g2.dispose();
        }
    }

    private static class ZoomCardPanel extends JPanel {
        private final String title;
        private final JComponent valueComponent;
        private final Color accentColor;
        private float hoverProgress = 0f;
        private Timer hoverTimer;

        public ZoomCardPanel(String title, JComponent valueComponent, Color accentColor) {
            this.title = title;
            this.valueComponent = valueComponent;
            this.accentColor = accentColor;

            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(10, 16, 10, 16));

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            titleLbl.setForeground(UITheme.TEXT_MUTED);

            add(titleLbl, BorderLayout.NORTH);
            add(valueComponent, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { animateScale(true); }
                @Override public void mouseExited(MouseEvent e) { animateScale(false); }
            });
        }

        private void animateScale(boolean forward) {
            if (hoverTimer != null && hoverTimer.isRunning()) hoverTimer.stop();
            final long start = System.currentTimeMillis();
            final float startVal = hoverProgress;
            final float targetVal = forward ? 1.0f : 0.0f;

            hoverTimer = new Timer(15, e -> {
                long elapsed = System.currentTimeMillis() - start;
                float t = Math.min(1.0f, elapsed / 140f);
                hoverProgress = startVal + (targetVal - startVal) * t;
                repaint();
                if (t >= 1.0f) {
                    hoverProgress = targetVal;
                    ((Timer) e.getSource()).stop();
                }
            });
            hoverTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int grow = (int) (hoverProgress * 3);
            int x = Math.max(0, 2 - grow);
            int y = Math.max(0, 2 - grow);
            int w = getWidth() - 4 + (grow * 2);
            int h = getHeight() - 4 + (grow * 2);

            g2.setColor(new Color(25, 33, 48, 220));
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));

            if (hoverProgress > 0.01f) {
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), (int) (hoverProgress * 150)));
                g2.setStroke(new BasicStroke(1.5f));
            } else {
                g2.setColor(UITheme.BORDER_GLASS);
                g2.setStroke(new BasicStroke(1.0f));
            }
            g2.draw(new RoundRectangle2D.Float(x, y, w, h, 12, 12));
            g2.dispose();
            super.paintComponent(g);
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

        public void setOpenState(boolean open) {
            this.isOpen = open;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            g2.setColor(isHovered ? new Color(56, 189, 248, 35) : new Color(25, 33, 48));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(new Color(56, 189, 248, isHovered ? 140 : 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

            g2.setColor(UITheme.ACCENT_CYAN);
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
        private boolean isHovered = false;

        public VectorCloseButton() {
            setPreferredSize(new Dimension(24, 24));
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

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isHovered) {
                g2.setColor(new Color(56, 189, 248, 35));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                g2.setColor(UITheme.ACCENT_CYAN);
            } else {
                g2.setColor(UITheme.TEXT_MUTED);
            }

            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(6, 6, getWidth() - 6, getHeight() - 6);
            g2.drawLine(getWidth() - 6, 6, 6, getHeight() - 6);
            g2.dispose();
        }
    }

    private static class VectorCompassIcon extends JComponent {
        public VectorCompassIcon() { setPreferredSize(new Dimension(16, 16)); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(1, 1, 13, 13);
            int[] x = {8, 12, 8, 4};
            int[] y = {3, 8, 13, 8};
            g2.drawPolygon(x, y, 4);
            g2.dispose();
        }
    }

    private class CinematicIntroOverlay extends JPanel {
        private final String fullText;
        private int visibleChars = 0;
        private boolean isGliding = false;
        private float glideProgress = 0f;
        private boolean isCursorVisible = true;
        private Timer sequenceTimer;

        public CinematicIntroOverlay() {
            setOpaque(false);
            fullText = "Welcome, " + adminName;
        }

        public void startCinematicSequence() {
            Timer cursorTimer = new Timer(380, e -> { isCursorVisible = !isCursorVisible; repaint(); });
            cursorTimer.start();

            Timer typeTimer = new Timer(95, null);
            typeTimer.addActionListener(e -> {
                if (visibleChars < fullText.length()) {
                    visibleChars++;
                    repaint();
                } else {
                    typeTimer.stop();
                    Timer pauseTimer = new Timer(550, ev -> {
                        ((Timer) ev.getSource()).stop();
                        startSmoothGlide(cursorTimer);
                    });
                    pauseTimer.setRepeats(false);
                    pauseTimer.start();
                }
            });
            typeTimer.start();
        }

        private void startSmoothGlide(Timer cursorTimer) {
            isGliding = true;
            long glideStartTime = System.currentTimeMillis();
            final int glideDuration = 1800;

            sequenceTimer = new Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - glideStartTime;
                float t = Math.min(1.0f, (float) elapsed / glideDuration);
                glideProgress = t < 0.5f
                        ? 4f * t * t * t
                        : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
                repaint();

                if (t >= 1.0f) {
                    sequenceTimer.stop();
                    cursorTimer.stop();
                    setVisible(false);
                    if (welcomeLbl != null) welcomeLbl.setVisible(true);
                    appPanel.repaint();
                    loadRealDashboardData();
                }
            });
            sequenceTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int bgAlpha = isGliding ? Math.max(0, Math.min(255, (int) ((1.0f - glideProgress) * 255))) : 255;
            g2.setColor(new Color(11, 15, 23, bgAlpha));
            g2.fillRect(0, 0, width, height);

            Point targetPoint = new Point(24, 85);
            if (welcomeLbl != null && welcomeLbl.isShowing()) {
                Point p = SwingUtilities.convertPoint(welcomeLbl.getParent(), welcomeLbl.getLocation(), this);
                FontMetrics targetFm = welcomeLbl.getFontMetrics(welcomeLbl.getFont());
                targetPoint = new Point(p.x, p.y + targetFm.getAscent());
            }

            float fontSize = 30f - (11f * glideProgress);
            Font currentFont = new Font("Segoe UI", Font.BOLD, Math.round(fontSize));
            g2.setFont(currentFont);
            FontMetrics currentFm = g2.getFontMetrics();

            int startX = (width - currentFm.stringWidth(fullText)) / 2;
            int startY = (height / 2) + (currentFm.getAscent() / 2);

            int curX = (int) (startX + (targetPoint.x - startX) * glideProgress);
            int curY = (int) (startY + (targetPoint.y - startY) * glideProgress);

            String textToDraw = fullText.substring(0, visibleChars);
            g2.setColor(Color.WHITE);
            g2.drawString(textToDraw, curX, curY);

            if (!isGliding && isCursorVisible) {
                int cursorX = curX + currentFm.stringWidth(textToDraw) + 3;
                g2.fillRect(cursorX, curY - currentFm.getAscent() + 2, 2, currentFm.getHeight() - 4);
            }
            g2.dispose();
        }
    }
}