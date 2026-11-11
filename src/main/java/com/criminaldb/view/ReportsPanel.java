package com.criminaldb.view;

import com.criminaldb.config.DBConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;

public class ReportsPanel extends JPanel {

    private JLabel totalCasesVal;
    private JLabel approvedCasesVal;
    private JLabel rejectedCasesVal;
    private JLabel pendingCasesVal;
    private JLabel totalEvidenceVal;
    private JLabel totalInvestigatorsVal;

    private DonutChartPanel approvalChart;
    private CrimeCategoryBarPanel crimeCategoryChart;
    private SpeedMetricsPanel speedPanel;
    private EvidenceMiniStatsPanel evidenceStatsPanel;
    private InvestigatorActivityPanel investigatorRankPanel;
    private MonthlyActivityBarPanel monthlyActivityChart;

    public ReportsPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(10, 16, 8, 16));

        JLabel title = new JLabel("DEPARTMENTAL INTELLIGENCE & ANALYTICS REPORTS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(UITheme.ACCENT_CYAN);

        JButton refreshBtn = new JButton("Refresh Analytics");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshBtn.setBackground(new Color(30, 41, 59));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        refreshBtn.setBorder(new EmptyBorder(6, 14, 6, 14));
        refreshBtn.addActionListener(e -> loadAllReportData());

        topBar.add(title, BorderLayout.WEST);
        topBar.add(refreshBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setOpaque(false);
        scrollContent.setBorder(new EmptyBorder(0, 16, 24, 16));

        JPanel kpiRow = createKpiCardsRow();
        scrollContent.add(kpiRow);
        scrollContent.add(Box.createVerticalStrut(12));

        JPanel row1 = new JPanel(new GridLayout(1, 2, 12, 0));
        row1.setOpaque(false);
        row1.setPreferredSize(new Dimension(800, 230));
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        approvalChart = new DonutChartPanel();
        crimeCategoryChart = new CrimeCategoryBarPanel();
        row1.add(approvalChart);
        row1.add(crimeCategoryChart);
        scrollContent.add(row1);
        scrollContent.add(Box.createVerticalStrut(12));

        monthlyActivityChart = new MonthlyActivityBarPanel();
        monthlyActivityChart.setPreferredSize(new Dimension(800, 190));
        monthlyActivityChart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        scrollContent.add(monthlyActivityChart);
        scrollContent.add(Box.createVerticalStrut(12));

        JPanel row3 = new JPanel(new GridLayout(1, 3, 12, 0));
        row3.setOpaque(false);
        row3.setPreferredSize(new Dimension(800, 210));
        row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));

        speedPanel = new SpeedMetricsPanel();
        evidenceStatsPanel = new EvidenceMiniStatsPanel();
        investigatorRankPanel = new InvestigatorActivityPanel();

        row3.add(speedPanel);
        row3.add(evidenceStatsPanel);
        row3.add(investigatorRankPanel);
        scrollContent.add(row3);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        loadAllReportData();
    }

    private JPanel createKpiCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 6, 10, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(800, 78));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));

        totalCasesVal = new JLabel("0", SwingConstants.CENTER);
        approvedCasesVal = new JLabel("0", SwingConstants.CENTER);
        rejectedCasesVal = new JLabel("0", SwingConstants.CENTER);
        pendingCasesVal = new JLabel("0", SwingConstants.CENTER);
        totalEvidenceVal = new JLabel("0", SwingConstants.CENTER);
        totalInvestigatorsVal = new JLabel("0", SwingConstants.CENTER);

        row.add(buildKpiCard("TOTAL CASES", totalCasesVal, UITheme.ACCENT_CYAN));
        row.add(buildKpiCard("APPROVED", approvedCasesVal, new Color(52, 211, 153)));
        row.add(buildKpiCard("REJECTED", rejectedCasesVal, new Color(239, 68, 68)));
        row.add(buildKpiCard("PENDING", pendingCasesVal, new Color(251, 191, 36)));
        row.add(buildKpiCard("EVIDENCE", totalEvidenceVal, new Color(168, 85, 247)));
        row.add(buildKpiCard("INVESTIGATORS", totalInvestigatorsVal, new Color(56, 189, 248)));

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
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(8, 6, 8, 6));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.setForeground(new Color(148, 163, 184));

        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valLbl.setForeground(accent);

        card.add(t, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        return card;
    }

    public void loadAllReportData() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            int totalCases = 0, approvedCases = 0, rejectedCases = 0, pendingCases = 0;
            int totalEvidence = 0, totalInvestigators = 0;

            final Map<String, Integer> crimeCounts = new LinkedHashMap<>();
            final Map<String, Integer> evidenceStatusCounts = new HashMap<>();
            final Map<String, Integer> speedCounts = new LinkedHashMap<>();
            final Map<String, Integer> investigatorLoads = new LinkedHashMap<>();
            final List<MonthlyStat> monthlyStats = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                try (Connection con = DBConnection.getConnection()) {
                    if (con == null) return null;

                    String caseKpiSql = "SELECT " +
                            "COUNT(*) AS total, " +
                            "COALESCE(SUM(CASE WHEN UPPER(status) IN ('ACTIVE', 'APPROVED', 'CLOSED') THEN 1 ELSE 0 END), 0) AS approved, " +
                            "COALESCE(SUM(CASE WHEN UPPER(status) = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected, " +
                            "COALESCE(SUM(CASE WHEN UPPER(status) LIKE '%PENDING%' THEN 1 ELSE 0 END), 0) AS pending " +
                            "FROM cases";
                    try (PreparedStatement ps = con.prepareStatement(caseKpiSql);
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            totalCases = rs.getInt("total");
                            approvedCases = rs.getInt("approved");
                            rejectedCases = rs.getInt("rejected");
                            pendingCases = rs.getInt("pending");
                        }
                    }

                    try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM case_evidence");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) totalEvidence = rs.getInt(1);
                    }

                    try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM users WHERE role != 'ADMIN' AND status = 'ACTIVE'");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) totalInvestigators = rs.getInt(1);
                    }

                    String crimeSql = "SELECT COALESCE(category, 'Unspecified') AS cat, COUNT(*) AS cnt " +
                            "FROM cases GROUP BY cat ORDER BY cnt DESC LIMIT 5";
                    try (PreparedStatement ps = con.prepareStatement(crimeSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            crimeCounts.put(rs.getString("cat"), rs.getInt("cnt"));
                        }
                    }

                    String evdSql = "SELECT status, COUNT(*) AS cnt FROM case_evidence GROUP BY status";
                    try (PreparedStatement ps = con.prepareStatement(evdSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            evidenceStatusCounts.put(rs.getString("status").toUpperCase(), rs.getInt("cnt"));
                        }
                    }

                    String speedSql = "SELECT " +
                            "COALESCE(SUM(CASE WHEN DATEDIFF(updated_at, date_reported) <= 7 THEN 1 ELSE 0 END), 0) AS fast, " +
                            "COALESCE(SUM(CASE WHEN DATEDIFF(updated_at, date_reported) BETWEEN 8 AND 15 THEN 1 ELSE 0 END), 0) AS mid, " +
                            "COALESCE(SUM(CASE WHEN DATEDIFF(updated_at, date_reported) BETWEEN 16 AND 30 THEN 1 ELSE 0 END), 0) AS standard, " +
                            "COALESCE(SUM(CASE WHEN DATEDIFF(updated_at, date_reported) > 30 THEN 1 ELSE 0 END), 0) AS extended " +
                            "FROM cases";
                    try (PreparedStatement ps = con.prepareStatement(speedSql);
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            speedCounts.put("< 7 Days", rs.getInt("fast"));
                            speedCounts.put("7-15 Days", rs.getInt("mid"));
                            speedCounts.put("15-30 Days", rs.getInt("standard"));
                            speedCounts.put("> 30 Days", rs.getInt("extended"));
                        }
                    }

                    String invLoadSql = "SELECT COALESCE(c.assigned_officer, c.created_by) AS officer, COUNT(*) AS c_count " +
                            "FROM cases c " +
                            "LEFT JOIN users u ON u.username = COALESCE(c.assigned_officer, c.created_by) " +
                            "WHERE (u.status = 'ACTIVE' OR u.role = 'ADMIN' OR u.id IS NULL) " +
                            "  AND (c.assigned_officer IS NOT NULL OR c.created_by IS NOT NULL) " +
                            "GROUP BY officer ORDER BY c_count DESC LIMIT 4";
                    try (PreparedStatement ps = con.prepareStatement(invLoadSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            investigatorLoads.put(rs.getString("officer"), rs.getInt("c_count"));
                        }
                    }

                    String monthlySql = "SELECT DATE_FORMAT(date_reported, '%b %Y') AS m_label, " +
                            "COUNT(*) AS total_m, " +
                            "COALESCE(SUM(CASE WHEN UPPER(status) IN ('ACTIVE', 'CLOSED') THEN 1 ELSE 0 END), 0) AS app_m " +
                            "FROM cases GROUP BY m_label ORDER BY MIN(date_reported) DESC LIMIT 6";
                    try (PreparedStatement ps = con.prepareStatement(monthlySql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            monthlyStats.add(new MonthlyStat(rs.getString("m_label"), rs.getInt("total_m"), rs.getInt("app_m")));
                        }
                    }

                } catch (Exception ex) {
                    System.err.println("Reports load failure: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                totalCasesVal.setText(String.valueOf(totalCases));
                approvedCasesVal.setText(String.valueOf(approvedCases));
                rejectedCasesVal.setText(String.valueOf(rejectedCases));
                pendingCasesVal.setText(String.valueOf(pendingCases));
                totalEvidenceVal.setText(String.valueOf(totalEvidence));
                totalInvestigatorsVal.setText(String.valueOf(totalInvestigators));

                approvalChart.setData(approvedCases, pendingCases, rejectedCases);
                crimeCategoryChart.setData(crimeCounts, totalCases);
                speedPanel.setData(speedCounts);
                evidenceStatsPanel.setData(evidenceStatusCounts);
                investigatorRankPanel.setData(investigatorLoads);
                monthlyActivityChart.setData(monthlyStats);
            }
        };
        worker.execute();
    }

    public static class MonthlyStat {
        public final String month;
        public final int total;
        public final int approved;

        public MonthlyStat(String month, int total, int approved) {
            this.month = month;
            this.total = total;
            this.approved = approved;
        }
    }

    private static class DonutChartPanel extends JPanel {
        private int approved = 0, pending = 0, rejected = 0;

        public DonutChartPanel() {
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
        }

        public void setData(int app, int pend, int rej) {
            this.approved = app;
            this.pending = pend;
            this.rejected = rej;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 32, 47, 220));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawString("CASE APPROVAL OVERVIEW", 14, 22);

            int total = approved + pending + rejected;
            int cx = getWidth() / 3;
            int cy = getHeight() / 2 + 10;
            int r = Math.min(cx, cy) - 24;
            int hole = (int) (r * 0.6);

            if (total == 0) {
                g2.setColor(new Color(148, 163, 184, 80));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                g2.drawString("No Case Data", cx - 35, cy + 4);
                g2.dispose();
                return;
            }

            double appAngle = ((double) approved / total) * 360;
            double pendAngle = ((double) pending / total) * 360;
            double rejAngle = 360 - appAngle - pendAngle;

            double start = 90;
            g2.setColor(new Color(52, 211, 153));
            g2.fill(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, appAngle, Arc2D.PIE));
            start += appAngle;

            g2.setColor(new Color(251, 191, 36));
            g2.fill(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, pendAngle, Arc2D.PIE));
            start += pendAngle;

            g2.setColor(new Color(239, 68, 68));
            g2.fill(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, rejAngle, Arc2D.PIE));

            g2.setColor(new Color(24, 32, 47));
            g2.fillOval(cx - hole, cy - hole, hole * 2, hole * 2);

            int lx = cx + r + 24;
            int ly = cy - 30;
            drawLegendRow(g2, "Approved: " + approved, new Color(52, 211, 153), lx, ly);
            drawLegendRow(g2, "Pending: " + pending, new Color(251, 191, 36), lx, ly + 28);
            drawLegendRow(g2, "Rejected: " + rejected, new Color(239, 68, 68), lx, ly + 56);

            g2.dispose();
        }

        private void drawLegendRow(Graphics2D g2, String text, Color c, int x, int y) {
            g2.setColor(c);
            g2.fillRoundRect(x, y - 9, 10, 10, 4, 4);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.drawString(text, x + 16, y);
        }
    }

    private static class CrimeCategoryBarPanel extends JPanel {
        private final Map<String, Integer> crimeData = new LinkedHashMap<>();
        private int total = 0;

        public CrimeCategoryBarPanel() {
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
        }

        public void setData(Map<String, Integer> map, int total) {
            this.crimeData.clear();
            this.crimeData.putAll(map);
            this.total = total;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 32, 47, 220));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawString("CASES BY CRIME TYPE", 14, 22);

            int y = 52;
            int maxBarW = getWidth() - 170;

            if (crimeData.isEmpty()) {
                g2.setColor(new Color(148, 163, 184));
                g2.drawString("No incident classifications logged.", 18, 90);
                g2.dispose();
                return;
            }

            for (Map.Entry<String, Integer> e : crimeData.entrySet()) {
                String cat = e.getKey();
                int cnt = e.getValue();
                double pct = total > 0 ? (double) cnt / total : 0;
                int barW = Math.max(8, (int) (pct * maxBarW));

                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.setColor(Color.WHITE);
                g2.drawString(cat, 16, y);

                g2.setColor(new Color(56, 189, 248, 40));
                g2.fillRoundRect(100, y - 10, maxBarW, 10, 6, 6);

                g2.setColor(UITheme.ACCENT_CYAN);
                g2.fillRoundRect(100, y - 10, barW, 10, 6, 6);

                g2.setColor(new Color(148, 163, 184));
                g2.drawString(String.valueOf(cnt), 108 + maxBarW, y);

                y += 32;
            }
            g2.dispose();
        }
    }

    private static class MonthlyActivityBarPanel extends JPanel {
        private final List<MonthlyStat> stats = new ArrayList<>();

        public MonthlyActivityBarPanel() {
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
        }

        public void setData(List<MonthlyStat> list) {
            stats.clear();
            stats.addAll(list);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 32, 47, 220));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawString("CASE COMPLETION & ACTIVITY TREND (MONTHLY VOLUME)", 14, 22);

            g2.setColor(new Color(56, 189, 248));
            g2.fillRect(getWidth() - 180, 14, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.drawString("Created", getWidth() - 168, 22);

            g2.setColor(new Color(52, 211, 153));
            g2.fillRect(getWidth() - 100, 14, 8, 8);
            g2.drawString("Approved", getWidth() - 88, 22);

            if (stats.isEmpty()) {
                g2.setColor(new Color(148, 163, 184));
                g2.drawString("Awaiting historical monthly incident submissions.", 24, 90);
                g2.dispose();
                return;
            }

            int baselineY = getHeight() - 32;
            int colWidth = (getWidth() - 80) / Math.max(1, stats.size());
            int maxVal = 1;
            for (MonthlyStat s : stats) maxVal = Math.max(maxVal, s.total);

            int x = 45;
            for (MonthlyStat s : stats) {
                int totalH = (int) (((double) s.total / maxVal) * 90);
                int appH = (int) (((double) s.approved / maxVal) * 90);

                g2.setColor(new Color(56, 189, 248, 180));
                g2.fillRoundRect(x, baselineY - totalH, 18, totalH, 6, 6);

                g2.setColor(new Color(52, 211, 153, 200));
                g2.fillRoundRect(x + 22, baselineY - appH, 18, appH, 6, 6);

                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString(s.month, x, baselineY + 16);

                x += colWidth;
            }
            g2.dispose();
        }
    }

    private static class SpeedMetricsPanel extends JPanel {
        private final Map<String, Integer> speeds = new LinkedHashMap<>();

        public SpeedMetricsPanel() {
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
        }

        public void setData(Map<String, Integer> data) {
            speeds.clear();
            speeds.putAll(data);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 32, 47, 220));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawString("COMPLETION SPEED", 14, 22);

            int y = 52;
            for (Map.Entry<String, Integer> s : speeds.entrySet()) {
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                g2.drawString(s.getKey(), 16, y);

                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.setColor(new Color(52, 211, 153));
                g2.drawString(s.getValue() + " cases", getWidth() - 75, y);
                y += 34;
            }
            g2.dispose();
        }
    }

    private static class EvidenceMiniStatsPanel extends JPanel {
        private final Map<String, Integer> evidenceStats = new HashMap<>();

        public EvidenceMiniStatsPanel() {
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
        }

        public void setData(Map<String, Integer> data) {
            evidenceStats.clear();
            evidenceStats.putAll(data);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 32, 47, 220));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawString("EVIDENCE STATISTICS", 14, 22);

            int ver = evidenceStats.getOrDefault("VERIFIED", 0);
            int pend = evidenceStats.getOrDefault("PENDING REVIEW", 0);
            int flag = evidenceStats.getOrDefault("FLAGGED", 0);

            drawStatPill(g2, "VERIFIED", ver, new Color(52, 211, 153), 50);
            drawStatPill(g2, "PENDING REVIEW", pend, new Color(251, 191, 36), 92);
            drawStatPill(g2, "FLAGGED", flag, new Color(239, 68, 68), 134);

            g2.dispose();
        }

        private void drawStatPill(Graphics2D g2, String label, int count, Color col, int y) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(Color.WHITE);
            g2.drawString(label, 16, y);

            g2.setColor(col);
            g2.fillRoundRect(getWidth() - 65, y - 12, 45, 18, 8, 8);
            g2.setColor(new Color(15, 23, 42));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.drawString(String.valueOf(count), getWidth() - 48, y + 1);
        }
    }

    private static class InvestigatorActivityPanel extends JPanel {
        private final Map<String, Integer> ranks = new LinkedHashMap<>();

        public InvestigatorActivityPanel() {
            setOpaque(false);
            setBorder(new EmptyBorder(12, 14, 12, 14));
        }

        public void setData(Map<String, Integer> data) {
            ranks.clear();
            ranks.putAll(data);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(24, 32, 47, 220));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(UITheme.BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawString("INVESTIGATOR ACTIVITY", 14, 22);

            if (ranks.isEmpty()) {
                g2.setColor(new Color(148, 163, 184));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.drawString("No active investigator workloads.", 16, 70);
                g2.dispose();
                return;
            }

            int y = 52;
            for (Map.Entry<String, Integer> e : ranks.entrySet()) {
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                g2.setColor(Color.WHITE);
                g2.drawString(e.getKey(), 16, y);

                g2.setColor(UITheme.ACCENT_CYAN);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.drawString(e.getValue() + " cases", getWidth() - 75, y);
                y += 32;
            }
            g2.dispose();
        }
    }
}