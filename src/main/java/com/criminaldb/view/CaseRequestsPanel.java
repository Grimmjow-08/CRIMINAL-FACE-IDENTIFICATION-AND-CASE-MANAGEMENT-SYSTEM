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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CaseRequestsPanel extends JPanel {

    private final String adminName;
    private final Runnable onRequestsUpdatedCallback;

    private DefaultTableModel tableModel;
    private JTable requestsTable;
    private JLabel countBadge;

    private final List<Integer> caseDbIds = new ArrayList<>();
    private final List<String> caseNumbers = new ArrayList<>();
    private final List<String> requestTypes = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<String> requestingUsernames = new ArrayList<>();
    private final List<String> requestingFullNames = new ArrayList<>();
    private final List<String> locations = new ArrayList<>();

    public CaseRequestsPanel() {
        this("Head Administrator", null);
    }

    public CaseRequestsPanel(String adminName, Runnable onRequestsUpdatedCallback) {
        this.adminName = adminName != null ? adminName : "Head Administrator";
        this.onRequestsUpdatedCallback = onRequestsUpdatedCallback;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel topContainer = new JPanel(new GridLayout(2, 1, 0, 8));
        topContainer.setOpaque(false);

        // Header Title Row
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);

        JLabel title = new JLabel("INCOMING INVESTIGATOR INCIDENT & CLAIM REQUESTS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(new Color(251, 191, 36));

        countBadge = new JLabel("0 Pending") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(251, 191, 36, 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(251, 191, 36, 140));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        countBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        countBadge.setForeground(new Color(251, 191, 36));
        countBadge.setBorder(new EmptyBorder(2, 8, 2, 8));

        titleRow.add(title);
        titleRow.add(countBadge);

        // Action Buttons Row
        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsRow.setOpaque(false);

        JButton inspectBtn = createStyledButton("Inspect Case Details", new Color(59, 130, 246));
        JButton rejectBtn = createStyledButton("Reject Request", new Color(239, 68, 68));
        JButton approveBtn = createStyledButton("Authorize & Dispatch", new Color(16, 185, 129));
        JButton refreshBtn = createStyledButton("Refresh Queue", new Color(30, 41, 59));

        inspectBtn.addActionListener(e -> inspectSelectedCase());
        rejectBtn.addActionListener(e -> openRejectCaseModal());
        approveBtn.addActionListener(e -> openAuthorizeAndAssignModal());
        refreshBtn.addActionListener(e -> loadPendingCaseRequests());

        buttonsRow.add(inspectBtn);
        buttonsRow.add(rejectBtn);
        buttonsRow.add(approveBtn);
        buttonsRow.add(refreshBtn);

        topContainer.add(titleRow);
        topContainer.add(buttonsRow);
        add(topContainer, BorderLayout.NORTH);

        String[] cols = {"NO.", "CASE NUMBER", "REQUESTED BY (OFFICER)", "REQUEST TYPE", "CASE TITLE", "CATEGORY", "PRIORITY", "DATE"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        requestsTable = configureCasesStyleTable(tableModel);

        requestsTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        requestsTable.getColumnModel().getColumn(1).setPreferredWidth(125);
        requestsTable.getColumnModel().getColumn(2).setPreferredWidth(190);
        requestsTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        requestsTable.getColumnModel().getColumn(4).setPreferredWidth(210);
        requestsTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        requestsTable.getColumnModel().getColumn(6).setPreferredWidth(90);
        requestsTable.getColumnModel().getColumn(7).setPreferredWidth(100);

        requestsTable.getColumnModel().getColumn(2).setCellRenderer(new OfficerBadgeRenderer());
        requestsTable.getColumnModel().getColumn(3).setCellRenderer(new RequestTypeBadgeRenderer());
        requestsTable.getColumnModel().getColumn(6).setCellRenderer(new PriorityPillRenderer());

        requestsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) inspectSelectedCase();
            }
        });

        JScrollPane scroll = new JScrollPane(requestsTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(251, 191, 36, 60), 1));
        add(scroll, BorderLayout.CENTER);

        loadPendingCaseRequests();
    }

    private JTable configureCasesStyleTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setOpaque(false);
        table.setBackground(new Color(15, 23, 42));
        table.setForeground(UITheme.TEXT_WHITE);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
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
                    comp.setForeground(c == 1 ? UITheme.ACCENT_CYAN : Color.WHITE);
                } else {
                    comp.setBackground(new Color(56, 189, 248, 45));
                    comp.setForeground(Color.WHITE);
                }
                return comp;
            }
        };

        for (int i = 0; i < 8; i++) {
            if (i != 2 && i != 3 && i != 6) {
                table.getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
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
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        return btn;
    }

    public void loadCaseRequests() {
        loadPendingCaseRequests();
    }

    public void loadPendingCaseRequests() {
        tableModel.setRowCount(0);
        caseDbIds.clear();
        caseNumbers.clear();
        requestTypes.clear();
        descriptions.clear();
        notes.clear();
        requestingUsernames.clear();
        requestingFullNames.clear();
        locations.clear();

        Map<String, String> userFullNameMap = new HashMap<>();
        String userSql = "SELECT username, full_name FROM users";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(userSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String u = rs.getString("username");
                String fn = rs.getString("full_name");
                if (fn != null && !fn.trim().isEmpty()) {
                    userFullNameMap.put(u.toLowerCase(), fn);
                }
            }
        } catch (Exception ignored) {}

        String sql = "SELECT id, case_number, title, category, priority, status, " +
                "COALESCE(created_by, assigned_officer) AS reporter, " +
                "date_reported, location_jurisdiction, incident_description, investigation_notes " +
                "FROM cases " +
                "WHERE UPPER(TRIM(status)) IN ('PENDING', 'CLAIM_PENDING') " +
                "  AND LOWER(TRIM(COALESCE(created_by, ''))) NOT IN ('admin', 'head administrator') " +
                "ORDER BY id DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int serialNo = 1;
            while (rs.next()) {
                int id = rs.getInt("id");
                String caseNum = rs.getString("case_number");
                String status = rs.getString("status");
                String reporterUsername = rs.getString("reporter") != null ? rs.getString("reporter") : "Field Officer";
                String reqType = "CLAIM_PENDING".equalsIgnoreCase(status) ? "POOL CLAIM REQUEST" : "NEW INTAKE REPORT";
                String desc = rs.getString("incident_description");
                String note = rs.getString("investigation_notes");
                String loc = rs.getString("location_jurisdiction");

                String resolvedName = userFullNameMap.getOrDefault(reporterUsername.toLowerCase(), reporterUsername);
                String displayOfficer = resolvedName.equalsIgnoreCase(reporterUsername)
                        ? reporterUsername
                        : resolvedName + " [" + reporterUsername + "]";

                caseDbIds.add(id);
                caseNumbers.add(caseNum);
                requestTypes.add(reqType);
                requestingUsernames.add(reporterUsername);
                requestingFullNames.add(resolvedName);
                descriptions.add(desc);
                notes.add(note);
                locations.add(loc);

                tableModel.addRow(new Object[]{
                        "#" + (serialNo++),
                        caseNum,
                        displayOfficer,
                        reqType,
                        rs.getString("title"),
                        rs.getString("category"),
                        rs.getString("priority"),
                        rs.getString("date_reported")
                });
            }

            int count = caseDbIds.size();
            countBadge.setText(count + " Pending");

        } catch (Exception ex) {
            InvestigatorsPanel.GlassMessageDialog.showError(this, "QUERY FAILURE", "Could not fetch case requests: " + ex.getMessage());
        }
    }

    private void inspectSelectedCase() {
        int row = requestsTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select a case incident to inspect its dossier.");
            return;
        }

        String caseNum = caseNumbers.get(row);
        String officerName = requestingFullNames.get(row);
        String officerUsername = requestingUsernames.get(row);
        String reqType = requestTypes.get(row);
        String title = (String) tableModel.getValueAt(row, 4);
        String category = (String) tableModel.getValueAt(row, 5);
        String priority = (String) tableModel.getValueAt(row, 6);
        String loc = locations.get(row);
        String desc = descriptions.get(row);
        String note = notes.get(row);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Case Request Dossier: " + caseNum, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel(new GridLayout(4, 1, 0, 4));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(16, 20, 0, 20));

        JLabel h1 = new JLabel("INCIDENT REQUEST: " + caseNum + "  [" + reqType + "]");
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(UITheme.ACCENT_CYAN);

        JLabel h2 = new JLabel("Lead Applicant: " + officerName + " (" + officerUsername + ")  •  Jurisdiction: " + (loc != null ? loc : "Standard Sector"));
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h2.setForeground(new Color(52, 211, 153));

        JLabel h3 = new JLabel("Title: " + title + "  •  Category: " + category + "  •  Priority: " + priority);
        h3.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h3.setForeground(Color.WHITE);

        top.add(h1);
        top.add(h2);
        top.add(h3);
        dialog.add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 11));
        tabs.setBackground(new Color(25, 33, 48));
        tabs.setForeground(Color.WHITE);

        // Tab 1: Incident Description & Directives
        JTextArea bodyArea = new JTextArea();
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bodyArea.setForeground(new Color(241, 245, 249));
        bodyArea.setBackground(new Color(20, 27, 40));
        bodyArea.setEditable(false);
        bodyArea.setFocusable(false);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setText("--- INCIDENT SUMMARY / DETAILS ---\n" + (desc != null && !desc.isEmpty() ? desc : "None logged.") +
                "\n\n--- PRELIMINARY INVESTIGATION LOGS ---\n" + (note != null && !note.isEmpty() ? note : "None attached."));

        JScrollPane scrollDesc = new JScrollPane(bodyArea);
        scrollDesc.setBorder(new EmptyBorder(8, 12, 8, 12));
        tabs.addTab("Incident Overview", scrollDesc);

        // Tab 2: Victims Directory
        String[] vCols = {"#", "FULL NAME", "GENDER", "AGE", "DOB", "PHONE", "ADDRESS"};
        DefaultTableModel vModel = new DefaultTableModel(vCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable vTable = new JTable(vModel);
        vTable.setOpaque(false);
        vTable.setBackground(new Color(15, 23, 42));
        vTable.setForeground(Color.WHITE);
        vTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        vTable.setRowHeight(26);
        vTable.getTableHeader().setBackground(new Color(20, 27, 40));
        vTable.getTableHeader().setForeground(new Color(148, 163, 184));
        vTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 10));

        JScrollPane vScroll = new JScrollPane(vTable);
        vScroll.getViewport().setBackground(new Color(15, 23, 42));
        vScroll.setBorder(new EmptyBorder(8, 12, 8, 12));
        tabs.addTab("Victim Directory", vScroll);

        // Asynchronously load victim records
        new Thread(() -> {
            String victimSql = "SELECT full_name, gender, age, dob, phone, address FROM case_victims WHERE case_number = ? ORDER BY id ASC";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement psV = conn.prepareStatement(victimSql)) {
                psV.setString(1, caseNum);
                try (ResultSet rsV = psV.executeQuery()) {
                    int idx = 1;
                    while (rsV.next()) {
                        final int rowIdx = idx++;
                        final String fn = rsV.getString("full_name");
                        final String g = rsV.getString("gender");
                        final String a = rsV.getString("age");
                        final String dob = rsV.getString("dob");
                        final String ph = rsV.getString("phone");
                        final String addr = rsV.getString("address");
                        SwingUtilities.invokeLater(() -> {
                            vModel.addRow(new Object[]{
                                    rowIdx, fn, g, (a != null ? a : "--"), (dob != null ? dob : "--"), (ph != null ? ph : "--"), (addr != null ? addr : "--")
                            });
                        });
                    }
                }
            } catch (Exception ex) {
                System.err.println("Load victims in request inspection error: " + ex.getMessage());
            }
        }).start();

        dialog.add(tabs, BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 14));
        bot.setOpaque(false);
        JButton closeBtn = createStyledButton("Close Dossier", new Color(30, 41, 59));
        closeBtn.addActionListener(e -> dialog.dispose());
        bot.add(closeBtn);
        dialog.add(bot, BorderLayout.SOUTH);

        dialog.setSize(640, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openRejectCaseModal() {
        int row = requestsTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select a case request to dismiss.");
            return;
        }

        int caseId = caseDbIds.get(row);
        String caseNum = caseNumbers.get(row);
        String officerName = requestingFullNames.get(row);
        String officerUsername = requestingUsernames.get(row);
        String reqType = requestTypes.get(row);
        String title = (String) tableModel.getValueAt(row, 4);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Dismiss Request: " + caseNum, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(14, 14));

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 20, 0, 20));

        JLabel h1 = new JLabel("DISMISS & REJECT JURISDICTION REQUEST");
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(new Color(248, 113, 113));

        JLabel h2 = new JLabel(caseNum + " — " + title + " (" + reqType + ")");
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h2.setForeground(Color.WHITE);

        header.add(h1);
        header.add(h2);
        dialog.add(header, BorderLayout.NORTH);

        JPanel dossierCard = new JPanel(new GridLayout(3, 1, 0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(28, 20, 28, 240));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(239, 68, 68, 120));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        dossierCard.setOpaque(false);
        dossierCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel sub1 = new JLabel("REJECT CLAIM FOR: " + officerName.toUpperCase() + " [" + officerUsername + "]");
        sub1.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sub1.setForeground(new Color(248, 113, 113));

        JLabel sub2 = new JLabel("Status will permanently transition to REJECTED for this officer.");
        sub2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub2.setForeground(Color.WHITE);

        JLabel sub3 = new JLabel("Docket will be released back to the Available Cases pool for other investigators.");
        sub3.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        sub3.setForeground(new Color(148, 163, 184));

        dossierCard.add(sub1);
        dossierCard.add(sub2);
        dossierCard.add(sub3);

        JPanel centerWrap = new JPanel(new BorderLayout(0, 10));
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(4, 20, 4, 20));
        centerWrap.add(dossierCard, BorderLayout.NORTH);

        JPanel reasonPanel = new JPanel(new BorderLayout(0, 4));
        reasonPanel.setOpaque(false);
        JLabel reasonLbl = new JLabel("Rejection Directive Note:");
        reasonLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        reasonLbl.setForeground(new Color(148, 163, 184));

        JComboBox<String> reasonCombo = new JComboBox<>(new String[]{
                "Jurisdiction Mismatch / Reassigned to District Division",
                "Insufficient Clearance Level for Active Priority",
                "Investigator Active Docket Overload (Max 5 Cases)",
                "Administrative Executive Dismissal"
        });
        reasonCombo.setBackground(new Color(25, 33, 48));
        reasonCombo.setForeground(Color.WHITE);
        reasonCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reasonPanel.add(reasonLbl, BorderLayout.NORTH);
        reasonPanel.add(reasonCombo, BorderLayout.CENTER);

        centerWrap.add(reasonPanel, BorderLayout.CENTER);
        dialog.add(centerWrap, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        actions.setOpaque(false);

        JButton cancelBtn = createStyledButton("Cancel", new Color(30, 41, 59));
        JButton confirmRejectBtn = createStyledButton("Confirm Rejection", new Color(239, 68, 68));

        cancelBtn.addActionListener(e -> dialog.dispose());
        confirmRejectBtn.addActionListener(e -> {
            String selectedReason = (String) reasonCombo.getSelectedItem();

            // When rejected:
            // 1. Append the officer's username to rejected_officers so they cannot claim it again.
            // 2. Reset status to PENDING with assigned_officer='Unassigned' and created_by='admin'
            //    so all other active investigators can view and claim it in the pool.
            String updateSql;
            if ("POOL CLAIM REQUEST".equalsIgnoreCase(reqType)) {
                updateSql = "UPDATE cases SET status = 'PENDING', assigned_officer = 'Unassigned', created_by = 'admin', " +
                        "rejected_officers = CONCAT(COALESCE(rejected_officers,''), ',', ?), " +
                        "investigation_notes = CONCAT(COALESCE(investigation_notes,''), '\n[ADMIN REJECT]: " + selectedReason + "'), " +
                        "updated_at = NOW() WHERE id = ?";
            } else {
                updateSql = "UPDATE cases SET status = 'REJECTED', created_by = ?, " +
                        "investigation_notes = CONCAT(COALESCE(investigation_notes,''), '\n[ADMIN REJECT]: " + selectedReason + "'), " +
                        "updated_at = NOW() WHERE id = ?";
            }

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setString(1, officerUsername);
                ps.setInt(2, caseId);
                ps.executeUpdate();

                AuditLogger.log(adminName, "CASE REQUEST REJECTED: Docket [" + caseNum + "] requested by [" + officerName + "] rejected. Released to public pool.");

                dialog.dispose();
                InvestigatorsPanel.GlassMessageDialog.showSuccess(this, "REQUEST REJECTED", "Case " + caseNum + " has been rejected and released back to available pool.");
                loadPendingCaseRequests();
                if (onRequestsUpdatedCallback != null) onRequestsUpdatedCallback.run();
            } catch (Exception ex) {
                InvestigatorsPanel.GlassMessageDialog.showError(this, "REJECTION FAILED", ex.getMessage());
            }
        });

        actions.add(cancelBtn);
        actions.add(confirmRejectBtn);
        dialog.add(actions, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(530, 310);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openAuthorizeAndAssignModal() {
        int row = requestsTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select a case incident to authorize.");
            return;
        }

        int caseId = caseDbIds.get(row);
        String caseNum = caseNumbers.get(row);
        String reqType = requestTypes.get(row);
        String title = (String) tableModel.getValueAt(row, 4);
        String officerUsername = requestingUsernames.get(row);
        String officerName = requestingFullNames.get(row);

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Authorize Case: " + caseNum, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(14, 14));

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 20, 0, 20));

        JLabel h1 = new JLabel("AUTHORIZE & DISPATCH CASE DOSSIER");
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(new Color(52, 211, 153));

        JLabel h2 = new JLabel(caseNum + " — " + title);
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h2.setForeground(Color.WHITE);

        header.add(h1);
        header.add(h2);
        dialog.add(header, BorderLayout.NORTH);

        JPanel dossierCard = new JPanel(new GridLayout(3, 1, 0, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(24, 32, 47, 240));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(52, 211, 153, 110));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        dossierCard.setOpaque(false);
        dossierCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel sub1 = new JLabel("DISPATCH TARGET: " + officerName.toUpperCase());
        sub1.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sub1.setForeground(new Color(52, 211, 153));

        JLabel sub2 = new JLabel("Badge Clearance: [" + officerUsername + "] • Request: " + reqType);
        sub2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub2.setForeground(Color.WHITE);

        JLabel sub3 = new JLabel("Authorizing will clear the request queue and assign this docket directly into the officer's My Cases.");
        sub3.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        sub3.setForeground(new Color(148, 163, 184));

        dossierCard.add(sub1);
        dossierCard.add(sub2);
        dossierCard.add(sub3);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(4, 20, 4, 20));
        centerWrap.add(dossierCard, BorderLayout.CENTER);
        dialog.add(centerWrap, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        actions.setOpaque(false);

        JButton cancelBtn = createStyledButton("Cancel", new Color(30, 41, 59));
        JButton authorizeBtn = createStyledButton("Authorize & Dispatch to " + officerName, new Color(16, 185, 129));

        cancelBtn.addActionListener(e -> dialog.dispose());
        authorizeBtn.addActionListener(e -> {
            String updateSql = "UPDATE cases SET status = 'ACTIVE', assigned_officer = ?, created_by = 'admin', updated_at = NOW() WHERE id = ?";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setString(1, officerUsername);
                ps.setInt(2, caseId);
                ps.executeUpdate();

                AuditLogger.log(adminName, "CASE AUTHORIZED: Docket [" + caseNum + "] dispatched to requesting detective [" + officerName + "] (" + officerUsername + ") as ACTIVE.");

                dialog.dispose();
                InvestigatorsPanel.GlassMessageDialog.showSuccess(this, "CASE DISPATCHED", "Docket " + caseNum + " is now ACTIVE and assigned to " + officerName + ". It appears directly in their My Cases.");
                loadPendingCaseRequests();
                if (onRequestsUpdatedCallback != null) onRequestsUpdatedCallback.run();
            } catch (Exception ex) {
                InvestigatorsPanel.GlassMessageDialog.showError(this, "AUTHORIZATION FAILED", ex.getMessage());
            }
        });

        actions.add(cancelBtn);
        actions.add(authorizeBtn);
        dialog.add(actions, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(520, 260);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static class OfficerBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            c.setFont(new Font("Segoe UI", Font.BOLD, 12));
            if (!isSelected) {
                c.setForeground(new Color(52, 211, 153));
                c.setBackground(row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140));
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return c;
        }
    }

    private static class RequestTypeBadgeRenderer extends DefaultTableCellRenderer {
        public RequestTypeBadgeRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString() : "INTAKE";
            boolean isClaim = text.contains("CLAIM");
            Color bg = isClaim ? new Color(56, 189, 248, 35) : new Color(251, 191, 36, 35);
            Color fg = isClaim ? UITheme.ACCENT_CYAN : new Color(251, 191, 36);

            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            wrap.setOpaque(true);
            wrap.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            JLabel pill = new JLabel("  " + text.toUpperCase() + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 120));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(fg);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));
            wrap.add(pill);
            return wrap;
        }
    }

    private static class PriorityPillRenderer extends DefaultTableCellRenderer {
        public PriorityPillRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim() : "HIGH";

            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            wrap.setOpaque(true);
            wrap.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            Color badgeBg;
            Color badgeText;

            if ("CRITICAL".equalsIgnoreCase(text)) {
                badgeBg = new Color(225, 29, 72, 45);
                badgeText = new Color(251, 113, 133);
            } else if ("HIGH".equalsIgnoreCase(text)) {
                badgeBg = new Color(239, 68, 68, 35);
                badgeText = new Color(248, 113, 113);
            } else if ("MEDIUM".equalsIgnoreCase(text)) {
                badgeBg = new Color(251, 191, 36, 35);
                badgeText = new Color(251, 191, 36);
            } else {
                badgeBg = new Color(56, 189, 248, 35);
                badgeText = UITheme.ACCENT_CYAN;
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
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(fTxt);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));

            wrap.add(pill);
            return wrap;
        }
    }
}