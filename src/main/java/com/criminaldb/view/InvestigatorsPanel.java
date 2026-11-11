package com.criminaldb.view;

import com.criminaldb.config.DBConnection;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class InvestigatorsPanel extends JPanel {

    private JPanel activePanel;
    private JPanel pendingPanel;
    private AnimatedTabContainer cardsContainer;
    private JButton activeTabBtn;
    private JButton pendingTabBtn;

    private DefaultTableModel activeModel;
    private JTable activeTable;
    private DefaultTableModel pendingModel;
    private JTable pendingTable;

    private final List<Integer> activeDbIds = new ArrayList<>();
    private final List<String> activeProofPaths = new ArrayList<>();
    private final List<Integer> pendingDbIds = new ArrayList<>();
    private final List<String> pendingProofPaths = new ArrayList<>();

    public InvestigatorsPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        // 1. Cyber Tab Switcher Header
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        tabHeader.setOpaque(false);
        tabHeader.setBorder(new EmptyBorder(8, 14, 4, 14));

        activeTabBtn = createTabHeaderButton("Active Investigators", true);
        pendingTabBtn = createTabHeaderButton("Pending Approvals", false);

        activeTabBtn.addActionListener(e -> switchTab(true));
        pendingTabBtn.addActionListener(e -> switchTab(false));

        tabHeader.add(activeTabBtn);
        tabHeader.add(pendingTabBtn);
        add(tabHeader, BorderLayout.NORTH);

        // 2. Animated Card Container
        cardsContainer = new AnimatedTabContainer();
        activePanel = createActivePanel();
        pendingPanel = createPendingPanel();

        cardsContainer.addTabComponent(activePanel, "ACTIVE");
        cardsContainer.addTabComponent(pendingPanel, "PENDING");
        add(cardsContainer, BorderLayout.CENTER);

        loadInvestigatorsData();
    }

    /**
     * Public alias called by AdminDashboardFrame to refresh all investigator tables.
     */
    public void loadInvestigatorsData() {
        loadAllInvestigators();
        loadPendingApprovals();
    }

    private void switchTab(boolean toActive) {
        setTabState(activeTabBtn, toActive);
        setTabState(pendingTabBtn, !toActive);
        cardsContainer.animateToTab(toActive ? "ACTIVE" : "PENDING");
    }

    private JButton createTabHeaderButton(String title, boolean selected) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        setTabState(btn, selected);
        return btn;
    }

    private void setTabState(JButton btn, boolean selected) {
        if (selected) {
            btn.setBackground(UITheme.ACCENT_CYAN);
            btn.setForeground(new Color(15, 23, 42));
            btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        } else {
            btn.setBackground(new Color(25, 33, 48));
            btn.setForeground(new Color(148, 163, 184));
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1, true),
                    new EmptyBorder(7, 15, 7, 15)
            ));
        }
    }

    private JPanel createActivePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(6, 14, 12, 14));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel title = new JLabel("COMMISSIONED INVESTIGATORS DIRECTORY");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(UITheme.ACCENT_CYAN);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JButton viewDocBtn = createStyledButton("View ID Document", new Color(59, 130, 246));
        JButton refreshBtn = createStyledButton("Refresh List", new Color(30, 41, 59));

        viewDocBtn.addActionListener(e -> {
            int sel = activeTable.getSelectedRow();
            if (sel != -1) {
                showIdProofDialog(activeProofPaths, (String) activeModel.getValueAt(sel, 1), sel);
            } else {
                GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an investigator from the table to view their credential.");
            }
        });
        refreshBtn.addActionListener(e -> loadAllInvestigators());

        actions.add(viewDocBtn);
        actions.add(refreshBtn);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(actions, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"NO.", "FULL NAME", "USERNAME", "ID NO.", "STATUS", "ID PROOF ACTION"};
        activeModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        activeTable = configureCasesStyleTable(activeModel);

        activeTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        activeTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        activeTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        activeTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        activeTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        activeTable.getColumnModel().getColumn(5).setPreferredWidth(130);

        activeTable.getColumnModel().getColumn(5).setCellRenderer(new ViewProofCellRenderer());

        activeTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int col = activeTable.columnAtPoint(e.getPoint());
                if (col == 5) {
                    activeTable.setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    activeTable.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        activeTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = activeTable.columnAtPoint(e.getPoint());
                int row = activeTable.rowAtPoint(e.getPoint());
                if (row >= 0 && col == 5) {
                    showIdProofDialog(activeProofPaths, (String) activeModel.getValueAt(row, 1), row);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(activeTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPendingPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(6, 14, 12, 14));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel title = new JLabel("INCOMING ACCESS REQUESTS (PENDING APPROVAL)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(new Color(251, 191, 36));

        JPanel actionsToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsToolbar.setOpaque(false);

        JButton viewDocBtn = createStyledButton("View ID Document", new Color(59, 130, 246));
        JButton rejectBtn = createStyledButton("Reject Request", new Color(239, 68, 68));
        JButton approveBtn = createStyledButton("Approve Clearance", new Color(16, 185, 129));
        JButton refreshBtn = createStyledButton("Refresh Queue", new Color(30, 41, 59));

        viewDocBtn.addActionListener(e -> {
            int sel = pendingTable.getSelectedRow();
            if (sel != -1) {
                showIdProofDialog(pendingProofPaths, (String) pendingModel.getValueAt(sel, 1), sel);
            } else {
                GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an applicant from the table to view their credential.");
            }
        });

        rejectBtn.addActionListener(e -> processApproval("REJECTED"));
        approveBtn.addActionListener(e -> processApproval("ACTIVE"));
        refreshBtn.addActionListener(e -> loadPendingApprovals());

        actionsToolbar.add(viewDocBtn);
        actionsToolbar.add(rejectBtn);
        actionsToolbar.add(approveBtn);
        actionsToolbar.add(refreshBtn);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(actionsToolbar, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"NO.", "APPLICANT NAME", "USERNAME", "ID NO.", "SUBMITTED AT", "ID PROOF ACTION"};
        pendingModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        pendingTable = configureCasesStyleTable(pendingModel);

        pendingTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        pendingTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        pendingTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        pendingTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        pendingTable.getColumnModel().getColumn(4).setPreferredWidth(160);
        pendingTable.getColumnModel().getColumn(5).setPreferredWidth(130);

        pendingTable.getColumnModel().getColumn(5).setCellRenderer(new ViewProofCellRenderer());

        pendingTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int col = pendingTable.columnAtPoint(e.getPoint());
                if (col == 5) {
                    pendingTable.setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    pendingTable.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        pendingTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = pendingTable.columnAtPoint(e.getPoint());
                int row = pendingTable.rowAtPoint(e.getPoint());
                if (row >= 0 && col == 5) {
                    showIdProofDialog(pendingProofPaths, (String) pendingModel.getValueAt(row, 1), row);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pendingTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(251, 191, 36, 60), 1));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JTable configureCasesStyleTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setOpaque(false);
        table.setBackground(new Color(15, 23, 42));
        table.setForeground(UITheme.TEXT_WHITE);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(new Color(56, 189, 248, 45));
        table.setSelectionForeground(Color.WHITE);

        table.getTableHeader().setBackground(new Color(20, 27, 40));
        table.getTableHeader().setForeground(new Color(148, 163, 184));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_GLASS));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, isSel, hasFocus, r, c);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                if (!isSel) {
                    comp.setBackground(r % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140));
                    comp.setForeground(c == 0 ? UITheme.ACCENT_CYAN : Color.WHITE);
                } else {
                    comp.setBackground(new Color(56, 189, 248, 45));
                    comp.setForeground(Color.WHITE);
                }
                return comp;
            }
        };

        for (int i = 0; i < model.getColumnCount(); i++) {
            if (i != 5) table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        return table;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        return btn;
    }

    public void loadAllInvestigators() {
        activeModel.setRowCount(0);
        activeDbIds.clear();
        activeProofPaths.clear();

        String sql = "SELECT id, full_name, username, id_number, status, id_proof_path FROM users WHERE status = 'ACTIVE' AND role != 'ADMIN' ORDER BY id DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int serialNo = 1;
            while (rs.next()) {
                activeDbIds.add(rs.getInt("id"));
                String path = rs.getString("id_proof_path");
                activeProofPaths.add(path != null ? path : "");

                activeModel.addRow(new Object[]{
                        "#" + (serialNo++),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        rs.getString("id_number") != null ? rs.getString("id_number") : "--",
                        "ACTIVE",
                        "VIEW ID"
                });
            }
        } catch (Exception ex) {
            GlassMessageDialog.showError(this, "QUERY FAILURE", "Could not fetch investigator records: " + ex.getMessage());
        }
    }

    public void loadPendingApprovals() {
        pendingModel.setRowCount(0);
        pendingDbIds.clear();
        pendingProofPaths.clear();

        String sql = "SELECT id, full_name, username, id_number, created_at, id_proof_path FROM users WHERE status = 'PENDING' ORDER BY created_at ASC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int serialNo = 1;
            while (rs.next()) {
                pendingDbIds.add(rs.getInt("id"));
                String proofPath = rs.getString("id_proof_path");
                pendingProofPaths.add(proofPath != null ? proofPath : "");

                pendingModel.addRow(new Object[]{
                        "#" + (serialNo++),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        rs.getString("id_number") != null ? rs.getString("id_number") : "--",
                        rs.getTimestamp("created_at"),
                        "VIEW ID"
                });
            }
        } catch (Exception ex) {
            GlassMessageDialog.showError(this, "QUERY FAILURE", "Could not fetch approval requests: " + ex.getMessage());
        }
    }

    private void showIdProofDialog(List<String> pathsList, String name, int row) {
        if (row < 0 || row >= pathsList.size()) return;

        String path = pathsList.get(row);

        if (path == null || path.trim().isEmpty()) {
            GlassMessageDialog.showWarning(this, "DOCUMENT MISSING", "No official ID document attached for " + name);
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            GlassMessageDialog.showError(this, "FILE NOT FOUND", "The specified document cannot be found on storage:\n" + path);
            return;
        }

        String nameLower = file.getName().toLowerCase();
        if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
            try {
                BufferedImage bimg = ImageIO.read(file);
                if (bimg != null) {
                    Image scaled = bimg.getScaledInstance(540, 340, Image.SCALE_SMOOTH);
                    JLabel imgLbl = new JLabel(new ImageIcon(scaled));

                    JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Investigator Document: " + name, true);
                    dialog.getContentPane().setBackground(new Color(15, 23, 42));
                    dialog.setLayout(new BorderLayout(10, 10));

                    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
                    top.setOpaque(false);
                    JLabel hLbl = new JLabel("Official ID Credential - " + name);
                    hLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
                    hLbl.setForeground(UITheme.ACCENT_CYAN);
                    top.add(hLbl);

                    JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 14));
                    bot.setOpaque(false);
                    JButton closeBtn = createStyledButton("Close Window", new Color(30, 41, 59));
                    closeBtn.addActionListener(e -> dialog.dispose());
                    bot.add(closeBtn);

                    dialog.add(top, BorderLayout.NORTH);
                    dialog.add(imgLbl, BorderLayout.CENTER);
                    dialog.add(bot, BorderLayout.SOUTH);

                    dialog.pack();
                    dialog.setLocationRelativeTo(this);
                    dialog.setVisible(true);
                    return;
                }
            } catch (Exception ignored) {}
        }

        try {
            Desktop.getDesktop().open(file);
        } catch (Exception ex) {
            GlassMessageDialog.showError(this, "VIEWER ERROR", "Unable to open system viewer: " + ex.getMessage());
        }
    }

    private void processApproval(String status) {
        int row = pendingTable.getSelectedRow();
        if (row == -1) {
            GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an applicant from the table to proceed.");
            return;
        }

        int userId = pendingDbIds.get(row);
        String name = (String) pendingModel.getValueAt(row, 1);

        boolean isConfirmed = GlassConfirmDialog.confirm(
                this,
                "REVIEW CONFIRMATION",
                "Authorize action: Update clearance status of investigator applicant " + name + " to [" + status + "]?"
        );

        if (!isConfirmed) return;

        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();

            GlassMessageDialog.showSuccess(this, "CLEARANCE UPDATED", "Applicant " + name + " status has been set to [" + status + "].");
            loadPendingApprovals();
            loadAllInvestigators();
        } catch (Exception ex) {
            GlassMessageDialog.showError(this, "UPDATE FAILURE", "Database operation failed: " + ex.getMessage());
        }
    }

    private static class ViewProofCellRenderer extends DefaultTableCellRenderer {
        public ViewProofCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 3));
            p.setOpaque(true);
            p.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            JLabel pill = new JLabel("  VIEW ID  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(56, 189, 248, 30));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(56, 189, 248, 140));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 11));
            pill.setForeground(new Color(56, 189, 248));
            pill.setBorder(new EmptyBorder(3, 10, 3, 10));

            p.add(pill);
            return p;
        }
    }

    private static class AnimatedTabContainer extends JPanel {
        private final CardLayout cardLayout = new CardLayout();
        private float transitionProgress = 1.0f;
        private Timer animTimer;

        public AnimatedTabContainer() {
            setLayout(cardLayout);
            setOpaque(false);
        }

        public void addTabComponent(Component comp, String name) {
            add(comp, name);
        }

        public void animateToTab(String targetCard) {
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();

            transitionProgress = 0.0f;
            cardLayout.show(this, targetCard);

            final long start = System.currentTimeMillis();
            final int duration = 220;

            animTimer = new Timer(15, e -> {
                long elapsed = System.currentTimeMillis() - start;
                float t = Math.min(1.0f, (float) elapsed / duration);
                transitionProgress = 1.0f - (float) Math.pow(1.0f - t, 3);
                repaint();

                if (t >= 1.0f) {
                    transitionProgress = 1.0f;
                    ((Timer) e.getSource()).stop();
                }
            });
            animTimer.start();
        }

        @Override
        public void paint(Graphics g) {
            if (transitionProgress >= 1.0f) {
                super.paint(g);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int slideOffset = (int) ((1.0f - transitionProgress) * 20);
            g2.translate(0, slideOffset);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.05f, transitionProgress)));

            super.paint(g2);
            g2.dispose();
        }
    }

    public static class GlassConfirmDialog extends JDialog {
        private boolean confirmed = false;

        public static boolean confirm(Component parent, String title, String message) {
            Frame frame = (Frame) SwingUtilities.getWindowAncestor(parent);
            GlassConfirmDialog dialog = new GlassConfirmDialog(frame, title, message);
            dialog.setVisible(true);
            return dialog.confirmed;
        }

        private GlassConfirmDialog(Frame parent, String title, String message) {
            super(parent, true);
            setUndecorated(true);
            setSize(480, 240);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            JPanel card = new JPanel(new BorderLayout(0, 14)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 248));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(new Color(56, 189, 248, 140));
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                    g2.dispose();
                }
            };
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(22, 24, 20, 24));

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
            titleLbl.setForeground(new Color(56, 189, 248));
            card.add(titleLbl, BorderLayout.NORTH);

            JTextArea msg = new JTextArea(message);
            msg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            msg.setForeground(new Color(241, 245, 249));
            msg.setOpaque(false);
            msg.setEditable(false);
            msg.setFocusable(false);
            if (msg.getCaret() != null) {
                msg.getCaret().setVisible(false);
            }
            msg.setHighlighter(null);
            msg.setCursor(Cursor.getDefaultCursor());
            msg.setLineWrap(true);
            msg.setWrapStyleWord(true);
            card.add(msg, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            bottom.setOpaque(false);

            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            cancelBtn.setBackground(new Color(30, 41, 59));
            cancelBtn.setForeground(Color.WHITE);
            cancelBtn.setBorder(new EmptyBorder(8, 18, 8, 18));
            cancelBtn.setFocusPainted(false);
            cancelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            cancelBtn.addActionListener(e -> {
                confirmed = false;
                dispose();
            });

            JButton okBtn = new JButton("Confirm Action");
            okBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            okBtn.setBackground(new Color(16, 185, 129));
            okBtn.setForeground(Color.WHITE);
            okBtn.setBorder(new EmptyBorder(8, 18, 8, 18));
            okBtn.setFocusPainted(false);
            okBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            okBtn.addActionListener(e -> {
                confirmed = true;
                dispose();
            });

            bottom.add(cancelBtn);
            bottom.add(okBtn);
            card.add(bottom, BorderLayout.SOUTH);

            setContentPane(card);
        }
    }

    public static class GlassMessageDialog extends JDialog {

        public static void showSuccess(Component parent, String title, String msg) {
            showDialog(parent, title, msg, new Color(16, 185, 129));
        }

        public static void showWarning(Component parent, String title, String msg) {
            showDialog(parent, title, msg, new Color(251, 191, 36));
        }

        public static void showError(Component parent, String title, String msg) {
            showDialog(parent, title, msg, new Color(239, 68, 68));
        }

        private static void showDialog(Component parent, String title, String msg, Color themeColor) {
            Frame frame = (Frame) SwingUtilities.getWindowAncestor(parent);
            GlassMessageDialog dialog = new GlassMessageDialog(frame, title, msg, themeColor);
            dialog.setVisible(true);
        }

        private GlassMessageDialog(Frame parent, String title, String message, Color themeColor) {
            super(parent, true);
            setUndecorated(true);
            setSize(460, 220);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            JPanel card = new JPanel(new BorderLayout(0, 12)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 248));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(themeColor);
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                    g2.dispose();
                }
            };
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(22, 24, 20, 24));

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
            titleLbl.setForeground(themeColor);
            card.add(titleLbl, BorderLayout.NORTH);

            JTextArea msg = new JTextArea(message);
            msg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            msg.setForeground(new Color(241, 245, 249));
            msg.setOpaque(false);
            msg.setEditable(false);
            msg.setFocusable(false);
            if (msg.getCaret() != null) {
                msg.getCaret().setVisible(false);
            }
            msg.setHighlighter(null);
            msg.setCursor(Cursor.getDefaultCursor());
            msg.setLineWrap(true);
            msg.setWrapStyleWord(true);
            card.add(msg, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottom.setOpaque(false);

            JButton ackBtn = new JButton("Acknowledge");
            ackBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            ackBtn.setBackground(themeColor);
            ackBtn.setForeground(new Color(15, 23, 42));
            ackBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
            ackBtn.setFocusPainted(false);
            ackBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            ackBtn.addActionListener(e -> dispose());

            bottom.add(ackBtn);
            card.add(bottom, BorderLayout.SOUTH);

            setContentPane(card);
        }
    }
}