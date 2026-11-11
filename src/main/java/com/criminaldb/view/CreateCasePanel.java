package com.criminaldb.view;

import com.criminaldb.config.DBConnection;
import com.criminaldb.util.AuditLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CreateCasePanel extends JPanel {

    private final String adminName;
    private final Runnable onCaseCreatedCallback;

    // Sub-Tab Switcher
    private CardLayout innerCardLayout;
    private JPanel innerContainer;
    private JButton caseDetailsTabBtn;
    private JButton victimTabBtn;

    // Case Details Form Fields
    private JTextField caseIdField;
    private JTextField caseTitleField;
    private AnimatedGlassComboBox crimeTypeCombo;
    private AnimatedGlassComboBox caseLevelCombo;
    private JTextField locationField;
    private JTextField dateReportedField;
    private JTextArea caseDescArea;
    private JTextArea initialReportArea;

    // Compact Victim Directory Table & Form
    private DefaultTableModel victimTableModel;
    private JTable victimTable;
    private final List<VictimProfileData> stagedVictims = new ArrayList<>();
    private JLabel victimCountBadge;

    // Victim Input Deck
    private JTextField victimNameInput;
    private JTextField victimDobInput;
    private JTextField victimAgeInput;
    private JComboBox<String> victimGenderInput;
    private JTextField victimPhoneInput;
    private JTextField victimAddressInput;

    private static final String DEFAULT_CRIME_TYPE_PROMPT = "-- Select Crime Type --";
    private static final String DEFAULT_CASE_LEVEL_PROMPT = "-- Select Case Level --";

    public CreateCasePanel(String adminName, Runnable onCaseCreatedCallback) {
        this.adminName = adminName != null ? adminName : "Head Administrator";
        this.onCaseCreatedCallback = onCaseCreatedCallback;

        setOpaque(false);
        setLayout(new BorderLayout());

        initUI();
        generateAutoCaseId();
    }

    private void initUI() {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(10, 20, 12, 20));

        // Top Navigation Bar
        JPanel topHeader = new JPanel(new BorderLayout(12, 0));
        topHeader.setOpaque(false);

        JPanel titleBlock = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleBlock.setOpaque(false);
        JLabel mainTitle = new JLabel("INTAKE & REGISTRATION WORKSPACE");
        mainTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        mainTitle.setForeground(Color.WHITE);
        titleBlock.add(mainTitle);

        JPanel subTabToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        subTabToolbar.setOpaque(false);

        caseDetailsTabBtn = createTabSwitchButton("1. Incident Dossier", true);
        victimTabBtn = createTabSwitchButton("2. Victim Directory (0)", false);

        caseDetailsTabBtn.addActionListener(e -> switchInnerTab("CASE_DETAILS"));
        victimTabBtn.addActionListener(e -> switchInnerTab("VICTIM_DIRECTORY"));

        subTabToolbar.add(caseDetailsTabBtn);
        subTabToolbar.add(victimTabBtn);

        topHeader.add(titleBlock, BorderLayout.WEST);
        topHeader.add(subTabToolbar, BorderLayout.EAST);
        container.add(topHeader, BorderLayout.NORTH);

        // Center Container with CardLayout
        innerCardLayout = new CardLayout();
        innerContainer = new JPanel(innerCardLayout);
        innerContainer.setOpaque(false);

        innerContainer.add(createCaseDetailsTab(), "CASE_DETAILS");
        innerContainer.add(createVictimDirectoryTab(), "VICTIM_DIRECTORY");

        container.add(innerContainer, BorderLayout.CENTER);
        add(container, BorderLayout.CENTER);
    }

    private JButton createTabSwitchButton(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 16, 6, 16));
        updateTabBtnVisual(btn, active);
        return btn;
    }

    private void updateTabBtnVisual(JButton btn, boolean active) {
        btn.setBackground(active ? UITheme.ACCENT_CYAN : new Color(25, 33, 48));
        btn.setForeground(active ? UITheme.BG_DARK : new Color(203, 213, 225));
        btn.setOpaque(active);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(active ? UITheme.ACCENT_CYAN : new Color(56, 189, 248, 60), 1, true),
                new EmptyBorder(5, 14, 5, 14)
        ));
    }

    private void switchInnerTab(String tabName) {
        boolean isCase = "CASE_DETAILS".equals(tabName);
        updateTabBtnVisual(caseDetailsTabBtn, isCase);
        updateTabBtnVisual(victimTabBtn, !isCase);
        innerCardLayout.show(innerContainer, tabName);
    }

    // =========================================================================
    // TAB 1: CASE DETAILS
    // =========================================================================
    private JPanel createCaseDetailsTab() {
        UITheme.GlassCardPanel card = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel formGrid = new JPanel(new GridBagLayout());
        formGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 10, 4, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Case ID & Title
        caseIdField = createCompactField();
        caseIdField.setEditable(false);
        caseIdField.setForeground(UITheme.ACCENT_CYAN);
        caseIdField.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addFormField(formGrid, gbc, 0, 0, 1, "Case ID (Auto Generated)", caseIdField);

        caseTitleField = createCompactField();
        addFormField(formGrid, gbc, 1, 0, 1, "Case Title *", caseTitleField);

        // Row 1: Crime Type & Level
        String[] crimeTypes = {
                DEFAULT_CRIME_TYPE_PROMPT,
                "Cybercrime", "Theft", "Robbery", "Fraud",
                "Missing Person", "Assault", "Homicide", "Property Crime", "Other"
        };
        crimeTypeCombo = new AnimatedGlassComboBox(crimeTypes);
        addFormField(formGrid, gbc, 0, 1, 1, "Case Type *", crimeTypeCombo);

        String[] caseLevels = {
                DEFAULT_CASE_LEVEL_PROMPT, "High", "Medium", "Low"
        };
        caseLevelCombo = new AnimatedGlassComboBox(caseLevels);
        addFormField(formGrid, gbc, 1, 1, 1, "Case Urgency / Level *", caseLevelCombo);

        // Row 2: Location & Date Reported (DD/MM/YYYY)
        locationField = createCompactField();
        addFormField(formGrid, gbc, 0, 2, 1, "Location / Jurisdiction *", locationField);

        dateReportedField = createCompactField();
        dateReportedField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        attachAutoDateFormatter(dateReportedField, null);
        addFormField(formGrid, gbc, 1, 2, 1, "Date Reported (DD/MM/YYYY)", dateReportedField);

        // Row 3: Narratives
        caseDescArea = createStyledTextArea();
        JScrollPane descScroll = createStyledScrollPane(caseDescArea);
        addFormField(formGrid, gbc, 0, 3, 1, "Incident Description & Summary *", descScroll);

        initialReportArea = createStyledTextArea();
        JScrollPane reportScroll = createStyledScrollPane(initialReportArea);
        addFormField(formGrid, gbc, 1, 3, 1, "Initial Investigation Directives *", reportScroll);

        // Row 4: Continue Button
        JButton proceedBtn = new JButton("Next: Configure Victim Directory →");
        proceedBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        proceedBtn.setBackground(new Color(59, 130, 246));
        proceedBtn.setForeground(Color.WHITE);
        proceedBtn.setFocusPainted(false);
        proceedBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        proceedBtn.setBorder(new EmptyBorder(9, 22, 9, 22));
        proceedBtn.addActionListener(e -> switchInnerTab("VICTIM_DIRECTORY"));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(new EmptyBorder(16, 0, 4, 0));
        btnRow.add(proceedBtn);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formGrid.add(btnRow, gbc);

        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formGrid.add(Box.createGlue(), gbc);

        card.add(formGrid, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // TAB 2: COMPACT VICTIM DIRECTORY & STAGING TABLE
    // =========================================================================
    private JPanel createVictimDirectoryTab() {
        UITheme.GlassCardPanel card = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Sub-header Bar
        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setOpaque(false);

        JPanel leftBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBadge.setOpaque(false);

        JLabel vLabel = new JLabel("VICTIM DIRECTORY & COMPLAINANT ROSTER");
        vLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        vLabel.setForeground(UITheme.ACCENT_CYAN);

        victimCountBadge = new JLabel("0 Records");
        victimCountBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        victimCountBadge.setForeground(new Color(52, 211, 153));
        victimCountBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 211, 153, 120), 1, true),
                new EmptyBorder(2, 6, 2, 6)
        ));

        leftBadge.add(vLabel);
        leftBadge.add(victimCountBadge);
        topHeader.add(leftBadge, BorderLayout.WEST);

        card.add(topHeader, BorderLayout.NORTH);

        // Center: Split with Staged Table (Top) and Entry Input Form (Bottom)
        JPanel centerSplit = new JPanel(new BorderLayout(0, 10));
        centerSplit.setOpaque(false);

        // 1. Table of Staged Victims
        String[] vCols = {"#", "VICTIM NAME", "GENDER", "AGE", "DOB", "PHONE (10-DIGIT)", "ADDRESS"};
        victimTableModel = new DefaultTableModel(vCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        victimTable = new JTable(victimTableModel);
        victimTable.setOpaque(false);
        victimTable.setBackground(new Color(15, 23, 42));
        victimTable.setForeground(Color.WHITE);
        victimTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        victimTable.setRowHeight(28);
        victimTable.setShowGrid(false);
        victimTable.setSelectionBackground(new Color(56, 189, 248, 45));
        victimTable.setSelectionForeground(Color.WHITE);

        victimTable.getTableHeader().setBackground(new Color(20, 27, 40));
        victimTable.getTableHeader().setForeground(new Color(148, 163, 184));
        victimTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        victimTable.getTableHeader().setPreferredSize(new Dimension(0, 26));

        victimTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        victimTable.getColumnModel().getColumn(1).setPreferredWidth(170);
        victimTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        victimTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        victimTable.getColumnModel().getColumn(4).setPreferredWidth(95);
        victimTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        victimTable.getColumnModel().getColumn(6).setPreferredWidth(200);

        DefaultTableCellRenderer centerRend = new DefaultTableCellRenderer();
        centerRend.setHorizontalAlignment(SwingConstants.CENTER);
        victimTable.getColumnModel().getColumn(0).setCellRenderer(centerRend);
        victimTable.getColumnModel().getColumn(2).setCellRenderer(centerRend);
        victimTable.getColumnModel().getColumn(3).setCellRenderer(centerRend);
        victimTable.getColumnModel().getColumn(4).setCellRenderer(centerRend);

        JScrollPane tableScroll = new JScrollPane(victimTable);
        tableScroll.setPreferredSize(new Dimension(0, 140));
        tableScroll.getViewport().setBackground(new Color(15, 23, 42));
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 60), 1));

        JPanel tablePanel = new JPanel(new BorderLayout(0, 4));
        tablePanel.setOpaque(false);
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        JButton deleteSelectedBtn = new JButton("Remove Selected Victim");
        deleteSelectedBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        deleteSelectedBtn.setBackground(new Color(239, 68, 68));
        deleteSelectedBtn.setForeground(Color.WHITE);
        deleteSelectedBtn.setFocusPainted(false);
        deleteSelectedBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        deleteSelectedBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
        deleteSelectedBtn.addActionListener(e -> removeSelectedVictimFromTable());

        JPanel delBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        delBar.setOpaque(false);
        delBar.add(deleteSelectedBtn);
        tablePanel.add(delBar, BorderLayout.SOUTH);

        centerSplit.add(tablePanel, BorderLayout.NORTH);

        // 2. Compact Input Form
        JPanel inputCard = new JPanel(new BorderLayout(0, 6));
        inputCard.setOpaque(false);
        inputCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(56, 189, 248, 80), 1, true),
                        " + STAGE VICTIM DETAILS ",
                        0, 0,
                        new Font("Segoe UI", Font.BOLD, 11),
                        new Color(52, 211, 153)
                ),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel grid = new JPanel(new GridLayout(3, 2, 12, 6));
        grid.setOpaque(false);

        victimNameInput = createCompactField();
        victimDobInput = createCompactField(); // Starts completely empty!
        victimAgeInput = createCompactField();

        // Attach date mask and automatic age calculator
        attachAutoDateFormatter(victimDobInput, victimAgeInput);

        victimGenderInput = new JComboBox<>(new String[]{"Male", "Female", "Non-Binary", "Other / Undisclosed"});
        victimGenderInput.setBackground(new Color(25, 33, 48));
        victimGenderInput.setForeground(Color.WHITE);
        victimGenderInput.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        victimPhoneInput = createCompactField();
        // Restrict phone strictly to 10 numerical digits
        attachTenDigitFilter(victimPhoneInput);

        victimAddressInput = createCompactField();

        grid.add(wrapField("Victim Full Name *", victimNameInput));
        grid.add(wrapField("Date of Birth (DD/MM/YYYY)", victimDobInput));
        grid.add(wrapField("Age (Calculated Automatically)", victimAgeInput));
        grid.add(wrapField("Gender", victimGenderInput));
        grid.add(wrapField("Contact Phone (Max 10 Digits)", victimPhoneInput));
        grid.add(wrapField("Residential Address", victimAddressInput));

        inputCard.add(grid, BorderLayout.CENTER);

        JButton stageBtn = new JButton("+ Stage Victim to Roster");
        stageBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        stageBtn.setBackground(new Color(16, 185, 129));
        stageBtn.setForeground(Color.WHITE);
        stageBtn.setFocusPainted(false);
        stageBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        stageBtn.setBorder(new EmptyBorder(7, 18, 7, 18));
        stageBtn.addActionListener(e -> stageVictimInput());

        JPanel stageBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        stageBar.setOpaque(false);
        stageBar.setBorder(new EmptyBorder(6, 0, 0, 0));
        stageBar.add(stageBtn);
        inputCard.add(stageBar, BorderLayout.SOUTH);

        centerSplit.add(inputCard, BorderLayout.CENTER);
        card.add(centerSplit, BorderLayout.CENTER);

        // Bottom Actions Row (Back + Final Registration)
        JPanel bottomActions = new JPanel(new BorderLayout());
        bottomActions.setOpaque(false);
        bottomActions.setBorder(new EmptyBorder(8, 0, 0, 0));

        JButton backBtn = new JButton("← Back to Case Details");
        backBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        backBtn.setBackground(new Color(30, 41, 59));
        backBtn.setForeground(Color.WHITE);
        backBtn.setFocusPainted(false);
        backBtn.setBorder(new EmptyBorder(8, 16, 8, 16));
        backBtn.addActionListener(e -> switchInnerTab("CASE_DETAILS"));

        JButton registerFinalBtn = new JButton("REGISTER CASE & DISPATCH");
        registerFinalBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        registerFinalBtn.setBackground(UITheme.ACCENT_CYAN);
        registerFinalBtn.setForeground(UITheme.BG_DARK);
        registerFinalBtn.setFocusPainted(false);
        registerFinalBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        registerFinalBtn.setBorder(new EmptyBorder(9, 28, 9, 28));
        registerFinalBtn.addActionListener(e -> initiateRegistrationWorkflow());

        bottomActions.add(backBtn, BorderLayout.WEST);
        bottomActions.add(registerFinalBtn, BorderLayout.EAST);
        card.add(bottomActions, BorderLayout.SOUTH);

        return card;
    }

    private void stageVictimInput() {
        String name = victimNameInput.getText().trim();
        String dob = victimDobInput.getText().trim();
        String age = victimAgeInput.getText().trim();
        String gender = (String) victimGenderInput.getSelectedItem();
        String phone = victimPhoneInput.getText().trim();
        String address = victimAddressInput.getText().trim();

        if (name.isEmpty()) {
            GlassToastModal.showToast(
                    (JFrame) SwingUtilities.getWindowAncestor(this),
                    "NAME REQUIRED",
                    "Please enter the Victim's Full Name before staging.",
                    false
            );
            return;
        }

        String sqlDob = null;
        if (!dob.isEmpty()) {
            sqlDob = convertDateToSql(dob);
            if (sqlDob == null) {
                GlassToastModal.showToast(
                        (JFrame) SwingUtilities.getWindowAncestor(this),
                        "INVALID DATE",
                        "Please use valid DD/MM/YYYY format for Date of Birth.",
                        false
                );
                return;
            }
        }

        VictimProfileData vData = new VictimProfileData(name, age, sqlDob != null ? sqlDob : "", gender, phone, address);
        // Insert at the top of the list
        stagedVictims.add(0, vData);

        // Insert at row 0 in table
        victimTableModel.insertRow(0, new Object[]{
                1, name, gender, age.isEmpty() ? "--" : age, dob.isEmpty() ? "--" : dob, phone.isEmpty() ? "--" : phone, address.isEmpty() ? "--" : address
        });

        // Re-index column 0
        for (int i = 0; i < victimTableModel.getRowCount(); i++) {
            victimTableModel.setValueAt(i + 1, i, 0);
        }

        updateVictimBadge();

        // Clear input form for next victim
        victimNameInput.setText("");
        victimDobInput.setText("");
        victimAgeInput.setText("");
        victimPhoneInput.setText("");
        victimAddressInput.setText("");
    }

    private void removeSelectedVictimFromTable() {
        int sel = victimTable.getSelectedRow();
        if (sel == -1) {
            GlassToastModal.showToast(
                    (JFrame) SwingUtilities.getWindowAncestor(this),
                    "SELECTION REQUIRED",
                    "Select a victim row from the table to remove.",
                    false
            );
            return;
        }

        stagedVictims.remove(sel);
        victimTableModel.removeRow(sel);

        for (int i = 0; i < victimTableModel.getRowCount(); i++) {
            victimTableModel.setValueAt(i + 1, i, 0);
        }

        updateVictimBadge();
    }

    private void updateVictimBadge() {
        int count = stagedVictims.size();
        victimTabBtn.setText("2. Victim Directory (" + count + ")");
        if (victimCountBadge != null) {
            victimCountBadge.setText(count + (count == 1 ? " Record" : " Records"));
        }
    }

    private void initiateRegistrationWorkflow() {
        String caseId = caseIdField.getText().trim();
        String title = caseTitleField.getText().trim();
        String selectedCrimeType = crimeTypeCombo.getSelectedItem();
        String selectedLevel = caseLevelCombo.getSelectedItem();
        String loc = locationField.getText().trim();
        String dateRaw = dateReportedField.getText().trim();
        String desc = caseDescArea.getText().trim();
        String report = initialReportArea.getText().trim();

        List<String> missingFields = new ArrayList<>();
        if (title.isEmpty()) missingFields.add("• Case Title");
        if (selectedCrimeType == null || selectedCrimeType.equals(DEFAULT_CRIME_TYPE_PROMPT)) missingFields.add("• Case Type");
        if (selectedLevel == null || selectedLevel.equals(DEFAULT_CASE_LEVEL_PROMPT)) missingFields.add("• Case Urgency / Level");
        if (loc.isEmpty()) missingFields.add("• Location / Jurisdiction");
        if (desc.isEmpty()) missingFields.add("• Incident Description");
        if (report.isEmpty()) missingFields.add("• Initial Directives");

        // Convert DD/MM/YYYY to SQL YYYY-MM-DD
        String dateSql = convertDateToSql(dateRaw);
        if (dateSql == null) {
            missingFields.add("• Date Reported: Use valid DD/MM/YYYY format");
        }

        // If victim input fields currently have text, prompt or auto-stage
        String pendingName = victimNameInput.getText().trim();
        if (!pendingName.isEmpty() && stagedVictims.isEmpty()) {
            stageVictimInput();
        }

        if (stagedVictims.isEmpty()) {
            missingFields.add("• At least one victim must be staged in the Victim Directory");
        }

        if (!missingFields.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Please provide the required details:\n");
            for (String field : missingFields) {
                errorMsg.append(field).append("\n");
            }
            GlassToastModal.showToast(
                    (JFrame) SwingUtilities.getWindowAncestor(this),
                    "INCOMPLETE INFORMATION",
                    errorMsg.toString().trim(),
                    false
            );
            return;
        }

        // Build composite string for legacy cases.victims column
        StringBuilder legacyVictimsSummary = new StringBuilder();
        for (int i = 0; i < stagedVictims.size(); i++) {
            VictimProfileData v = stagedVictims.get(i);
            legacyVictimsSummary.append(v.fullName)
                    .append(" (").append(v.gender).append(", Age: ").append(v.age.isEmpty() ? "--" : v.age).append(")")
                    .append(i < stagedVictims.size() - 1 ? "; " : "");
        }

        CaseFormData data = new CaseFormData(
                caseId, title, selectedCrimeType, loc, dateSql, selectedLevel,
                legacyVictimsSummary.toString(), desc, report, adminName, new ArrayList<>(stagedVictims)
        );

        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        AssignCaseModal.showModal(parentFrame, data, () -> {
            resetForm();
            if (onCaseCreatedCallback != null) {
                onCaseCreatedCallback.run();
            }
        });
    }

    public void resetForm() {
        if (caseTitleField != null) caseTitleField.setText("");
        if (crimeTypeCombo != null) crimeTypeCombo.setSelectedItem(DEFAULT_CRIME_TYPE_PROMPT);
        if (caseLevelCombo != null) caseLevelCombo.setSelectedItem(DEFAULT_CASE_LEVEL_PROMPT);
        if (locationField != null) locationField.setText("");
        if (caseDescArea != null) caseDescArea.setText("");
        if (initialReportArea != null) initialReportArea.setText("");
        if (dateReportedField != null) dateReportedField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        stagedVictims.clear();
        if (victimTableModel != null) {
            victimTableModel.setRowCount(0);
        }
        if (victimNameInput != null) victimNameInput.setText("");
        if (victimDobInput != null) victimDobInput.setText("");
        if (victimAgeInput != null) victimAgeInput.setText("");
        if (victimPhoneInput != null) victimPhoneInput.setText("");
        if (victimAddressInput != null) victimAddressInput.setText("");

        updateVictimBadge();
        switchInnerTab("CASE_DETAILS");
        generateAutoCaseId();
    }

    private void addFormField(JPanel panel, GridBagConstraints gbc, int gridx, int gridy, int gridwidth, String labelText, JComponent comp) {
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.gridwidth = gridwidth;
        gbc.weightx = (gridwidth == 2) ? 1.0 : 0.5;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel fieldPanel = new JPanel(new BorderLayout(0, 3));
        fieldPanel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(UITheme.TEXT_MUTED);

        fieldPanel.add(label, BorderLayout.NORTH);
        fieldPanel.add(comp, BorderLayout.CENTER);

        panel.add(fieldPanel, gbc);
    }

    private JTextField createCompactField() {
        JTextField tf = new JTextField();
        tf.setOpaque(true);
        tf.setBackground(new Color(25, 33, 48));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(UITheme.ACCENT_CYAN);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tf.setPreferredSize(new Dimension(200, 30));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248, 70), 1),
                new EmptyBorder(3, 8, 3, 8)
        ));
        return tf;
    }

    private JTextArea createStyledTextArea() {
        JTextArea ta = new JTextArea(4, 20);
        ta.setBackground(new Color(25, 33, 48));
        ta.setForeground(Color.WHITE);
        ta.setCaretColor(UITheme.ACCENT_CYAN);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(6, 8, 6, 8));
        return ta;
    }

    private JScrollPane createStyledScrollPane(JTextArea ta) {
        JScrollPane sp = new JScrollPane(ta);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setPreferredSize(new Dimension(240, 85));
        sp.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 70), 1));
        return sp;
    }

    private void generateAutoCaseId() {
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM cases";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int nextNum = rs.getInt(1);
                        return String.format("CR-2026-%05d", nextNum);
                    }
                } catch (Exception ex) {
                    System.err.println("Case ID Query: " + ex.getMessage());
                }
                return "CR-2026-00001";
            }

            @Override
            protected void done() {
                try {
                    caseIdField.setText(get());
                } catch (Exception e) {
                    caseIdField.setText("CR-2026-00001");
                }
            }
        };
        worker.execute();
    }

    // =========================================================================
    // AUTOMATIC DD/MM/YYYY FORMATTER & LIVE AGE CALCULATOR
    // =========================================================================
    private static void attachAutoDateFormatter(JTextField dateField, JTextField ageFieldToUpdate) {
        dateField.getDocument().addDocumentListener(new DocumentListener() {
            private boolean isUpdating = false;

            @Override public void insertUpdate(DocumentEvent e) { format(); }
            @Override public void removeUpdate(DocumentEvent e) { }
            @Override public void changedUpdate(DocumentEvent e) { format(); }

            private void format() {
                if (isUpdating) return;
                SwingUtilities.invokeLater(() -> {
                    isUpdating = true;
                    try {
                        String text = dateField.getText().replaceAll("[^0-9]", "");
                        if (text.length() > 8) text = text.substring(0, 8);

                        StringBuilder formatted = new StringBuilder();
                        for (int i = 0; i < text.length(); i++) {
                            if (i == 2 || i == 4) formatted.append("/");
                            formatted.append(text.charAt(i));
                        }

                        dateField.setText(formatted.toString());

                        if (text.length() == 8 && ageFieldToUpdate != null) {
                            try {
                                int day = Integer.parseInt(text.substring(0, 2));
                                int month = Integer.parseInt(text.substring(2, 4));
                                int year = Integer.parseInt(text.substring(4, 8));
                                LocalDate dob = LocalDate.of(year, month, day);
                                int age = Period.between(dob, LocalDate.now()).getYears();
                                if (age >= 0 && age <= 130) {
                                    ageFieldToUpdate.setText(String.valueOf(age));
                                }
                            } catch (Exception ignored) {}
                        }
                    } finally {
                        isUpdating = false;
                    }
                });
            }
        });
    }

    // Caps phone inputs strictly to 10 numerical digits
    private static void attachTenDigitFilter(JTextField phoneField) {
        ((AbstractDocument) phoneField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                String filtered = string.replaceAll("[^0-9]", "");
                if ((fb.getDocument().getLength() + filtered.length()) <= 10) {
                    super.insertString(fb, offset, filtered, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) return;
                String filtered = text.replaceAll("[^0-9]", "");
                int currentLength = fb.getDocument().getLength();
                int newLength = currentLength - length + filtered.length();
                if (newLength <= 10) {
                    super.replace(fb, offset, length, filtered, attrs);
                }
            }
        });
    }

    private static String convertDateToSql(String ddMMyyyy) {
        try {
            DateTimeFormatter inFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter sqlFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(ddMMyyyy.trim(), inFmt).format(sqlFmt);
        } catch (Exception e) {
            try {
                LocalDate.parse(ddMMyyyy.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return ddMMyyyy.trim();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static JPanel wrapField(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(new Color(148, 163, 184));
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    public static class VictimProfileData {
        String fullName;
        String age;
        String dob;
        String gender;
        String phone;
        String address;

        public VictimProfileData(String fullName, String age, String dob, String gender, String phone, String address) {
            this.fullName = fullName;
            this.age = age;
            this.dob = dob;
            this.gender = gender;
            this.phone = phone;
            this.address = address;
        }
    }

    private static class CaseFormData {
        String caseId, title, crimeType, loc, date, priority, victimSummary, desc, report, admin;
        List<VictimProfileData> victims;

        CaseFormData(String caseId, String title, String crimeType, String loc, String date, String priority,
                     String victimSummary, String desc, String report, String admin, List<VictimProfileData> victims) {
            this.caseId = caseId;
            this.title = title;
            this.crimeType = crimeType;
            this.loc = loc;
            this.date = date;
            this.priority = priority;
            this.victimSummary = victimSummary;
            this.desc = desc;
            this.report = report;
            this.admin = admin;
            this.victims = victims;
        }
    }

    private static class AssignCaseModal extends JDialog {

        private final int BASE_HEIGHT = 220;
        private final int EXPANDED_HEIGHT = 310;
        private Timer expansionTimer;
        private float expansionProgress = 0f;

        private JComboBox<String> routeCombo;
        private JComboBox<OfficerItem> investigatorCombo;
        private JPanel investigatorSection;
        private JButton confirmBtn;

        private static class OfficerItem {
            String username;
            String fullName;
            String badge;

            OfficerItem(String username, String fullName, String badge) {
                this.username = username;
                this.fullName = fullName;
                this.badge = badge;
            }

            @Override
            public String toString() {
                if (username.isEmpty()) return fullName;
                return fullName + " [" + username + "] (" + badge + ")";
            }
        }

        public static void showModal(JFrame parent, CaseFormData data, Runnable onComplete) {
            AssignCaseModal modal = new AssignCaseModal(parent, data, onComplete);
            modal.setVisible(true);
        }

        private AssignCaseModal(JFrame parent, CaseFormData data, Runnable onComplete) {
            super(parent, true);
            setUndecorated(true);
            setSize(460, BASE_HEIGHT);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            JPanel card = new JPanel(new BorderLayout(0, 10)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 252));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(UITheme.ACCENT_CYAN);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                    g2.dispose();
                }
            };
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(18, 22, 18, 22));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);

            JLabel titleLbl = new JLabel("ASSIGN CASE DOSSIER");
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            titleLbl.setForeground(Color.WHITE);

            JLabel caseBadge = new JLabel(data.caseId);
            caseBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
            caseBadge.setForeground(UITheme.ACCENT_CYAN);

            header.add(titleLbl, BorderLayout.WEST);
            header.add(caseBadge, BorderLayout.EAST);
            card.add(header, BorderLayout.NORTH);

            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(false);

            JLabel infoLbl = new JLabel("Select routing target:");
            infoLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            infoLbl.setForeground(new Color(203, 213, 225));
            infoLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            routeCombo = new JComboBox<>(new String[]{"Unassigned (Public Directory)", "Assign to Investigator"});
            routeCombo.setBackground(new Color(25, 33, 48));
            routeCombo.setForeground(Color.WHITE);
            routeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            routeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            routeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            routeCombo.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 80), 1));

            investigatorSection = new JPanel() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(410, (int) (80 * expansionProgress));
                }

                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, (int) (80 * expansionProgress));
                }
            };
            investigatorSection.setLayout(new BorderLayout(0, 6));
            investigatorSection.setOpaque(false);
            investigatorSection.setAlignmentX(Component.LEFT_ALIGNMENT);
            investigatorSection.setVisible(false);

            JPanel invInner = new JPanel(new GridLayout(2, 1, 0, 4));
            invInner.setOpaque(false);
            invInner.setBorder(new EmptyBorder(8, 0, 0, 0));

            JLabel invLabel = new JLabel("Assign To Approved Investigator *");
            invLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            invLabel.setForeground(new Color(52, 211, 153));

            investigatorCombo = new JComboBox<>();
            investigatorCombo.setBackground(new Color(20, 29, 44));
            investigatorCombo.setForeground(Color.WHITE);
            investigatorCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            investigatorCombo.setBorder(BorderFactory.createLineBorder(new Color(52, 211, 153, 120), 1));
            loadActiveInvestigators(investigatorCombo);

            invInner.add(invLabel);
            invInner.add(investigatorCombo);
            investigatorSection.add(invInner, BorderLayout.CENTER);

            routeCombo.addActionListener(e -> {
                boolean isInvestigator = routeCombo.getSelectedIndex() == 1;
                triggerDropdownSlideAnimation(isInvestigator);
            });

            body.add(infoLbl);
            body.add(Box.createVerticalStrut(6));
            body.add(routeCombo);
            body.add(investigatorSection);

            card.add(body, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            actions.setOpaque(false);

            JButton cancelBtn = new JButton("CANCEL");
            cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            cancelBtn.setBackground(new Color(30, 41, 59));
            cancelBtn.setForeground(Color.WHITE);
            cancelBtn.setFocusPainted(false);
            cancelBtn.setBorder(new EmptyBorder(8, 14, 8, 14));
            cancelBtn.addActionListener(e -> dispose());

            confirmBtn = new JButton("CONFIRM & SAVE");
            confirmBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            confirmBtn.setBackground(UITheme.ACCENT_CYAN);
            confirmBtn.setForeground(UITheme.BG_DARK);
            confirmBtn.setFocusPainted(false);
            confirmBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            confirmBtn.setBorder(new EmptyBorder(8, 20, 8, 20));

            confirmBtn.addActionListener(e -> {
                boolean isInvestigator = routeCombo.getSelectedIndex() == 1;
                OfficerItem selectedItem = isInvestigator ? (OfficerItem) investigatorCombo.getSelectedItem() : null;

                String finalStatus = (isInvestigator && selectedItem != null && !selectedItem.username.isEmpty()) ? "ACTIVE" : "PENDING";
                String selectedOfficerUsername = (isInvestigator && selectedItem != null && !selectedItem.username.isEmpty()) ? selectedItem.username : "Unassigned";

                executeSafeAtomicInsert(data, finalStatus, selectedOfficerUsername, parent, onComplete);
                dispose();
            });

            actions.add(cancelBtn);
            actions.add(confirmBtn);
            card.add(actions, BorderLayout.SOUTH);

            setContentPane(card);
        }

        private void triggerDropdownSlideAnimation(boolean expand) {
            if (expansionTimer != null && expansionTimer.isRunning()) {
                expansionTimer.stop();
            }

            if (expand) investigatorSection.setVisible(true);

            long startTime = System.currentTimeMillis();
            final int duration = 200;
            final float startProgress = expansionProgress;
            final float targetProgress = expand ? 1.0f : 0.0f;
            final int startH = getHeight();
            final int targetH = expand ? EXPANDED_HEIGHT : BASE_HEIGHT;

            expansionTimer = new Timer(15, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(1.0f, (float) elapsed / duration);
                float eased = 1.0f - (float) Math.pow(1.0f - t, 3);

                expansionProgress = startProgress + (targetProgress - startProgress) * eased;
                int currentH = (int) (startH + (targetH - startH) * eased);

                setSize(getWidth(), currentH);
                investigatorSection.revalidate();
                revalidate();
                repaint();

                if (t >= 1.0f) {
                    ((Timer) e.getSource()).stop();
                    expansionProgress = targetProgress;
                    if (!expand) investigatorSection.setVisible(false);
                    confirmBtn.setBackground(expand ? new Color(52, 211, 153) : UITheme.ACCENT_CYAN);
                }
            });
            expansionTimer.start();
        }

        private void loadActiveInvestigators(JComboBox<OfficerItem> combo) {
            SwingWorker<List<OfficerItem>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<OfficerItem> doInBackground() {
                    List<OfficerItem> list = new ArrayList<>();
                    String sql = "SELECT username, full_name, id_number FROM users " +
                            "WHERE role = 'INVESTIGATOR' AND status = 'ACTIVE' " +
                            "ORDER BY full_name ASC";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String u = rs.getString("username");
                            String fn = rs.getString("full_name");
                            String id = rs.getString("id_number");
                            list.add(new OfficerItem(u, (fn != null && !fn.isEmpty()) ? fn : u, (id != null) ? id : "BADGE-FIELD"));
                        }
                    } catch (Exception ignored) {}
                    return list;
                }

                @Override
                protected void done() {
                    try {
                        List<OfficerItem> results = get();
                        combo.removeAllItems();
                        if (results.isEmpty()) {
                            combo.addItem(new OfficerItem("", "-- No Approved Investigators Available --", ""));
                        } else {
                            for (OfficerItem item : results) {
                                combo.addItem(item);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            };
            worker.execute();
        }

        private void executeSafeAtomicInsert(CaseFormData d, String status, String officerUsername, JFrame parent, Runnable onComplete) {
            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override
                protected Boolean doInBackground() {
                    try (Connection conn = DBConnection.getConnection()) {
                        conn.setAutoCommit(false);

                        // 1. Insert Master Case Docket
                        String insertSql = "INSERT INTO cases " +
                                "(case_number, title, category, crime_type, location_jurisdiction, date_reported, priority, victims, incident_description, investigation_notes, status, assigned_officer, created_by, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'admin', NOW(), NOW())";

                        try (PreparedStatement psCase = conn.prepareStatement(insertSql)) {
                            psCase.setString(1, d.caseId);
                            psCase.setString(2, d.title);
                            psCase.setString(3, d.crimeType);
                            psCase.setString(4, d.crimeType);
                            psCase.setString(5, d.loc);
                            psCase.setDate(6, java.sql.Date.valueOf(d.date));
                            psCase.setString(7, d.priority);
                            psCase.setString(8, d.victimSummary);
                            psCase.setString(9, d.desc);
                            psCase.setString(10, d.report);
                            psCase.setString(11, status);
                            psCase.setString(12, officerUsername);
                            psCase.executeUpdate();
                        }

                        // 2. Batch Insert Individual Victim Profiles
                        String insertVictimSql = "INSERT INTO case_victims (case_number, full_name, age, dob, gender, phone, address, created_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";

                        try (PreparedStatement psVictim = conn.prepareStatement(insertVictimSql)) {
                            for (VictimProfileData vp : d.victims) {
                                psVictim.setString(1, d.caseId);
                                psVictim.setString(2, vp.fullName);

                                Integer ageVal = null;
                                try { ageVal = Integer.parseInt(vp.age); } catch (Exception ignored) {}
                                if (ageVal != null) psVictim.setInt(3, ageVal); else psVictim.setNull(3, java.sql.Types.INTEGER);

                                if (vp.dob != null && !vp.dob.isEmpty()) {
                                    try {
                                        psVictim.setDate(4, java.sql.Date.valueOf(vp.dob));
                                    } catch (Exception ignored) {
                                        psVictim.setNull(4, java.sql.Types.DATE);
                                    }
                                } else {
                                    psVictim.setNull(4, java.sql.Types.DATE);
                                }

                                psVictim.setString(5, vp.gender);
                                psVictim.setString(6, vp.phone);
                                psVictim.setString(7, vp.address);
                                psVictim.addBatch();
                            }
                            psVictim.executeBatch();
                        }

                        conn.commit();

                        String logText = "ACTIVE".equalsIgnoreCase(status)
                                ? "CASE DIRECT DISPATCH: Case [" + d.caseId + "] dispatched to [" + officerUsername + "] with " + d.victims.size() + " victim(s)."
                                : "CASE POOL REGISTRATION: Case [" + d.caseId + "] registered as PENDING in Public Pool with " + d.victims.size() + " victim(s).";
                        AuditLogger.log(d.admin, logText);
                        return true;

                    } catch (Exception ex) {
                        System.err.println("Insert case & victims error: " + ex.getMessage());
                    }
                    return false;
                }

                @Override
                protected void done() {
                    try {
                        boolean ok = get();
                        if (ok) {
                            String message = "ACTIVE".equalsIgnoreCase(status)
                                    ? "Case [" + d.caseId + "] registered (ACTIVE) and dispatched directly to " + officerUsername + "."
                                    : "Case [" + d.caseId + "] added to Available Pool (PENDING) awaiting investigator claim.";
                            GlassToastModal.showToast(parent, "CASE REGISTERED", message, true);
                            if (onComplete != null) {
                                SwingUtilities.invokeLater(onComplete);
                            }
                        } else {
                            GlassToastModal.showToast(parent, "DATABASE ERROR", "Failed to commit case to database.", false);
                        }
                    } catch (Exception ignored) {}
                }
            };
            worker.execute();
        }
    }

    public static class AnimatedGlassComboBox extends JPanel {
        private final String[] items;
        private String selectedItem;
        private final JLabel displayLabel;
        private JPopupMenu popupMenu;
        private boolean isHovered = false;

        public AnimatedGlassComboBox(String[] items) {
            this.items = items;
            this.selectedItem = items.length > 0 ? items[0] : "";

            setOpaque(false);
            setPreferredSize(new Dimension(200, 30));
            setLayout(new BorderLayout());
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            displayLabel = new JLabel(selectedItem);
            displayLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            displayLabel.setForeground(Color.WHITE);
            displayLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

            JLabel arrow = new JLabel("▼ ");
            arrow.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            arrow.setForeground(UITheme.ACCENT_CYAN);

            add(displayLabel, BorderLayout.CENTER);
            add(arrow, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { showAnimatedPopup(); }
            });
        }

        public String getSelectedItem() {
            return selectedItem;
        }

        public void setSelectedItem(String item) {
            this.selectedItem = item;
            displayLabel.setText(item);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(25, 33, 48));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

            g2.setColor(isHovered ? UITheme.ACCENT_CYAN : new Color(56, 189, 248, 70));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

            g2.dispose();
        }

        private void showAnimatedPopup() {
            popupMenu = new JPopupMenu() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 252));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(UITheme.ACCENT_CYAN);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                }
            };
            popupMenu.setBorder(new EmptyBorder(4, 4, 4, 4));
            popupMenu.setOpaque(false);

            for (String item : items) {
                JMenuItem mi = new JMenuItem(item);
                mi.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                mi.setForeground(Color.WHITE);
                mi.setOpaque(false);
                mi.setBorder(new EmptyBorder(6, 12, 6, 12));
                mi.setCursor(new Cursor(Cursor.HAND_CURSOR));

                mi.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        mi.setOpaque(true);
                        mi.setBackground(new Color(56, 189, 248, 45));
                        mi.setForeground(UITheme.ACCENT_CYAN);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        mi.setOpaque(false);
                        mi.setForeground(Color.WHITE);
                    }
                });

                mi.addActionListener(e -> {
                    selectedItem = item;
                    displayLabel.setText(item);
                    repaint();
                });

                popupMenu.add(mi);
            }

            popupMenu.show(this, 0, getHeight() + 2);
        }
    }

    private static class GlassToastModal extends JDialog {

        public static void showToast(JFrame parent, String title, String message, boolean isSuccess) {
            GlassToastModal modal = new GlassToastModal(parent, title, message, isSuccess);
            modal.setVisible(true);
        }

        private GlassToastModal(JFrame parent, String title, String message, boolean isSuccess) {
            super(parent, true);
            setUndecorated(true);
            setSize(380, 200);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            Color accentColor = isSuccess ? new Color(52, 211, 153) : new Color(248, 113, 113);

            JPanel card = new JPanel(new BorderLayout(0, 10)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 252));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(accentColor);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                    g2.dispose();
                }
            };
            card.setBorder(new EmptyBorder(14, 18, 14, 18));

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            titleLbl.setForeground(accentColor);
            card.add(titleLbl, BorderLayout.NORTH);

            JTextArea msgArea = new JTextArea(message);
            msgArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            msgArea.setForeground(new Color(226, 232, 240));
            msgArea.setOpaque(false);
            msgArea.setEditable(false);
            msgArea.setLineWrap(true);
            msgArea.setWrapStyleWord(true);
            card.add(msgArea, BorderLayout.CENTER);

            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actionPanel.setOpaque(false);

            JButton ackBtn = new JButton("DISMISS");
            ackBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            ackBtn.setBackground(new Color(30, 41, 59));
            ackBtn.setForeground(Color.WHITE);
            ackBtn.setFocusPainted(false);
            ackBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            ackBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1, true),
                    new EmptyBorder(6, 14, 6, 14)
            ));
            ackBtn.addActionListener(e -> dispose());
            actionPanel.add(ackBtn);

            card.add(actionPanel, BorderLayout.SOUTH);

            setContentPane(card);
        }
    }
}