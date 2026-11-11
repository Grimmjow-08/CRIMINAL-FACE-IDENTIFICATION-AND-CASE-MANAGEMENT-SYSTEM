package com.criminaldb.view;

import com.criminaldb.config.DBConnection;
import com.criminaldb.util.AuditLogger;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class EvidencePanel extends JPanel {

    private JLabel totalEvidenceVal;
    private JLabel pendingReviewVal;
    private JLabel verifiedVal;
    private JLabel flaggedVal;

    private JComboBox<String> statusFilterCombo;
    private JTextField searchField;

    private JButton closeCaseBtn;
    private JButton flagBtn;

    private DefaultTableModel batchTableModel;
    private JTable batchTable;

    // Track Case Batch Metadata
    private final List<String> batchCaseNumbers = new ArrayList<>();
    private final List<String> batchCaseTitles = new ArrayList<>();
    private final List<String> batchCollectors = new ArrayList<>();
    private final List<String> batchCollectorFullNames = new ArrayList<>();
    private final List<String> batchCollectorBadgeIds = new ArrayList<>();
    private final List<String> batchUploadTimes = new ArrayList<>();
    private final List<String> batchStatuses = new ArrayList<>();
    private final List<Integer> batchItemCounts = new ArrayList<>();
    private final List<Integer> batchRevisions = new ArrayList<>();
    private final List<Boolean> batchIsResubmitted = new ArrayList<>();

    public EvidencePanel() {
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 14, 16));

        add(createOverviewKpiRow(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.add(createFilterAndActionToolbar(), BorderLayout.NORTH);
        centerPanel.add(createTableScrollPane(), BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        loadEvidenceData();
    }

    private JPanel createOverviewKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 88));

        totalEvidenceVal = new JLabel("0", SwingConstants.CENTER);
        pendingReviewVal = new JLabel("0", SwingConstants.CENTER);
        verifiedVal = new JLabel("0", SwingConstants.CENTER);
        flaggedVal = new JLabel("0", SwingConstants.CENTER);

        row.add(buildKpiCard("TOTAL CASE DOSSIERS", totalEvidenceVal, UITheme.ACCENT_CYAN));
        row.add(buildKpiCard("PENDING REVIEW", pendingReviewVal, new Color(251, 191, 36)));
        row.add(buildKpiCard("VERIFIED / CLOSED", verifiedVal, new Color(52, 211, 153)));
        row.add(buildKpiCard("FLAGGED DOSSIERS", flaggedVal, new Color(239, 68, 68)));

        return row;
    }

    private JPanel buildKpiCard(String title, JLabel valueLabel, Color accent) {
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

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(new Color(148, 163, 184));

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLabel.setForeground(accent);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createFilterAndActionToolbar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);

        JPanel leftFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftFilters.setOpaque(false);

        JLabel statusLbl = new JLabel("Review Status:");
        statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLbl.setForeground(Color.WHITE);

        statusFilterCombo = new JComboBox<>(new String[]{"ALL STATUS", "PENDING REVIEW", "VERIFIED", "FLAGGED"});
        statusFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusFilterCombo.setBackground(new Color(25, 33, 48));
        statusFilterCombo.setForeground(Color.WHITE);
        statusFilterCombo.addActionListener(e -> loadEvidenceData());

        searchField = new JTextField(12);
        searchField.setBackground(new Color(25, 33, 48));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(UITheme.ACCENT_CYAN);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JButton searchBtn = createStyledButton("Filter Docket", new Color(30, 41, 59));
        searchBtn.addActionListener(e -> loadEvidenceData());

        leftFilters.add(statusLbl);
        leftFilters.add(statusFilterCombo);
        leftFilters.add(searchField);
        leftFilters.add(searchBtn);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        JButton inspectBtn = createStyledButton("Open Evidence Deck", new Color(59, 130, 246));
        closeCaseBtn = createStyledButton("Close Case", new Color(16, 185, 129));
        flagBtn = createStyledButton("Flag Discrepancy", new Color(239, 68, 68));
        JButton refreshBtn = createStyledButton("Refresh", new Color(30, 41, 59));

        inspectBtn.addActionListener(e -> openBatchGalleryModal());
        closeCaseBtn.addActionListener(e -> handleCloseCase());
        flagBtn.addActionListener(e -> openFlagDiscrepancyModal());
        refreshBtn.addActionListener(e -> loadEvidenceData());

        rightActions.add(inspectBtn);
        rightActions.add(closeCaseBtn);
        rightActions.add(flagBtn);
        rightActions.add(refreshBtn);

        bar.add(leftFilters, BorderLayout.WEST);
        bar.add(rightActions, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane createTableScrollPane() {
        String[] cols = {"NO.", "CASE DOCKET", "INCIDENT TITLE", "ATTACHED ASSETS", "SUBMITTING INVESTIGATOR", "LAST LOGGED AT", "REVIEW STATUS"};
        batchTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        batchTable = new JTable(batchTableModel);
        batchTable.setOpaque(false);
        batchTable.setBackground(new Color(15, 23, 42));
        batchTable.setForeground(UITheme.TEXT_WHITE);
        batchTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        batchTable.setRowHeight(38);
        batchTable.setShowGrid(false);
        batchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        batchTable.setSelectionBackground(new Color(56, 189, 248, 45));
        batchTable.setSelectionForeground(Color.WHITE);

        batchTable.getTableHeader().setBackground(new Color(20, 27, 40));
        batchTable.getTableHeader().setForeground(new Color(148, 163, 184));
        batchTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        batchTable.getTableHeader().setPreferredSize(new Dimension(0, 34));
        batchTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_GLASS));

        batchTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        batchTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        batchTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        batchTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        batchTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        batchTable.getColumnModel().getColumn(5).setPreferredWidth(130);
        batchTable.getColumnModel().getColumn(6).setPreferredWidth(170);

        batchTable.getColumnModel().getColumn(3).setCellRenderer(new AssetCountPillRenderer());
        batchTable.getColumnModel().getColumn(4).setCellRenderer(new OfficerBadgeRenderer());
        batchTable.getColumnModel().getColumn(6).setCellRenderer(new ReSubmissionStatusPillRenderer());

        DefaultTableCellRenderer regularRenderer = new DefaultTableCellRenderer() {
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
            if (i != 3 && i != 4 && i != 6) {
                batchTable.getColumnModel().getColumn(i).setCellRenderer(regularRenderer);
            }
        }

        batchTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonsState();
            }
        });

        batchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openBatchGalleryModal();
            }
        });

        JScrollPane scroll = new JScrollPane(batchTable);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
        return scroll;
    }

    private void updateActionButtonsState() {
        int row = batchTable.getSelectedRow();
        if (row == -1 || row >= batchStatuses.size()) {
            closeCaseBtn.setEnabled(true);
            flagBtn.setEnabled(true);
            return;
        }

        String st = batchStatuses.get(row);
        boolean isClosedOrVerified = st != null && ("VERIFIED".equalsIgnoreCase(st) || "CLOSED".equalsIgnoreCase(st));

        closeCaseBtn.setEnabled(!isClosedOrVerified);
        flagBtn.setEnabled(!isClosedOrVerified);
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

    public void loadEvidenceData() {
        batchTableModel.setRowCount(0);
        batchCaseNumbers.clear();
        batchCaseTitles.clear();
        batchCollectors.clear();
        batchCollectorFullNames.clear();
        batchCollectorBadgeIds.clear();
        batchUploadTimes.clear();
        batchStatuses.clear();
        batchItemCounts.clear();
        batchRevisions.clear();
        batchIsResubmitted.clear();

        int total = 0, pending = 0, verified = 0, flagged = 0;
        String selectedStatus = (String) statusFilterCombo.getSelectedItem();
        String searchTxt = searchField.getText().trim();

        try (Connection con = DBConnection.getConnection()) {
            if (con == null) return;

            String kpiSql =
                    "SELECT " +
                            "  COUNT(*) AS total_dossiers, " +
                            "  COALESCE(SUM(CASE WHEN batch_status = 'PENDING REVIEW' THEN 1 ELSE 0 END), 0) AS pending_dossiers, " +
                            "  COALESCE(SUM(CASE WHEN batch_status = 'VERIFIED' THEN 1 ELSE 0 END), 0) AS verified_dossiers, " +
                            "  COALESCE(SUM(CASE WHEN batch_status = 'FLAGGED' THEN 1 ELSE 0 END), 0) AS flagged_dossiers " +
                            "FROM (" +
                            "  SELECT " +
                            "    COALESCE(c.case_number, e.case_number) AS docket, " +
                            "    CASE " +
                            "      WHEN UPPER(MAX(c.status)) = 'CLOSED' THEN 'VERIFIED' " +
                            "      WHEN SUM(CASE WHEN UPPER(e.status) = 'FLAGGED' THEN 1 ELSE 0 END) > 0 THEN 'FLAGGED' " +
                            "      WHEN SUM(CASE WHEN UPPER(e.status) LIKE '%PENDING%' THEN 1 ELSE 0 END) > 0 THEN 'PENDING REVIEW' " +
                            "      ELSE 'VERIFIED' " +
                            "    END AS batch_status " +
                            "  FROM case_evidence e " +
                            "  LEFT JOIN cases c ON e.case_id = c.id " +
                            "  WHERE UPPER(e.status) != 'SUPERSEDED' " +
                            "  GROUP BY COALESCE(c.case_number, e.case_number) " +
                            ") AS dossiers";

            try (PreparedStatement psKpi = con.prepareStatement(kpiSql);
                 ResultSet rsKpi = psKpi.executeQuery()) {
                if (rsKpi.next()) {
                    total = rsKpi.getInt("total_dossiers");
                    pending = rsKpi.getInt("pending_dossiers");
                    verified = rsKpi.getInt("verified_dossiers");
                    flagged = rsKpi.getInt("flagged_dossiers");
                }
            }

            StringBuilder sql = new StringBuilder(
                    "SELECT COALESCE(c.case_number, e.case_number) AS docket_num, " +
                            "COALESCE(MAX(c.title), 'Active Incident Investigation') AS docket_title, " +
                            "MAX(e.collected_by) AS collector_username, " +
                            "COALESCE(MAX(u.full_name), MAX(e.collected_by)) AS collector_name, " +
                            "COALESCE(MAX(u.id_number), 'BADGE-FIELD') AS collector_badge, " +
                            "MAX(e.uploaded_at) AS last_upload_time, " +
                            "MAX(COALESCE(e.revision_number, 1)) AS max_rev, " +
                            "MAX(COALESCE(e.is_resubmission, 0)) AS was_resubmitted, " +
                            "CASE " +
                            "  WHEN UPPER(MAX(c.status)) = 'CLOSED' THEN 'VERIFIED' " +
                            "  WHEN SUM(CASE WHEN UPPER(e.status) = 'FLAGGED' THEN 1 ELSE 0 END) > 0 THEN 'FLAGGED' " +
                            "  WHEN SUM(CASE WHEN UPPER(e.status) LIKE '%PENDING%' THEN 1 ELSE 0 END) > 0 THEN 'PENDING REVIEW' " +
                            "  ELSE 'VERIFIED' " +
                            "END AS consolidated_status, " +
                            "COUNT(e.id) AS total_files " +
                            "FROM case_evidence e " +
                            "LEFT JOIN cases c ON e.case_id = c.id " +
                            "LEFT JOIN users u ON LOWER(TRIM(e.collected_by)) = LOWER(TRIM(u.username)) " +
                            "WHERE UPPER(e.status) != 'SUPERSEDED' "
            );

            if (!searchTxt.isEmpty()) {
                sql.append("AND (e.case_number LIKE ? OR c.case_number LIKE ? OR c.title LIKE ? OR e.title LIKE ? OR u.full_name LIKE ? OR e.collected_by LIKE ?) ");
            }

            sql.append("GROUP BY COALESCE(c.case_number, e.case_number) ");

            if (selectedStatus != null && !selectedStatus.equals("ALL STATUS")) {
                sql.append("HAVING consolidated_status = '").append(selectedStatus).append("' ");
            }

            sql.append("ORDER BY last_upload_time DESC");

            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                if (!searchTxt.isEmpty()) {
                    String param = "%" + searchTxt + "%";
                    for (int i = 1; i <= 6; i++) {
                        ps.setString(i, param);
                    }
                }

                try (ResultSet rs = ps.executeQuery()) {
                    int serialNo = 1;
                    while (rs.next()) {
                        String docket = rs.getString("docket_num") != null ? rs.getString("docket_num") : "UNASSIGNED";
                        String cTitle = rs.getString("docket_title");
                        String username = rs.getString("collector_username") != null ? rs.getString("collector_username") : "Investigator";
                        String fullName = rs.getString("collector_name") != null ? rs.getString("collector_name") : username;
                        String badgeId = rs.getString("collector_badge") != null ? rs.getString("collector_badge") : "BADGE-FIELD";
                        String time = rs.getString("last_upload_time") != null ? rs.getString("last_upload_time") : "--";
                        String st = rs.getString("consolidated_status");
                        int count = rs.getInt("total_files");
                        int rev = rs.getInt("max_rev");
                        boolean resub = rs.getInt("was_resubmitted") == 1 || rev > 1;

                        batchCaseNumbers.add(docket);
                        batchCaseTitles.add(cTitle);
                        batchCollectors.add(username);
                        batchCollectorFullNames.add(fullName);
                        batchCollectorBadgeIds.add(badgeId);
                        batchUploadTimes.add(time);
                        batchStatuses.add(st);
                        batchItemCounts.add(count);
                        batchRevisions.add(rev);
                        batchIsResubmitted.add(resub);

                        String officerDisplay = fullName + " [" + username + "] (" + badgeId + ")";
                        String displayStatus = st;
                        if (resub && "PENDING REVIEW".equalsIgnoreCase(st)) {
                            displayStatus = "RE-SUBMITTED (REV #" + rev + ")";
                        }

                        batchTableModel.addRow(new Object[]{
                                "#" + (serialNo++),
                                docket,
                                cTitle,
                                count + " Files Attached",
                                officerDisplay,
                                time,
                                displayStatus
                        });
                    }
                }
            }

            final int fTotal = total, fPending = pending, fVerified = verified, fFlagged = flagged;
            SwingUtilities.invokeLater(() -> {
                totalEvidenceVal.setText(String.valueOf(fTotal));
                pendingReviewVal.setText(String.valueOf(fPending));
                verifiedVal.setText(String.valueOf(fVerified));
                flaggedVal.setText(String.valueOf(fFlagged));
                updateActionButtonsState();
            });

        } catch (Exception ex) {
            System.err.println("Evidence batch group error: " + ex.getMessage());
        }
    }

    private void openBatchGalleryModal() {
        int row = batchTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an evidence dossier from the table to inspect.");
            return;
        }

        String caseNum = batchCaseNumbers.get(row);
        String caseTitle = batchCaseTitles.get(row);
        String username = batchCollectors.get(row);
        String fullName = batchCollectorFullNames.get(row);
        String badgeId = batchCollectorBadgeIds.get(row);
        String uploadTime = batchUploadTimes.get(row);
        String status = batchStatuses.get(row);
        int rev = batchRevisions.get(row);
        boolean isResub = batchIsResubmitted.get(row);

        EvidenceDossierModal modal = new EvidenceDossierModal(
                (Frame) SwingUtilities.getWindowAncestor(this),
                caseNum,
                caseTitle,
                username,
                fullName,
                badgeId,
                uploadTime,
                status,
                rev,
                isResub
        );
        modal.setVisible(true);
    }

    private void handleCloseCase() {
        int row = batchTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select a case evidence package to authorize closure.");
            return;
        }

        String caseNum = batchCaseNumbers.get(row);
        String officerName = batchCollectorFullNames.get(row);
        String currentStatus = batchStatuses.get(row);

        if ("VERIFIED".equalsIgnoreCase(currentStatus) || "CLOSED".equalsIgnoreCase(currentStatus)) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "ALREADY CLOSED", "Case " + caseNum + " is already verified and closed.");
            return;
        }

        boolean confirm = InvestigatorsPanel.GlassConfirmDialog.confirm(
                this,
                "AUTHORIZE CASE CLOSURE",
                "Are you sure you want to close Case " + caseNum + " submitted by Detective " + officerName + "?\nAll files will be marked as VERIFIED and the case docket will be permanently CLOSED."
        );

        if (!confirm) return;

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            String evSql = "UPDATE case_evidence SET status = 'VERIFIED' WHERE case_number = ? OR case_id = (SELECT id FROM cases WHERE case_number = ? LIMIT 1)";
            try (PreparedStatement ps1 = con.prepareStatement(evSql)) {
                ps1.setString(1, caseNum);
                ps1.setString(2, caseNum);
                ps1.executeUpdate();
            }

            String caseSql = "UPDATE cases SET status = 'CLOSED', updated_at = NOW() WHERE case_number = ?";
            try (PreparedStatement ps2 = con.prepareStatement(caseSql)) {
                ps2.setString(1, caseNum);
                ps2.executeUpdate();
            }

            con.commit();
            AuditLogger.log("admin", "CASE CLOSED: Forensic evidence verified and Case [" + caseNum + "] officially closed by administration.");

            InvestigatorsPanel.GlassMessageDialog.showSuccess(this, "CASE CLOSED", "Case " + caseNum + " is closed. Both dashboards have been synchronized.");
            loadEvidenceData();

        } catch (Exception ex) {
            InvestigatorsPanel.GlassMessageDialog.showError(this, "CLOSURE FAILED", ex.getMessage());
        }
    }

    // =========================================================================
    // MODAL DIALOG PROMPTING ADMIN FOR FLAGGING REASON (CLEAN EDITABLE TEXT AREA)
    // =========================================================================
    private void openFlagDiscrepancyModal() {
        int row = batchTable.getSelectedRow();
        if (row == -1) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "SELECTION REQUIRED", "Please select an evidence dossier from the table first.");
            return;
        }

        String caseNum = batchCaseNumbers.get(row);
        String officerName = batchCollectorFullNames.get(row);
        String currentStatus = batchStatuses.get(row);

        if ("VERIFIED".equalsIgnoreCase(currentStatus) || "CLOSED".equalsIgnoreCase(currentStatus)) {
            InvestigatorsPanel.GlassMessageDialog.showWarning(this, "ACTION BLOCKED", "Cannot flag an already closed or verified case dossier.");
            return;
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Flag Evidence Discrepancy - " + caseNum, true);
        dialog.getContentPane().setBackground(new Color(15, 23, 42));
        dialog.setLayout(new BorderLayout(14, 14));

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(16, 20, 0, 20));

        JLabel h1 = new JLabel("FLAG FORENSIC DISCREPANCY & NOTIFY DETECTIVE");
        h1.setFont(new Font("Segoe UI", Font.BOLD, 15));
        h1.setForeground(new Color(248, 113, 113));

        JLabel h2 = new JLabel("Case Docket: " + caseNum + " • Lead Officer: " + officerName);
        h2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        h2.setForeground(new Color(148, 163, 184));

        header.add(h1);
        header.add(h2);
        dialog.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(4, 20, 4, 20));

        JLabel noteLbl = new JLabel("Specific Audit Deficiencies & Corrective Instructions for Detective *");
        noteLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        noteLbl.setForeground(new Color(251, 191, 36));

        JTextArea reasonText = new JTextArea(5, 24);
        reasonText.setBackground(new Color(24, 32, 47));
        reasonText.setForeground(Color.WHITE);
        reasonText.setCaretColor(UITheme.ACCENT_CYAN);
        reasonText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        reasonText.setLineWrap(true);
        reasonText.setWrapStyleWord(true);
        reasonText.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(reasonText);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(239, 68, 68, 120), 1));

        body.add(noteLbl, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        dialog.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        actions.setOpaque(false);

        JButton cancelBtn = createStyledButton("Cancel", new Color(30, 41, 59));
        JButton submitFlagBtn = createStyledButton("Submit Discrepancy & Notify Detective", new Color(239, 68, 68));

        cancelBtn.addActionListener(e -> dialog.dispose());
        submitFlagBtn.addActionListener(e -> {
            String customNote = reasonText.getText().trim();
            if (customNote.isEmpty()) {
                InvestigatorsPanel.GlassMessageDialog.showWarning(dialog, "REASON REQUIRED", "Please enter the discrepancy reason and instructions for the detective.");
                return;
            }

            String fullFlagFeedback = customNote;

            String sql = "UPDATE case_evidence SET status = 'FLAGGED', " +
                    "notes = CONCAT(COALESCE(notes, ''), '\n[ADMIN FLAGGED]: ', ?) " +
                    "WHERE (case_number = ? OR case_id = (SELECT id FROM cases WHERE case_number = ? LIMIT 1)) AND UPPER(status) != 'SUPERSEDED'";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, fullFlagFeedback);
                ps.setString(2, caseNum);
                ps.setString(3, caseNum);
                ps.executeUpdate();

                AuditLogger.log("admin", "EVIDENCE FLAGGED: Case [" + caseNum + "] flagged by Admin. Reason: " + customNote);

                dialog.dispose();
                InvestigatorsPanel.GlassMessageDialog.showSuccess(
                        this,
                        "EVIDENCE FLAGGED",
                        "Evidence for Case " + caseNum + " is marked as FLAGGED.\nNotice dispatched to Detective " + officerName + "."
                );
                loadEvidenceData();
            } catch (Exception ex) {
                InvestigatorsPanel.GlassMessageDialog.showError(this, "UPDATE FAILURE", ex.getMessage());
            }
        });

        actions.add(cancelBtn);
        actions.add(submitFlagBtn);
        dialog.add(actions, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(620, 330);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // =========================================================================
    // CYBERPUNK MULTI-ASSET GALLERY MODAL WITH RESUBMISSION BANNER
    // =========================================================================
    private static class EvidenceDossierModal extends JDialog {

        private final String caseNumber;
        private final DefaultListModel<EvidenceItem> assetListModel = new DefaultListModel<>();
        private JList<EvidenceItem> assetList;

        private JPanel previewViewerPanel;
        private JLabel metaTitleLbl;
        private JLabel metaTypeLbl;
        private JLabel metaSizeLbl;
        private JTextArea notesArea;

        private Clip audioClip;
        private Timer audioProgressTimer;
        private JSlider audioSlider;
        private JLabel audioTimeLabel;
        private JButton playBtn;

        public static class EvidenceItem {
            int id;
            String evNumber;
            String fileName;
            String fileType;
            String filePath;
            String notes;
            String status;

            public EvidenceItem(int id, String evNumber, String fileName, String fileType, String filePath, String notes, String status) {
                this.id = id;
                this.evNumber = evNumber;
                this.fileName = fileName;
                this.fileType = fileType;
                this.filePath = filePath;
                this.notes = notes;
                this.status = status;
            }

            @Override
            public String toString() {
                return fileName;
            }
        }

        public EvidenceDossierModal(Frame parent, String caseNumber, String caseTitle,
                                    String officerUsername, String officerFullName, String officerBadgeId,
                                    String uploadTime, String status, int revisionNumber, boolean isResubmitted) {
            super(parent, "Evidence Deck - " + caseNumber, true);
            this.caseNumber = caseNumber;

            setUndecorated(true);
            setSize(980, 680);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            JPanel card = new JPanel(new BorderLayout(0, 12)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 252));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(new Color(56, 189, 248, 140));
                    g2.setStroke(new BasicStroke(1.4f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                    g2.dispose();
                }
            };
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(18, 22, 16, 22));

            JPanel headerDeck = new JPanel(new BorderLayout(0, 8));
            headerDeck.setOpaque(false);

            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);

            JLabel title = new JLabel("CASE EVIDENCE CONSOLE: " + caseNumber + " — " + caseTitle);
            title.setFont(new Font("Segoe UI", Font.BOLD, 15));
            title.setForeground(UITheme.ACCENT_CYAN);

            JButton closeBtn = new JButton("×");
            closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 22));
            closeBtn.setForeground(new Color(148, 163, 184));
            closeBtn.setContentAreaFilled(false);
            closeBtn.setBorderPainted(false);
            closeBtn.setFocusPainted(false);
            closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            closeBtn.addActionListener(e -> {
                cleanupAudio();
                dispose();
            });

            titleRow.add(title, BorderLayout.WEST);
            titleRow.add(closeBtn, BorderLayout.EAST);
            headerDeck.add(titleRow, BorderLayout.NORTH);

            JPanel officerBadgeCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(24, 32, 47, 220));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(isResubmitted ? new Color(251, 191, 36, 120) : new Color(52, 211, 153, 100));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            officerBadgeCard.setOpaque(false);
            officerBadgeCard.setBorder(new EmptyBorder(4, 10, 4, 10));

            JLabel offLbl1 = new JLabel("SUBMITTING INVESTIGATOR: " + officerFullName.toUpperCase());
            offLbl1.setFont(new Font("Segoe UI", Font.BOLD, 12));
            offLbl1.setForeground(new Color(52, 211, 153));

            JLabel offLbl2 = new JLabel("Badge: [" + officerBadgeId + "]");
            offLbl2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            offLbl2.setForeground(Color.WHITE);

            JLabel offLbl3 = new JLabel("ID: [" + officerUsername + "]");
            offLbl3.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            offLbl3.setForeground(new Color(148, 163, 184));

            JLabel offLbl4 = new JLabel("Logged: " + uploadTime);
            offLbl4.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            offLbl4.setForeground(new Color(251, 191, 36));

            officerBadgeCard.add(offLbl1);
            officerBadgeCard.add(new JLabel("•"));
            officerBadgeCard.add(offLbl2);
            officerBadgeCard.add(new JLabel("•"));
            officerBadgeCard.add(offLbl3);
            officerBadgeCard.add(new JLabel("•"));
            officerBadgeCard.add(offLbl4);

            if (isResubmitted) {
                JLabel resubPill = new JLabel("  RE-TRANSMITTED BATCH (REV #" + revisionNumber + ")  ");
                resubPill.setFont(new Font("Segoe UI", Font.BOLD, 10));
                resubPill.setForeground(new Color(251, 191, 36));
                resubPill.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(251, 191, 36, 140), 1, true),
                        new EmptyBorder(2, 6, 2, 6)
                ));
                officerBadgeCard.add(new JLabel("•"));
                officerBadgeCard.add(resubPill);
            }

            headerDeck.add(officerBadgeCard, BorderLayout.CENTER);

            if (isResubmitted) {
                JLabel resubNotice = new JLabel(" ⚠ ACTION NOTICE: Detective re-transmitted this batch to correct a previously flagged discrepancy (Revision #" + revisionNumber + "). Overwriting previous flags.");
                resNoticeSetStyle(resubNotice);
                headerDeck.add(resubNotice, BorderLayout.SOUTH);
            }

            card.add(headerDeck, BorderLayout.NORTH);

            JPanel splitBody = new JPanel(new GridLayout(1, 2, 16, 0));
            splitBody.setOpaque(false);

            JPanel leftDeck = new JPanel(new BorderLayout(0, 8));
            leftDeck.setOpaque(false);

            JLabel queueHeader = new JLabel("ACTIVE EVIDENCE ASSETS IN THIS REVISION");
            queueHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
            queueHeader.setForeground(new Color(148, 163, 184));
            leftDeck.add(queueHeader, BorderLayout.NORTH);

            assetList = new JList<>(assetListModel);
            assetList.setBackground(new Color(20, 27, 40));
            assetList.setForeground(Color.WHITE);
            assetList.setSelectionBackground(new Color(56, 189, 248, 45));
            assetList.setSelectionForeground(UITheme.ACCENT_CYAN);
            assetList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            assetList.setCellRenderer(new AssetListCellRenderer());

            assetList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    displaySelectedAsset(assetList.getSelectedValue());
                }
            });

            JScrollPane listScroll = new JScrollPane(assetList);
            listScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));
            leftDeck.add(listScroll, BorderLayout.CENTER);

            JPanel metaBox = new JPanel(new GridLayout(3, 1, 0, 4));
            metaBox.setOpaque(false);
            metaBox.setBorder(new EmptyBorder(8, 0, 0, 0));

            metaTitleLbl = new JLabel("Selected File: None");
            metaTitleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            metaTitleLbl.setForeground(Color.WHITE);

            metaTypeLbl = new JLabel("Classification: --");
            metaTypeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            metaTypeLbl.setForeground(new Color(52, 211, 153));

            metaSizeLbl = new JLabel("Storage Size: --");
            metaSizeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            metaSizeLbl.setForeground(new Color(148, 163, 184));

            metaBox.add(metaTitleLbl);
            metaBox.add(metaTypeLbl);
            metaBox.add(metaSizeLbl);
            leftDeck.add(metaBox, BorderLayout.SOUTH);

            splitBody.add(leftDeck);

            JPanel rightDeck = new JPanel(new BorderLayout(0, 10));
            rightDeck.setOpaque(false);

            previewViewerPanel = new JPanel(new BorderLayout());
            previewViewerPanel.setOpaque(false);
            previewViewerPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1, true),
                    new EmptyBorder(8, 8, 8, 8)
            ));

            rightDeck.add(previewViewerPanel, BorderLayout.CENTER);

            JPanel notesPanel = new JPanel(new BorderLayout(0, 4));
            notesPanel.setOpaque(false);

            JLabel notesHeader = new JLabel("Chain of Custody & Investigator Notes:");
            notesHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
            notesHeader.setForeground(new Color(148, 163, 184));

            notesArea = new JTextArea(3, 20);
            notesArea.setBackground(new Color(24, 32, 47));
            notesArea.setForeground(Color.WHITE);
            notesArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            notesArea.setEditable(false);
            notesArea.setLineWrap(true);
            notesArea.setWrapStyleWord(true);

            JScrollPane notesScroll = new JScrollPane(notesArea);
            notesScroll.setPreferredSize(new Dimension(0, 80));
            notesScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1));

            notesPanel.add(notesHeader, BorderLayout.NORTH);
            notesPanel.add(notesScroll, BorderLayout.CENTER);
            rightDeck.add(notesPanel, BorderLayout.SOUTH);

            splitBody.add(rightDeck);

            card.add(splitBody, BorderLayout.CENTER);

            JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            bottomBar.setOpaque(false);

            JButton openNativeBtn = new JButton("Open in System App ↗");
            openNativeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            openNativeBtn.setBackground(new Color(30, 41, 59));
            openNativeBtn.setForeground(Color.WHITE);
            openNativeBtn.setFocusPainted(false);
            openNativeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openNativeBtn.setBorder(new EmptyBorder(8, 16, 8, 16));
            openNativeBtn.addActionListener(e -> {
                EvidenceItem item = assetList.getSelectedValue();
                if (item != null && item.filePath != null) {
                    try { Desktop.getDesktop().open(new File(item.filePath)); } catch (Exception ignored) {}
                }
            });

            JButton dismissBtn = new JButton("Close Deck");
            dismissBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            dismissBtn.setBackground(new Color(59, 130, 246));
            dismissBtn.setForeground(Color.WHITE);
            dismissBtn.setFocusPainted(false);
            dismissBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            dismissBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
            dismissBtn.addActionListener(e -> {
                cleanupAudio();
                dispose();
            });

            bottomBar.add(openNativeBtn);
            bottomBar.add(dismissBtn);
            card.add(bottomBar, BorderLayout.SOUTH);

            setContentPane(card);

            loadCaseAssetsFromDatabase();
        }

        private static void resNoticeSetStyle(JLabel resubNotice) {
            resubNotice.setFont(new Font("Segoe UI", Font.BOLD, 11));
            resubNotice.setForeground(new Color(254, 243, 199));
            resubNotice.setOpaque(true);
            resubNotice.setBackground(new Color(120, 53, 15, 160));
            resubNotice.setBorder(new EmptyBorder(4, 8, 4, 8));
        }

        private void loadCaseAssetsFromDatabase() {
            assetListModel.clear();
            String sql = "SELECT id, evidence_number, file_name, file_type, file_path, notes, status " +
                    "FROM case_evidence WHERE (case_number = ? OR case_id = (SELECT id FROM cases WHERE case_number = ? LIMIT 1)) " +
                    "  AND UPPER(status) != 'SUPERSEDED' " +
                    "ORDER BY id ASC";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, caseNumber);
                ps.setString(2, caseNumber);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        assetListModel.addElement(new EvidenceItem(
                                rs.getInt("id"),
                                rs.getString("evidence_number"),
                                rs.getString("file_name"),
                                rs.getString("file_type"),
                                rs.getString("file_path"),
                                rs.getString("notes"),
                                rs.getString("status")
                        ));
                    }
                }

                if (!assetListModel.isEmpty()) {
                    assetList.setSelectedIndex(0);
                } else {
                    previewViewerPanel.removeAll();
                    previewViewerPanel.add(new JLabel("No files logged for this case.", SwingConstants.CENTER));
                    previewViewerPanel.revalidate();
                    previewViewerPanel.repaint();
                }

            } catch (Exception ex) {
                System.err.println("Load evidence assets error: " + ex.getMessage());
            }
        }

        private void displaySelectedAsset(EvidenceItem item) {
            cleanupAudio();
            if (item == null) return;

            metaTitleLbl.setText("Selected Asset: " + item.fileName);
            metaTypeLbl.setText("Classification: " + item.fileType.toUpperCase() + "  •  Review: " + item.status);
            notesArea.setText(item.notes != null && !item.notes.isEmpty() ? item.notes : "No field observations attached.");

            File f = item.filePath != null ? new File(item.filePath) : null;
            metaSizeLbl.setText("Storage Size: " + (f != null && f.exists() ? formatFileSize(f.length()) : "Offline"));

            previewViewerPanel.removeAll();

            if (f == null || !f.exists()) {
                previewViewerPanel.add(new JLabel("File unavailable on local storage.", SwingConstants.CENTER));
            } else {
                String type = item.fileType;
                String nameLower = f.getName().toLowerCase();

                if ("Image".equalsIgnoreCase(type) || nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".bmp")) {
                    previewViewerPanel.add(buildImagePreview(f), BorderLayout.CENTER);
                } else if ("Audio".equalsIgnoreCase(type) || nameLower.endsWith(".wav") || nameLower.endsWith(".mp3")) {
                    previewViewerPanel.add(buildAudioPlayerDeck(f), BorderLayout.CENTER);
                } else if ("Video".equalsIgnoreCase(type) || nameLower.endsWith(".mp4") || nameLower.endsWith(".avi") || nameLower.endsWith(".mov") || nameLower.endsWith(".mkv")) {
                    previewViewerPanel.add(buildVideoPlayerDeck(f), BorderLayout.CENTER);
                } else {
                    previewViewerPanel.add(buildDocumentDeck(f), BorderLayout.CENTER);
                }
            }

            previewViewerPanel.revalidate();
            previewViewerPanel.repaint();
        }

        private Component buildImagePreview(File file) {
            try {
                BufferedImage bimg = ImageIO.read(file);
                if (bimg != null) {
                    int maxW = 420, maxH = 340;
                    int origW = bimg.getWidth(), origH = bimg.getHeight();
                    double scale = Math.min((double) maxW / origW, (double) maxH / origH);
                    int finalW = (int) (origW * scale);
                    int finalH = (int) (origH * scale);

                    Image scaled = bimg.getScaledInstance(finalW, finalH, Image.SCALE_SMOOTH);
                    JLabel imgLbl = new JLabel(new ImageIcon(scaled), SwingConstants.CENTER);
                    imgLbl.setBackground(new Color(10, 15, 26));
                    imgLbl.setOpaque(true);
                    return imgLbl;
                }
            } catch (Exception ignored) {}
            return new JLabel("Corrupted image media.", SwingConstants.CENTER);
        }

        private Component buildAudioPlayerDeck(File audioFile) {
            JPanel deck = new JPanel();
            deck.setLayout(new BoxLayout(deck, BoxLayout.Y_AXIS));
            deck.setOpaque(false);
            deck.setBorder(new EmptyBorder(30, 20, 20, 20));

            JLabel iconLbl = new JLabel("AUDIO EVIDENCE TAPE", SwingConstants.CENTER);
            iconLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            iconLbl.setForeground(new Color(236, 72, 153));
            iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel nameLbl = new JLabel(audioFile.getName(), SwingConstants.CENTER);
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            nameLbl.setForeground(Color.WHITE);
            nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            audioSlider = new JSlider(0, 100, 0);
            audioSlider.setOpaque(false);
            audioSlider.setAlignmentX(Component.CENTER_ALIGNMENT);

            audioTimeLabel = new JLabel("00:00 / 00:00", SwingConstants.CENTER);
            audioTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            audioTimeLabel.setForeground(new Color(148, 163, 184));
            audioTimeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            controls.setOpaque(false);

            playBtn = new JButton("Play");
            playBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            playBtn.setBackground(new Color(236, 72, 153));
            playBtn.setForeground(Color.WHITE);
            playBtn.setFocusPainted(false);
            playBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            JButton stopBtn = new JButton("Stop");
            stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            stopBtn.setBackground(new Color(30, 41, 59));
            stopBtn.setForeground(Color.WHITE);
            stopBtn.setFocusPainted(false);
            stopBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            playBtn.addActionListener(e -> toggleAudioPlayback(audioFile));
            stopBtn.addActionListener(e -> stopAudioPlayback());

            controls.add(playBtn);
            controls.add(stopBtn);

            deck.add(iconLbl);
            deck.add(Box.createVerticalStrut(10));
            deck.add(nameLbl);
            deck.add(Box.createVerticalStrut(20));
            deck.add(audioSlider);
            deck.add(audioTimeLabel);
            deck.add(Box.createVerticalStrut(10));
            deck.add(controls);

            return deck;
        }

        private void toggleAudioPlayback(File file) {
            try {
                if (audioClip != null && audioClip.isRunning()) {
                    audioClip.stop();
                    playBtn.setText("Play");
                    if (audioProgressTimer != null) audioProgressTimer.stop();
                    return;
                }

                if (audioClip == null) {
                    AudioInputStream ais = AudioSystem.getAudioInputStream(file);
                    audioClip = AudioSystem.getClip();
                    audioClip.open(ais);

                    audioClip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP && audioClip.getMicrosecondPosition() >= audioClip.getMicrosecondLength()) {
                            stopAudioPlayback();
                        }
                    });
                }

                audioClip.start();
                playBtn.setText("Pause");

                if (audioProgressTimer == null) {
                    audioProgressTimer = new Timer(100, ev -> {
                        if (audioClip != null && audioClip.isOpen()) {
                            long pos = audioClip.getMicrosecondPosition();
                            long len = audioClip.getMicrosecondLength();
                            if (len > 0) {
                                audioSlider.setValue((int) ((pos * 100) / len));
                                audioTimeLabel.setText(formatMicroseconds(pos) + " / " + formatMicroseconds(len));
                            }
                        }
                    });
                }
                audioProgressTimer.start();

            } catch (Exception ex) {
                try { Desktop.getDesktop().open(file); } catch (Exception ignored) {}
            }
        }

        private void stopAudioPlayback() {
            if (audioClip != null) {
                audioClip.stop();
                audioClip.setMicrosecondPosition(0);
            }
            if (playBtn != null) playBtn.setText("Play");
            if (audioSlider != null) audioSlider.setValue(0);
            if (audioProgressTimer != null) audioProgressTimer.stop();
        }

        private void cleanupAudio() {
            stopAudioPlayback();
            if (audioClip != null) {
                audioClip.close();
                audioClip = null;
            }
        }

        private Component buildVideoPlayerDeck(File videoFile) {
            JPanel deck = new JPanel(new GridBagLayout());
            deck.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);

            JLabel iconLbl = new JLabel("VIDEO SURVEILLANCE FEED", SwingConstants.CENTER);
            iconLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            iconLbl.setForeground(new Color(168, 85, 247));
            iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel nameLbl = new JLabel(videoFile.getName(), SwingConstants.CENTER);
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            nameLbl.setForeground(Color.WHITE);
            nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton playVidBtn = new JButton("Launch Video Player");
            playVidBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            playVidBtn.setBackground(new Color(168, 85, 247));
            playVidBtn.setForeground(Color.WHITE);
            playVidBtn.setFocusPainted(false);
            playVidBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            playVidBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            playVidBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
            playVidBtn.addActionListener(e -> {
                try { Desktop.getDesktop().open(videoFile); } catch (Exception ignored) {}
            });

            card.add(iconLbl);
            card.add(Box.createVerticalStrut(10));
            card.add(nameLbl);
            card.add(Box.createVerticalStrut(22));
            card.add(playVidBtn);

            deck.add(card);
            return deck;
        }

        private Component buildDocumentDeck(File docFile) {
            JPanel deck = new JPanel(new GridBagLayout());
            deck.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);

            JLabel iconLbl = new JLabel("FORENSIC DOCUMENT / REPORT", SwingConstants.CENTER);
            iconLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
            iconLbl.setForeground(new Color(251, 191, 36));
            iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel nameLbl = new JLabel(docFile.getName(), SwingConstants.CENTER);
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            nameLbl.setForeground(Color.WHITE);
            nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton openDocBtn = new JButton("Open Document Viewer");
            openDocBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            openDocBtn.setBackground(new Color(251, 191, 36));
            openDocBtn.setForeground(new Color(15, 23, 42));
            openDocBtn.setFocusPainted(false);
            openDocBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openDocBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            openDocBtn.setBorder(new EmptyBorder(10, 18, 10, 18));
            openDocBtn.addActionListener(e -> {
                try { Desktop.getDesktop().open(docFile); } catch (Exception ignored) {}
            });

            card.add(iconLbl);
            card.add(Box.createVerticalStrut(10));
            card.add(nameLbl);
            card.add(Box.createVerticalStrut(22));
            card.add(openDocBtn);

            deck.add(card);
            return deck;
        }

        private static String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            char pre = "KMGTPE".charAt(exp - 1);
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }

        private static String formatMicroseconds(long us) {
            long totalSec = us / 1_000_000;
            long mins = totalSec / 60;
            long secs = totalSec % 60;
            return String.format("%02d:%02d", mins, secs);
        }

        private static class AssetListCellRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EvidenceItem) {
                    EvidenceItem it = (EvidenceItem) value;
                    lbl.setText("  " + it.fileName + "  [" + it.fileType.toUpperCase() + "]");
                    lbl.setBorder(new EmptyBorder(8, 10, 8, 10));
                }
                return lbl;
            }
        }
    }

    // =========================================================================
    // UI Helpers & Resubmission Pill Renderers
    // =========================================================================
    private static class AssetCountPillRenderer extends DefaultTableCellRenderer {
        public AssetCountPillRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString() : "0 Files";
            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            wrap.setOpaque(true);
            wrap.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            JLabel pill = new JLabel("  " + text.toUpperCase() + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(56, 189, 248, 35));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(56, 189, 248, 140));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            pill.setFont(new Font("Segoe UI", Font.BOLD, 10));
            pill.setForeground(UITheme.ACCENT_CYAN);
            pill.setBorder(new EmptyBorder(1, 6, 1, 6));
            wrap.add(pill);
            return wrap;
        }
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

    private static class ReSubmissionStatusPillRenderer extends DefaultTableCellRenderer {
        public ReSubmissionStatusPillRenderer() { setHorizontalAlignment(SwingConstants.CENTER); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim() : "PENDING REVIEW";
            JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            badgeWrapper.setOpaque(true);
            badgeWrapper.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));

            Color badgeBg = new Color(251, 191, 36, 35);
            Color badgeText = new Color(251, 191, 36);

            if (text.contains("RE-SUBMITTED")) {
                badgeBg = new Color(56, 189, 248, 45);
                badgeText = UITheme.ACCENT_CYAN;
            } else if ("VERIFIED".equalsIgnoreCase(text)) {
                badgeBg = new Color(52, 211, 153, 35);
                badgeText = new Color(52, 211, 153);
            } else if ("FLAGGED".equalsIgnoreCase(text)) {
                badgeBg = new Color(239, 68, 68, 35);
                badgeText = new Color(248, 113, 113);
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
                    g2.setColor(new Color(fTxt.getRed(), fTxt.getGreen(), fTxt.getBlue(), 140));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    super.paintComponent(g);
                    g2.dispose();
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