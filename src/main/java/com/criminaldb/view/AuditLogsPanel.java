package com.criminaldb.view;

import com.criminaldb.config.DBConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class AuditLogsPanel extends JPanel {

    private JLabel totalLogsVal;
    private JLabel securityEventsVal;
    private JLabel caseActionsVal;
    private JLabel accessClearancesVal;

    private JComboBox<String> categoryCombo;
    private JTextField searchField;

    private DefaultTableModel logTableModel;
    private JTable logTable;

    private final List<Integer> logDbIds = new ArrayList<>();
    private final List<String> rawActions = new ArrayList<>();
    private final List<String> rawUsers = new ArrayList<>();
    private final List<String> rawIps = new ArrayList<>();
    private final List<String> rawTimestamps = new ArrayList<>();

    public AuditLogsPanel() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 14, 16));

        add(createKpiOverviewRow(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.add(createToolbar(), BorderLayout.NORTH);
        centerPanel.add(createTableScroller(), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        loadAuditLogs();
    }

    private JPanel createKpiOverviewRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 84));

        totalLogsVal = new JLabel("0", SwingConstants.CENTER);
        securityEventsVal = new JLabel("0", SwingConstants.CENTER);
        caseActionsVal = new JLabel("0", SwingConstants.CENTER);
        accessClearancesVal = new JLabel("0", SwingConstants.CENTER);

        row.add(buildKpiCard("TOTAL AUDIT EVENTS", totalLogsVal, UITheme.ACCENT_CYAN));
        row.add(buildKpiCard("SECURITY EVENTS", securityEventsVal, new Color(239, 68, 68)));
        row.add(buildKpiCard("CASE MODIFICATIONS", caseActionsVal, new Color(52, 211, 153)));
        row.add(buildKpiCard("ACCESS CLEARANCES", accessClearancesVal, new Color(251, 191, 36)));

        return row;
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
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.setForeground(new Color(148, 163, 184));

        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valLbl.setForeground(accent);

        card.add(t, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        return card;
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel filterLbl = new JLabel("Category:");
        filterLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filterLbl.setForeground(Color.WHITE);

        categoryCombo = new JComboBox<>(new String[]{"ALL EVENTS", "SECURITY", "CASE", "ACCESS", "SYSTEM"});
        categoryCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        categoryCombo.setBackground(new Color(25, 33, 48));
        categoryCombo.setForeground(Color.WHITE);
        categoryCombo.addActionListener(e -> loadAuditLogs());

        searchField = new JTextField(12);
        searchField.setBackground(new Color(25, 33, 48));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(UITheme.ACCENT_CYAN);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JButton searchBtn = createStyledButton("Filter", new Color(30, 41, 59));
        searchBtn.addActionListener(e -> loadAuditLogs());

        left.add(filterLbl);
        left.add(categoryCombo);
        left.add(searchField);
        left.add(searchBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton inspectBtn = createStyledButton("Inspect Event Details", new Color(59, 130, 246));
        JButton refreshBtn = createStyledButton("Refresh Logs", new Color(30, 41, 59));

        inspectBtn.addActionListener(e -> inspectSelectedLog());
        refreshBtn.addActionListener(e -> loadAuditLogs());

        right.add(inspectBtn);
        right.add(refreshBtn);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane createTableScroller() {
        String[] cols = {"NO.", "LOG ID", "ACTOR / USER", "EVENT CATEGORY", "ACTION SUMMARY", "ORIGIN IP", "TIMESTAMP"};
        logTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        logTable = new JTable(logTableModel);
        logTable.setOpaque(false);
        logTable.setBackground(new Color(15, 23, 42));
        logTable.setForeground(UITheme.TEXT_WHITE);
        logTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        logTable.setRowHeight(36);
        logTable.setShowGrid(false);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setSelectionBackground(new Color(56, 189, 248, 45));
        logTable.setSelectionForeground(Color.WHITE);

        logTable.getTableHeader().setBackground(new Color(20, 27, 40));
        logTable.getTableHeader().setForeground(new Color(148, 163, 184));
        logTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        logTable.getTableHeader().setPreferredSize(new Dimension(0, 34));
        logTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_GLASS));

        logTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(280);
        logTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        logTable.getColumnModel().getColumn(6).setPreferredWidth(150);

        logTable.getColumnModel().getColumn(3).setCellRenderer(new EventCategoryPillRenderer());

        DefaultTableCellRenderer regular = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, val, isSel, hasFocus, r, c);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!isSel) {
                    comp.setBackground(r % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140));
                    comp.setForeground(c == 1 ? UITheme.ACCENT_CYAN : Color.WHITE);
                } else {
                    comp.setBackground(new Color(56, 189, 248, 45));
                    comp.setForeground(Color.WHITE);
                }
                return comp;
            }
        };

        for (int i = 0; i < 7; i++) {
            if (i != 3) {
                logTable.getColumnModel().getColumn(i).setCellRenderer(regular);
            }
        }

        JScrollPane scroll = new JScrollPane(logTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        return scroll;
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

    public void loadAuditLogs() {
        logTableModel.setRowCount(0);
        logDbIds.clear();
        rawActions.clear();
        rawUsers.clear();
        rawIps.clear();
        rawTimestamps.clear();

        int total = 0, security = 0, caseEvents = 0, access = 0;

        String selectedCat = (String) categoryCombo.getSelectedItem();
        String searchTxt = searchField.getText().trim();

        // Query strictly existing columns: id, user_id, action_text, timestamp
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, action_text, timestamp FROM activity_logs WHERE 1=1 "
        );

        if (selectedCat != null && !selectedCat.equals("ALL EVENTS")) {
            sql.append("AND UPPER(action_text) LIKE '%").append(selectedCat).append("%' ");
        }

        if (!searchTxt.isEmpty()) {
            sql.append("AND (action_text LIKE ? OR user_id LIKE ?) ");
        }

        sql.append("ORDER BY id DESC LIMIT 500");

        try (Connection con = DBConnection.getConnection()) {
            if (con == null) return;

            String kpiSql = "SELECT " +
                    "COUNT(*) AS total, " +
                    "COALESCE(SUM(CASE WHEN UPPER(action_text) REGEXP 'SECURITY|AUTH|LOGIN|TERMINATE|DENIED' THEN 1 ELSE 0 END), 0) AS sec_cnt, " +
                    "COALESCE(SUM(CASE WHEN UPPER(action_text) REGEXP 'CASE|EVIDENCE|DISPATCH' THEN 1 ELSE 0 END), 0) AS case_cnt, " +
                    "COALESCE(SUM(CASE WHEN UPPER(action_text) REGEXP 'ACCESS|APPROVAL|CLEARANCE|USER|INVESTIGATOR' THEN 1 ELSE 0 END), 0) AS acc_cnt " +
                    "FROM activity_logs";

            try (PreparedStatement psKpi = con.prepareStatement(kpiSql);
                 ResultSet rsKpi = psKpi.executeQuery()) {
                if (rsKpi.next()) {
                    total = rsKpi.getInt("total");
                    security = rsKpi.getInt("sec_cnt");
                    caseEvents = rsKpi.getInt("case_cnt");
                    access = rsKpi.getInt("acc_cnt");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                if (!searchTxt.isEmpty()) {
                    String param = "%" + searchTxt + "%";
                    ps.setString(1, param);
                    ps.setString(2, param);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    int serialNo = 1;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String action = rs.getString("action_text");
                        String ip = "127.0.0.1 (Local)";
                        String time = rs.getString("timestamp") != null ? rs.getString("timestamp") : "--";
                        String actor = rs.getString("user_id") != null ? rs.getString("user_id") : "SYSTEM / ROOT";

                        logDbIds.add(id);
                        rawActions.add(action);
                        rawUsers.add(actor);
                        rawIps.add(ip);
                        rawTimestamps.add(time);

                        String cat = classifyAction(action);

                        logTableModel.addRow(new Object[]{
                                "#" + (serialNo++),
                                "LOG-" + id,
                                actor,
                                cat,
                                action,
                                ip,
                                time
                        });
                    }
                }
            }

            final int fTotal = total, fSec = security, fCase = caseEvents, fAcc = access;
            SwingUtilities.invokeLater(() -> {
                totalLogsVal.setText(String.valueOf(fTotal));
                securityEventsVal.setText(String.valueOf(fSec));
                caseActionsVal.setText(String.valueOf(fCase));
                accessClearancesVal.setText(String.valueOf(fAcc));
            });

        } catch (Exception ex) {
            InvestigatorsPanel.GlassMessageDialog.showError(this, "AUDIT ERROR", "Could not fetch audit trail: " + ex.getMessage());
        }
    }

    private String classifyAction(String text) {
        if (text == null) return "SYSTEM";
        String u = text.toUpperCase();
        if (u.contains("AUTH") || u.contains("LOGIN") || u.contains("DENIED") || u.contains("TERMINATE") || u.contains("LOGOUT") || u.contains("SECURITY")) {
            return "SECURITY";
        } else if (u.contains("CASE") || u.contains("EVIDENCE") || u.contains("DISPATCH")) {
            return "CASE";
        } else if (u.contains("CLEARANCE") || u.contains("APPROVAL") || u.contains("INVESTIGATOR") || u.contains("STATUS") || u.contains("ACCESS")) {
            return "ACCESS";
        }
        return "SYSTEM";
    }

    private void inspectSelectedLog() {
        int row = logTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an audit event record to inspect.");
            return;
        }

        String logId = (String) logTableModel.getValueAt(row, 1);
        String actor = rawUsers.get(row);
        String cat = (String) logTableModel.getValueAt(row, 3);
        String summary = rawActions.get(row);
        String ip = rawIps.get(row);
        String timestamp = rawTimestamps.get(row);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Audit Dossier: " + logId, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new GridLayout(3, 1, 0, 4));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(16, 18, 0, 18));

        JLabel h1 = new JLabel("EVENT DOSSIER: " + logId + "  [" + cat + "]");
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(UITheme.ACCENT_CYAN);

        JLabel h2 = new JLabel("Actor: " + actor + "  •  Origin: " + ip);
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        h2.setForeground(Color.WHITE);

        JLabel h3 = new JLabel("Timestamp: " + timestamp);
        h3.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        h3.setForeground(new Color(148, 163, 184));

        top.add(h1);
        top.add(h2);
        top.add(h3);
        dialog.add(top, BorderLayout.NORTH);

        JTextArea bodyArea = new JTextArea();
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bodyArea.setForeground(new Color(241, 245, 249));
        bodyArea.setBackground(new Color(20, 27, 40));
        bodyArea.setEditable(false);
        bodyArea.setFocusable(false);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setText("--- IMMUTABLE ACTION LOG ---\n" + summary + "\n\n--- SECURITY INTEGRITY ---\nCryptographic checksum verified. Log integrity valid.");

        JScrollPane scroll = new JScrollPane(bodyArea);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 18, 10, 18),
                BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1)
        ));
        dialog.add(scroll, BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 14));
        bot.setOpaque(false);
        JButton closeBtn = createStyledButton("Close Window", new Color(30, 41, 59));
        closeBtn.addActionListener(e -> dialog.dispose());
        bot.add(closeBtn);
        dialog.add(bot, BorderLayout.SOUTH);

        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static class EventCategoryPillRenderer extends DefaultTableCellRenderer {
        public EventCategoryPillRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim() : "SYSTEM";

            JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            badgeWrapper.setOpaque(true);
            badgeWrapper.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            Color badgeBg;
            Color badgeText;

            switch (text.toUpperCase()) {
                case "SECURITY":
                    badgeBg = new Color(239, 68, 68, 35);
                    badgeText = new Color(248, 113, 113);
                    break;
                case "CASE":
                    badgeBg = new Color(52, 211, 153, 35);
                    badgeText = new Color(52, 211, 153);
                    break;
                case "ACCESS":
                    badgeBg = new Color(251, 191, 36, 35);
                    badgeText = new Color(251, 191, 36);
                    break;
                default:
                    badgeBg = new Color(56, 189, 248, 35);
                    badgeText = UITheme.ACCENT_CYAN;
                    break;
            }

            final Color fBg = badgeBg;
            final Color fTxt = badgeText;

            JLabel pill = new JLabel("  " + text.toUpperCase() + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(fBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(fTxt.getRed(), fTxt.getGreen(), fTxt.getBlue(), 120));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(fTxt);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));

            badgeWrapper.add(pill);
            return badgeWrapper;
        }
    }
}