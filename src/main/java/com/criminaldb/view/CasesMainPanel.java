package com.criminaldb.view;

import com.criminaldb.config.DBConnection;
import com.criminaldb.util.AuditLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

public class CasesMainPanel extends JPanel {

    private final String adminName;
    private final Runnable onDataUpdatedGlobalCallback;

    private AnimatedCardContainer tabAnimatedContainer;

    private TabNavButton allCasesBtn;
    private TabNavButton createCaseBtn;
    private TabNavButton availableCasesBtn;

    // View Components
    private AllCasesView allCasesView;
    private CreateCasePanel createCasePanel;
    private AvailableCasesView availableCasesView;

    public CasesMainPanel(String adminName, Runnable onDataUpdatedGlobalCallback) {
        this.adminName = adminName != null ? adminName : "Head Administrator";
        this.onDataUpdatedGlobalCallback = onDataUpdatedGlobalCallback;

        setOpaque(false);
        setLayout(new BorderLayout());

        initUI();
    }

    private void initUI() {
        JPanel topToolbar = new JPanel(new BorderLayout());
        topToolbar.setOpaque(false);
        topToolbar.setBorder(new EmptyBorder(8, 20, 4, 20));

        JPanel buttonCluster = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonCluster.setOpaque(false);

        allCasesBtn = new TabNavButton("All Cases", true);
        createCaseBtn = new TabNavButton("Create Case", false);
        availableCasesBtn = new TabNavButton("Available Cases", false);

        allCasesBtn.addActionListener(e -> switchTab("ALL_CASES"));
        createCaseBtn.addActionListener(e -> switchTab("CREATE_CASE"));
        availableCasesBtn.addActionListener(e -> switchTab("AVAILABLE_CASES"));

        buttonCluster.add(allCasesBtn);
        buttonCluster.add(createCaseBtn);
        buttonCluster.add(availableCasesBtn);

        topToolbar.add(buttonCluster, BorderLayout.WEST);
        add(topToolbar, BorderLayout.NORTH);

        allCasesView = new AllCasesView(adminName, this::refreshAllTabs);
        createCasePanel = new CreateCasePanel(adminName, () -> {
            refreshAllTabs();
            switchTab("ALL_CASES");
        });
        availableCasesView = new AvailableCasesView(adminName, this::refreshAllTabs);

        tabAnimatedContainer = new AnimatedCardContainer();
        tabAnimatedContainer.addTab("ALL_CASES", allCasesView);
        tabAnimatedContainer.addTab("CREATE_CASE", createCasePanel);
        tabAnimatedContainer.addTab("AVAILABLE_CASES", availableCasesView);
        tabAnimatedContainer.showTabImmediately("ALL_CASES");

        add(tabAnimatedContainer, BorderLayout.CENTER);
    }

    private void switchTab(String tabName) {
        allCasesBtn.setSelected("ALL_CASES".equals(tabName));
        createCaseBtn.setSelected("CREATE_CASE".equals(tabName));
        availableCasesBtn.setSelected("AVAILABLE_CASES".equals(tabName));

        if ("ALL_CASES".equals(tabName)) {
            allCasesView.resetViewToNormal();
        } else if ("CREATE_CASE".equals(tabName)) {
            createCasePanel.resetForm();
        } else if ("AVAILABLE_CASES".equals(tabName)) {
            availableCasesView.resetViewToNormal();
        }

        tabAnimatedContainer.switchTo(tabName);
    }

    public void refreshAllTabs() {
        if (allCasesView != null) allCasesView.loadCases();
        if (availableCasesView != null) availableCasesView.loadAvailableCases();
        if (onDataUpdatedGlobalCallback != null) {
            SwingUtilities.invokeLater(onDataUpdatedGlobalCallback);
        }
    }

    private static class AnimatedCardContainer extends JPanel {
        private final java.util.Map<String, JComponent> views = new java.util.LinkedHashMap<>();
        private String currentKey = null;

        private BufferedImage currentSnapshot = null;
        private BufferedImage nextSnapshot = null;
        private boolean isAnimating = false;
        private float transitionProgress = 0f;
        private Timer animTimer;

        public AnimatedCardContainer() {
            setLayout(new BorderLayout());
            setOpaque(false);
        }

        public void addTab(String key, JComponent comp) {
            views.put(key, comp);
        }

        public void showTabImmediately(String key) {
            this.currentKey = key;
            removeAll();
            add(views.get(key), BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        public void switchTo(String nextKey) {
            if (nextKey == null || nextKey.equals(currentKey) || isAnimating) return;

            JComponent currentComp = views.get(currentKey);
            JComponent nextComp = views.get(nextKey);
            if (currentComp == null || nextComp == null) return;

            int w = getWidth();
            int h = getHeight();

            if (w <= 0 || h <= 0) {
                showTabImmediately(nextKey);
                return;
            }

            currentSnapshot = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g1 = currentSnapshot.createGraphics();
            currentComp.paint(g1);
            g1.dispose();

            nextComp.setSize(w, h);
            nextComp.doLayout();
            nextSnapshot = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = nextSnapshot.createGraphics();
            nextComp.paint(g2);
            g2.dispose();

            removeAll();
            isAnimating = true;
            transitionProgress = 0f;

            long start = System.currentTimeMillis();
            final int duration = 200;

            animTimer = new Timer(15, e -> {
                long elapsed = System.currentTimeMillis() - start;
                float t = Math.min(1.0f, (float) elapsed / duration);
                transitionProgress = 1.0f - (float) Math.pow(1.0f - t, 3);
                repaint();

                if (t >= 1.0f) {
                    animTimer.stop();
                    isAnimating = false;
                    currentKey = nextKey;
                    removeAll();
                    add(nextComp, BorderLayout.CENTER);
                    revalidate();
                    repaint();
                }
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!isAnimating) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth();
            int h = getHeight();

            if (currentSnapshot != null) {
                float alphaOld = Math.max(0f, 1.0f - transitionProgress);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaOld));
                int slideY = (int) (transitionProgress * 14);
                g2.drawImage(currentSnapshot, 0, slideY, null);
            }

            if (nextSnapshot != null) {
                float alphaNew = Math.min(1.0f, transitionProgress);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaNew));
                int offsetY = (int) ((1.0f - transitionProgress) * -14);
                g2.drawImage(nextSnapshot, 0, offsetY, null);
            }

            int sweepY = (int) (transitionProgress * h);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(0.35f, (1.0f - transitionProgress))));
            g2.setColor(UITheme.ACCENT_CYAN);
            g2.drawLine(0, sweepY, w, sweepY);

            g2.dispose();
        }
    }

    private static class TabNavButton extends JButton {
        private boolean isSelected;
        private boolean isHovered = false;

        public TabNavButton(String text, boolean isSelected) {
            super(text);
            this.isSelected = isSelected;
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 20, 7, 20));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
            });
        }

        public void setSelected(boolean sel) {
            this.isSelected = sel;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isSelected) {
                g2.setColor(UITheme.ACCENT_CYAN);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                setForeground(UITheme.BG_DARK);
            } else {
                g2.setColor(isHovered ? new Color(56, 189, 248, 30) : new Color(25, 33, 48));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(56, 189, 248, isHovered ? 120 : 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                setForeground(isHovered ? Color.WHITE : new Color(203, 213, 225));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================================
    // TAB 1: ALL CASES VIEW (PROPER CLOSED, ACTIVE, PENDING BADGES & MATTE STYLING)
    // =========================================================================
    public static class AllCasesView extends JPanel {
        private final String adminName;
        private final Runnable onModifiedCallback;

        private DefaultTableModel tableModel;
        private JTable table;
        private TableRowSorter<DefaultTableModel> rowSorter;

        private JTextField searchField;
        private JLabel matchCounterLbl;
        private String currentStatusFilter = "ALL";
        private FilterChip[] filterChips;

        public AllCasesView(String adminName, Runnable onModifiedCallback) {
            this.adminName = adminName;
            this.onModifiedCallback = onModifiedCallback;
            setOpaque(false);
            setLayout(new BorderLayout(0, 6));
            setBorder(new EmptyBorder(2, 20, 10, 20));

            initUI();
            loadCases();
        }

        private void initUI() {
            UITheme.GlassCardPanel card = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
            card.setLayout(new BorderLayout(0, 8));
            card.setBorder(new EmptyBorder(10, 14, 10, 14));

            JPanel toolbar = new JPanel(new BorderLayout(12, 0));
            toolbar.setOpaque(false);

            JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            searchBox.setOpaque(false);

            JLabel searchIcon = new JLabel("SEARCH");
            searchIcon.setFont(new Font("Segoe UI", Font.BOLD, 10));
            searchIcon.setForeground(UITheme.ACCENT_CYAN);

            searchField = new JTextField();
            searchField.setPreferredSize(new Dimension(210, 28));
            searchField.setBackground(new Color(25, 33, 48));
            searchField.setForeground(Color.WHITE);
            searchField.setCaretColor(UITheme.ACCENT_CYAN);
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            searchField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(56, 189, 248, 80), 1, true),
                    new EmptyBorder(2, 8, 2, 8)
            ));

            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
                @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
                @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
            });

            searchBox.add(searchIcon);
            searchBox.add(searchField);

            JPanel filterChipsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            filterChipsPanel.setOpaque(false);

            FilterChip allChip = new FilterChip("ALL", true);
            FilterChip activeChip = new FilterChip("ACTIVE", false);
            FilterChip pendingChip = new FilterChip("PENDING", false);
            FilterChip closedChip = new FilterChip("CLOSED", false);

            filterChips = new FilterChip[]{allChip, activeChip, pendingChip, closedChip};
            for (FilterChip chip : filterChips) {
                chip.addActionListener(e -> {
                    for (FilterChip c : filterChips) c.setSelected(false);
                    chip.setSelected(true);
                    currentStatusFilter = chip.getText();
                    applyFilters();
                });
                filterChipsPanel.add(chip);
            }

            JPanel actionCluster = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actionCluster.setOpaque(false);

            matchCounterLbl = new JLabel("0 Cases");
            matchCounterLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            matchCounterLbl.setForeground(UITheme.TEXT_MUTED);

            VectorRefreshButton refreshBtn = new VectorRefreshButton();
            refreshBtn.addActionListener(e -> resetViewToNormal());

            JButton editBtn = new JButton("EDIT DOSSIER");
            editBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            editBtn.setBackground(UITheme.ACCENT_CYAN);
            editBtn.setForeground(UITheme.BG_DARK);
            editBtn.setFocusPainted(false);
            editBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            editBtn.setBorder(new EmptyBorder(6, 14, 6, 14));
            editBtn.addActionListener(e -> openEditDialogForSelected());

            actionCluster.add(matchCounterLbl);
            actionCluster.add(refreshBtn);
            actionCluster.add(editBtn);

            toolbar.add(searchBox, BorderLayout.WEST);
            toolbar.add(filterChipsPanel, BorderLayout.CENTER);
            toolbar.add(actionCluster, BorderLayout.EAST);
            card.add(toolbar, BorderLayout.NORTH);

            String[] columns = {"CASE ID", "CASE TITLE", "CATEGORY", "LOCATION", "STATUS", "PRIORITY", "DATE"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            table = new JTable(tableModel);
            table.setOpaque(false);
            table.setBackground(new Color(15, 23, 42));
            table.setForeground(Color.WHITE);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            table.setRowHeight(34);
            table.setShowGrid(false);
            table.setSelectionBackground(new Color(56, 189, 248, 45));
            table.setSelectionForeground(Color.WHITE);

            rowSorter = new TableRowSorter<>(tableModel);
            table.setRowSorter(rowSorter);

            table.getTableHeader().setOpaque(false);
            table.getTableHeader().setBackground(new Color(20, 27, 40));
            table.getTableHeader().setForeground(new Color(148, 163, 184));
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
            table.getTableHeader().setPreferredSize(new Dimension(0, 30));

            table.setDefaultRenderer(Object.class, new CleanRowRenderer());
            table.getColumnModel().getColumn(4).setCellRenderer(new BadgePillRenderer(true));
            table.getColumnModel().getColumn(5).setCellRenderer(new BadgePillRenderer(false));

            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) openEditDialogForSelected();
                }
            });

            JScrollPane sp = new JScrollPane(table);
            sp.getViewport().setBackground(new Color(15, 23, 42));
            sp.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 50), 1));
            card.add(sp, BorderLayout.CENTER);

            add(card, BorderLayout.CENTER);
        }

        public void resetViewToNormal() {
            if (searchField != null) searchField.setText("");
            if (table != null) table.clearSelection();
            currentStatusFilter = "ALL";
            if (filterChips != null) {
                for (FilterChip c : filterChips) {
                    c.setSelected("ALL".equalsIgnoreCase(c.getText()));
                }
            }
            loadCases();
        }

        private void applyFilters() {
            String query = searchField.getText().trim();
            RowFilter<DefaultTableModel, Object> textFilter = null;
            RowFilter<DefaultTableModel, Object> statusFilter = null;

            if (!query.isEmpty()) {
                textFilter = RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query), 0, 1, 3);
            }
            if (!"ALL".equalsIgnoreCase(currentStatusFilter)) {
                statusFilter = RowFilter.regexFilter("(?i)^" + currentStatusFilter + "$", 4);
            }

            List<RowFilter<DefaultTableModel, Object>> filters = new ArrayList<>();
            if (textFilter != null) filters.add(textFilter);
            if (statusFilter != null) filters.add(statusFilter);

            rowSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
            matchCounterLbl.setText(table.getRowCount() + " Cases");
        }

        public void loadCases() {
            SwingWorker<List<Object[]>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<Object[]> doInBackground() {
                    List<Object[]> rows = new ArrayList<>();
                    // Explicitly query all statuses including CLOSED
                    String sql = "SELECT case_number, title, category, location_jurisdiction, status, priority, date_reported " +
                            "FROM cases WHERE UPPER(TRIM(status)) != 'REJECTED' ORDER BY id DESC";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {

                        while (rs.next()) {
                            String caseNum = rs.getString("case_number");
                            String title = rs.getString("title");
                            String cat = rs.getString("category");
                            String loc = rs.getString("location_jurisdiction");
                            String st = rs.getString("status");
                            String prio = rs.getString("priority");
                            String dt = rs.getString("date_reported");

                            // Explicitly keep actual DB status: CLOSED, ACTIVE, PENDING, CLAIM_PENDING
                            String resolvedStatus = (st != null && !st.trim().isEmpty()) ? st.trim().toUpperCase() : "ACTIVE";

                            rows.add(new Object[]{
                                    caseNum != null ? caseNum : "--",
                                    title != null ? title : "--",
                                    cat != null ? cat : "--",
                                    loc != null ? loc : "--",
                                    resolvedStatus,
                                    prio != null ? prio : "HIGH",
                                    dt != null ? dt : "--"
                            });
                        }
                    } catch (Exception ex) {
                        System.err.println("Load all cases error: " + ex.getMessage());
                    }
                    return rows;
                }

                @Override
                protected void done() {
                    try {
                        tableModel.setRowCount(0);
                        for (Object[] r : get()) tableModel.addRow(r);
                        applyFilters();
                    } catch (Exception ignored) {}
                }
            };
            worker.execute();
        }

        private void openEditDialogForSelected() {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                GlassToastModal.showToast((JFrame) SwingUtilities.getWindowAncestor(this), "SELECTION REQUIRED", "Please select a case row from the table first.", false);
                return;
            }
            int modelRow = table.convertRowIndexToModel(selectedRow);
            String caseId = (String) tableModel.getValueAt(modelRow, 0);
            EditCaseModal.showModal((JFrame) SwingUtilities.getWindowAncestor(this), caseId, adminName, () -> {
                resetViewToNormal();
                if (onModifiedCallback != null) onModifiedCallback.run();
            });
        }
    }

    // =========================================================================
    // TAB 3: AVAILABLE CASES VIEW
    // =========================================================================
    public static class AvailableCasesView extends JPanel {
        private final String adminName;
        private final Runnable onModifiedCallback;

        private DefaultTableModel tableModel;
        private JTable table;
        private TableRowSorter<DefaultTableModel> rowSorter;

        private JTextField searchField;
        private JLabel queueCounterLbl;
        private String currentStatusFilter = "ALL";
        private FilterChip[] filterChips;

        public AvailableCasesView(String adminName, Runnable onModifiedCallback) {
            this.adminName = adminName;
            this.onModifiedCallback = onModifiedCallback;
            setOpaque(false);
            setLayout(new BorderLayout(0, 6));
            setBorder(new EmptyBorder(2, 20, 10, 20));

            initUI();
            loadAvailableCases();
        }

        private void initUI() {
            UITheme.GlassCardPanel card = new UITheme.GlassCardPanel(14, UITheme.CARD_BG);
            card.setLayout(new BorderLayout(0, 8));
            card.setBorder(new EmptyBorder(10, 14, 10, 14));

            JPanel toolbar = new JPanel(new BorderLayout(12, 0));
            toolbar.setOpaque(false);

            JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            searchBox.setOpaque(false);

            JLabel searchIcon = new JLabel("SEARCH");
            searchIcon.setFont(new Font("Segoe UI", Font.BOLD, 10));
            searchIcon.setForeground(UITheme.ACCENT_CYAN);

            searchField = new JTextField();
            searchField.setPreferredSize(new Dimension(190, 28));
            searchField.setBackground(new Color(25, 33, 48));
            searchField.setForeground(Color.WHITE);
            searchField.setCaretColor(UITheme.ACCENT_CYAN);
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            searchField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(56, 189, 248, 80), 1, true),
                    new EmptyBorder(2, 8, 2, 8)
            ));

            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
                @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
                @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
            });

            searchBox.add(searchIcon);
            searchBox.add(searchField);

            JPanel filterChipsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            filterChipsPanel.setOpaque(false);

            FilterChip allChip = new FilterChip("ALL", true);
            FilterChip activeChip = new FilterChip("ACTIVE", false);
            FilterChip pendingChip = new FilterChip("PENDING", false);

            filterChips = new FilterChip[]{allChip, activeChip, pendingChip};
            for (FilterChip chip : filterChips) {
                chip.addActionListener(e -> {
                    for (FilterChip c : filterChips) c.setSelected(false);
                    chip.setSelected(true);
                    currentStatusFilter = chip.getText();
                    applyFilters();
                });
                filterChipsPanel.add(chip);
            }

            JPanel actionCluster = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actionCluster.setOpaque(false);

            queueCounterLbl = new JLabel("0 Listed");
            queueCounterLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            queueCounterLbl.setForeground(UITheme.TEXT_MUTED);

            VectorRefreshButton refreshBtn = new VectorRefreshButton();
            refreshBtn.addActionListener(e -> resetViewToNormal());

            JButton assignBtn = new JButton("ASSIGN / REASSIGN →");
            assignBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            assignBtn.setBackground(new Color(52, 211, 153));
            assignBtn.setForeground(UITheme.BG_DARK);
            assignBtn.setFocusPainted(false);
            assignBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            assignBtn.setBorder(new EmptyBorder(6, 16, 6, 16));
            assignBtn.addActionListener(e -> openAssignDialogForSelected());

            actionCluster.add(queueCounterLbl);
            actionCluster.add(refreshBtn);
            actionCluster.add(assignBtn);

            toolbar.add(searchBox, BorderLayout.WEST);
            toolbar.add(filterChipsPanel, BorderLayout.CENTER);
            toolbar.add(actionCluster, BorderLayout.EAST);
            card.add(toolbar, BorderLayout.NORTH);

            String[] columns = {"CASE ID", "CASE TITLE", "CATEGORY", "LOCATION", "STATUS", "ASSIGNED TO", "PRIORITY"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            table = new JTable(tableModel);
            table.setOpaque(false);
            table.setBackground(new Color(15, 23, 42));
            table.setForeground(Color.WHITE);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            table.setRowHeight(34);
            table.setShowGrid(false);
            table.setSelectionBackground(new Color(52, 211, 153, 40));
            table.setSelectionForeground(Color.WHITE);

            rowSorter = new TableRowSorter<>(tableModel);
            table.setRowSorter(rowSorter);

            table.getTableHeader().setOpaque(false);
            table.getTableHeader().setBackground(new Color(20, 27, 40));
            table.getTableHeader().setForeground(new Color(148, 163, 184));
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
            table.getTableHeader().setPreferredSize(new Dimension(0, 30));

            table.setDefaultRenderer(Object.class, new CleanRowRenderer());
            table.getColumnModel().getColumn(4).setCellRenderer(new BadgePillRenderer(true));
            table.getColumnModel().getColumn(6).setCellRenderer(new BadgePillRenderer(false));

            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) openAssignDialogForSelected();
                }
            });

            JScrollPane sp = new JScrollPane(table);
            sp.getViewport().setBackground(new Color(15, 23, 42));
            sp.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 50), 1));
            card.add(sp, BorderLayout.CENTER);

            add(card, BorderLayout.CENTER);
        }

        public void resetViewToNormal() {
            if (searchField != null) searchField.setText("");
            if (table != null) table.clearSelection();
            currentStatusFilter = "ALL";
            if (filterChips != null) {
                for (FilterChip c : filterChips) {
                    c.setSelected("ALL".equalsIgnoreCase(c.getText()));
                }
            }
            loadAvailableCases();
        }

        private void applyFilters() {
            String query = searchField.getText().trim();
            RowFilter<DefaultTableModel, Object> textFilter = null;
            RowFilter<DefaultTableModel, Object> statusFilter = null;

            if (!query.isEmpty()) {
                textFilter = RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query), 0, 1, 3, 5);
            }
            if (!"ALL".equalsIgnoreCase(currentStatusFilter)) {
                statusFilter = RowFilter.regexFilter("(?i)^" + currentStatusFilter + "$", 4);
            }

            List<RowFilter<DefaultTableModel, Object>> filters = new ArrayList<>();
            if (textFilter != null) filters.add(textFilter);
            if (statusFilter != null) filters.add(statusFilter);

            rowSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
            queueCounterLbl.setText(table.getRowCount() + " Listed");
        }

        public void loadAvailableCases() {
            SwingWorker<List<Object[]>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<Object[]> doInBackground() {
                    List<Object[]> rows = new ArrayList<>();
                    // Available pool: Cases with status PENDING, CLAIM_PENDING, or unassigned ACTIVE
                    String sql = "SELECT case_number, title, category, location_jurisdiction, status, assigned_officer, priority " +
                            "FROM cases WHERE UPPER(TRIM(status)) IN ('PENDING', 'CLAIM_PENDING') " +
                            "   OR (UPPER(TRIM(status)) = 'ACTIVE' AND (assigned_officer IS NULL OR assigned_officer = '' OR assigned_officer = 'Unassigned')) " +
                            "ORDER BY id DESC";

                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {

                        while (rs.next()) {
                            String caseNum = rs.getString("case_number");
                            String title = rs.getString("title");
                            String cat = rs.getString("category");
                            String loc = rs.getString("location_jurisdiction");
                            String status = rs.getString("status");
                            String officer = rs.getString("assigned_officer");
                            String prio = rs.getString("priority");

                            if (officer == null || officer.trim().isEmpty() || "--".equals(officer)) {
                                officer = "Unassigned";
                            }

                            rows.add(new Object[]{
                                    caseNum != null ? caseNum : "--",
                                    title != null ? title : "--",
                                    cat != null ? cat : "--",
                                    loc != null ? loc : "--",
                                    status != null ? status : "PENDING",
                                    officer,
                                    prio != null ? prio : "HIGH"
                            });
                        }
                    } catch (Exception ex) {
                        System.err.println("Load available cases error: " + ex.getMessage());
                    }
                    return rows;
                }

                @Override
                protected void done() {
                    try {
                        tableModel.setRowCount(0);
                        for (Object[] r : get()) tableModel.addRow(r);
                        applyFilters();
                    } catch (Exception ignored) {}
                }
            };
            worker.execute();
        }

        private void openAssignDialogForSelected() {
            int selectedRow = table.getSelectedRow();
            if (selectedRow == -1) {
                GlassToastModal.showToast((JFrame) SwingUtilities.getWindowAncestor(this), "SELECTION REQUIRED", "Please select a case row from the table first.", false);
                return;
            }
            int modelRow = table.convertRowIndexToModel(selectedRow);
            String caseId = (String) tableModel.getValueAt(modelRow, 0);

            AssignOnlyModal.showModal((JFrame) SwingUtilities.getWindowAncestor(this), caseId, adminName, () -> {
                resetViewToNormal();
                if (onModifiedCallback != null) onModifiedCallback.run();
            });
        }
    }

    private static String getColSafe(ResultSet rs, ResultSetMetaData md, int count, String colName) {
        try {
            for (int i = 1; i <= count; i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(colName) || md.getColumnName(i).equalsIgnoreCase(colName)) {
                    return rs.getString(i);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // =========================================================================
    // MODAL: ASSIGN / REASSIGN DIALOG (DIRECT DISPATCH TO MY CASES AS ACTIVE)
    // =========================================================================
    private static class AssignOnlyModal extends JDialog {

        private final int BASE_HEIGHT = 295;
        private final int EXPANDED_HEIGHT = 380;
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

        public static void showModal(JFrame parent, String caseNumber, String adminName, Runnable onAssigned) {
            AssignOnlyModal modal = new AssignOnlyModal(parent, caseNumber, adminName, onAssigned);
            modal.setVisible(true);
        }

        private AssignOnlyModal(JFrame parent, String caseNumber, String adminName, Runnable onAssigned) {
            super(parent, true);
            setUndecorated(true);
            setSize(490, BASE_HEIGHT);
            setLocationRelativeTo(parent);
            setBackground(new Color(0, 0, 0, 0));

            JPanel card = new JPanel(new BorderLayout(0, 10)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(15, 23, 42, 252));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(new Color(52, 211, 153));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                    g2.dispose();
                }
            };
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(16, 20, 16, 20));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);

            JLabel titleLbl = new JLabel("ASSIGN / REASSIGN DOSSIER");
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            titleLbl.setForeground(new Color(52, 211, 153));

            JLabel caseBadge = new JLabel(caseNumber);
            caseBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
            caseBadge.setForeground(Color.WHITE);

            header.add(titleLbl, BorderLayout.WEST);
            header.add(caseBadge, BorderLayout.EAST);
            card.add(header, BorderLayout.NORTH);

            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setOpaque(false);

            JPanel summaryCard = new JPanel(new GridLayout(2, 2, 10, 6));
            summaryCard.setOpaque(true);
            summaryCard.setBackground(new Color(25, 33, 48));
            summaryCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(56, 189, 248, 60), 1, true),
                    new EmptyBorder(10, 14, 10, 14)
            ));

            JLabel titleValLbl = new JLabel("Title: Loading...");
            JLabel catValLbl = new JLabel("Category: Loading...");
            JLabel locValLbl = new JLabel("Location: Loading...");
            JLabel prioValLbl = new JLabel("Priority: Loading...");

            for (JLabel l : new JLabel[]{titleValLbl, catValLbl, locValLbl, prioValLbl}) {
                l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                l.setForeground(new Color(226, 232, 240));
                summaryCard.add(l);
            }

            JLabel routeLabel = new JLabel("Select Routing Target *");
            routeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            routeLabel.setForeground(UITheme.ACCENT_CYAN);
            routeLabel.setBorder(new EmptyBorder(10, 0, 4, 0));

            routeCombo = new JComboBox<>(new String[]{"Unassigned (Public Queue)", "Assign to Investigator"});
            routeCombo.setBackground(new Color(20, 29, 44));
            routeCombo.setForeground(Color.WHITE);
            routeCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            routeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            routeCombo.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 80), 1));

            investigatorSection = new JPanel() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(440, (int) (68 * expansionProgress));
                }

                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, (int) (68 * expansionProgress));
                }
            };
            investigatorSection.setLayout(new BorderLayout(0, 4));
            investigatorSection.setOpaque(false);
            investigatorSection.setAlignmentX(Component.LEFT_ALIGNMENT);
            investigatorSection.setVisible(false);

            JPanel invInner = new JPanel(new GridLayout(2, 1, 0, 2));
            invInner.setOpaque(false);
            invInner.setBorder(new EmptyBorder(6, 0, 0, 0));

            JLabel invLabel = new JLabel("Assign To Approved Investigator *");
            invLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            invLabel.setForeground(new Color(52, 211, 153));

            investigatorCombo = new JComboBox<>();
            investigatorCombo.setBackground(new Color(20, 29, 44));
            investigatorCombo.setForeground(Color.WHITE);
            investigatorCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            investigatorCombo.setBorder(BorderFactory.createLineBorder(new Color(52, 211, 153, 100), 1));

            invInner.add(invLabel);
            invInner.add(investigatorCombo);
            investigatorSection.add(invInner, BorderLayout.CENTER);

            loadReadOnlyDetails(caseNumber, titleValLbl, catValLbl, locValLbl, prioValLbl, routeCombo, investigatorCombo);

            routeCombo.addActionListener(e -> {
                boolean isInvestigator = routeCombo.getSelectedIndex() == 1;
                triggerDropdownSlideAnimation(isInvestigator);
            });

            centerPanel.add(summaryCard);
            centerPanel.add(routeLabel);
            centerPanel.add(routeCombo);
            centerPanel.add(investigatorSection);

            card.add(centerPanel, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            actions.setOpaque(false);

            JButton cancelBtn = new JButton("CANCEL");
            cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            cancelBtn.setBackground(new Color(30, 41, 59));
            cancelBtn.setForeground(Color.WHITE);
            cancelBtn.setFocusPainted(false);
            cancelBtn.setBorder(new EmptyBorder(7, 14, 7, 14));
            cancelBtn.addActionListener(e -> dispose());

            confirmBtn = new JButton("CONFIRM DISPATCH");
            confirmBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            confirmBtn.setBackground(UITheme.ACCENT_CYAN);
            confirmBtn.setForeground(UITheme.BG_DARK);
            confirmBtn.setFocusPainted(false);
            confirmBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            confirmBtn.setBorder(new EmptyBorder(7, 18, 7, 18));

            confirmBtn.addActionListener(e -> {
                boolean isInvestigator = routeCombo.getSelectedIndex() == 1;
                OfficerItem selectedOfficer = isInvestigator ? (OfficerItem) investigatorCombo.getSelectedItem() : null;

                String finalOfficerUsername = (isInvestigator && selectedOfficer != null && !selectedOfficer.username.isEmpty())
                        ? selectedOfficer.username
                        : "Unassigned";

                // Direct assignment sets status=ACTIVE and created_by=admin to prevent circular approvals
                String finalStatus = (isInvestigator && selectedOfficer != null && !selectedOfficer.username.isEmpty()) ? "ACTIVE" : "PENDING";

                assignAndActivateCase(caseNumber, finalOfficerUsername, finalStatus, adminName, () -> {
                    dispose();
                    if (onAssigned != null) onAssigned.run();
                });
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
            final int duration = 180;
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

        private static void loadReadOnlyDetails(String caseNum, JLabel t, JLabel c, JLabel l, JLabel p, JComboBox<String> rCombo, JComboBox<OfficerItem> iCombo) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                String title = "", cat = "", loc = "", prio = "High", currOfficer = "";
                List<OfficerItem> investigators = new ArrayList<>();

                @Override
                protected Void doInBackground() {
                    String sql = "SELECT title, category, location_jurisdiction, priority, status, assigned_officer FROM cases WHERE TRIM(case_number) = TRIM(?)";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, caseNum);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                title = rs.getString("title");
                                cat = rs.getString("category");
                                loc = rs.getString("location_jurisdiction");
                                prio = rs.getString("priority");
                                currOfficer = rs.getString("assigned_officer");
                            }
                        }
                    } catch (Exception ignored) {}

                    String invSql = "SELECT username, full_name, id_number FROM users " +
                            "WHERE role IN ('INVESTIGATOR', 'OFFICER') AND status = 'ACTIVE' " +
                            "ORDER BY full_name ASC";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(invSql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String u = rs.getString("username");
                            String fn = rs.getString("full_name");
                            String id = rs.getString("id_number");
                            investigators.add(new OfficerItem(u, fn != null ? fn : u, id != null ? id : "BADGE-FIELD"));
                        }
                    } catch (Exception ignored) {}

                    return null;
                }

                @Override
                protected void done() {
                    t.setText("Title: " + (title != null && !title.isEmpty() ? title : "--"));
                    c.setText("Category: " + (cat != null && !cat.isEmpty() ? cat : "--"));
                    l.setText("Location: " + (loc != null && !loc.isEmpty() ? loc : "--"));
                    p.setText("Priority: " + (prio != null && !prio.isEmpty() ? prio : "High"));

                    iCombo.removeAllItems();
                    if (investigators.isEmpty()) {
                        iCombo.addItem(new OfficerItem("", "-- No Approved Investigators Available --", ""));
                    } else {
                        OfficerItem matched = null;
                        for (OfficerItem inv : investigators) {
                            iCombo.addItem(inv);
                            if (currOfficer != null && (currOfficer.equalsIgnoreCase(inv.username) || currOfficer.equalsIgnoreCase(inv.fullName))) {
                                matched = inv;
                            }
                        }
                        if (matched != null) {
                            rCombo.setSelectedIndex(1);
                            iCombo.setSelectedItem(matched);
                        } else {
                            rCombo.setSelectedIndex(0);
                        }
                    }
                }
            };
            worker.execute();
        }

        private static void assignAndActivateCase(String caseNum, String officerUsername, String status, String admin, Runnable onComplete) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    // Set created_by='admin', assigned_officer, and status=ACTIVE to prevent approval loop
                    String sql = "UPDATE cases SET status = ?, assigned_officer = ?, created_by = 'admin', updated_at = NOW() WHERE TRIM(case_number) = TRIM(?)";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, status);
                        ps.setString(2, officerUsername);
                        ps.setString(3, caseNum);
                        ps.executeUpdate();

                        String text = (!"Unassigned".equalsIgnoreCase(officerUsername))
                                ? "CASE DISPATCH: Case [" + caseNum + "] Assigned directly to Detective [" + officerUsername + "] as ACTIVE by " + admin
                                : "CASE ROUTING: Case [" + caseNum + "] Moved to Public Queue (Status: " + status + ") by " + admin;
                        AuditLogger.log(admin, text);
                    } catch (Exception ex) {
                        System.err.println("Assignment error: " + ex.getMessage());
                    }
                    return null;
                }

                @Override
                protected void done() {
                    if (onComplete != null) {
                        SwingUtilities.invokeLater(onComplete);
                    }
                }
            };
            worker.execute();
        }
    }

    // =========================================================================
    // MODAL: EDIT CASE MODAL FOR ALL CASES (SUPPORTS CLOSED, ACTIVE, PENDING)
    // =========================================================================
    public static class EditCaseModal extends JDialog {
        public static void showModal(JFrame parent, String caseNumber, String adminName, Runnable onSaved) {
            EditCaseModal modal = new EditCaseModal(parent, caseNumber, adminName, onSaved);
            modal.setVisible(true);
        }

        private EditCaseModal(JFrame parent, String caseNumber, String adminName, Runnable onSaved) {
            super(parent, true);
            setUndecorated(true);
            setSize(520, 480);
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
            card.setBorder(new EmptyBorder(16, 20, 16, 20));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);

            JLabel titleLbl = new JLabel("EDIT CASE DOSSIER");
            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            titleLbl.setForeground(Color.WHITE);

            JLabel caseBadge = new JLabel(caseNumber);
            caseBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
            caseBadge.setForeground(UITheme.ACCENT_CYAN);

            header.add(titleLbl, BorderLayout.WEST);
            header.add(caseBadge, BorderLayout.EAST);
            card.add(header, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridLayout(5, 1, 0, 6));
            form.setOpaque(false);

            JTextField titleField = createField();
            JTextField locField = createField();
            JComboBox<String> priorityCombo = new JComboBox<>(new String[]{"High", "Medium", "Low"});
            JComboBox<String> statusCombo = new JComboBox<>(new String[]{"ACTIVE", "CLOSED", "PENDING"});
            JTextArea descArea = new JTextArea(3, 20);
            descArea.setBackground(new Color(25, 33, 48));
            descArea.setForeground(Color.WHITE);
            descArea.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            descArea.setLineWrap(true);
            descArea.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 70)));

            loadCaseDataIntoFields(caseNumber, titleField, locField, priorityCombo, statusCombo, descArea);

            form.add(wrapField("Case Title", titleField));
            form.add(wrapField("Location", locField));
            form.add(wrapField("Priority", priorityCombo));
            form.add(wrapField("Status", statusCombo));
            form.add(wrapField("Description", new JScrollPane(descArea)));

            card.add(form, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            actions.setOpaque(false);

            JButton cancelBtn = new JButton("DISCARD");
            cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            cancelBtn.setBackground(new Color(30, 41, 59));
            cancelBtn.setForeground(Color.WHITE);
            cancelBtn.setFocusPainted(false);
            cancelBtn.setBorder(new EmptyBorder(8, 14, 8, 14));
            cancelBtn.addActionListener(e -> dispose());

            JButton saveBtn = new JButton("SAVE & UPDATE");
            saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
            saveBtn.setBackground(UITheme.ACCENT_CYAN);
            saveBtn.setForeground(UITheme.BG_DARK);
            saveBtn.setFocusPainted(false);
            saveBtn.setBorder(new EmptyBorder(8, 18, 8, 18));

            saveBtn.addActionListener(e -> {
                String newTitle = titleField.getText().trim();
                String newLoc = locField.getText().trim();
                String newPriority = (String) priorityCombo.getSelectedItem();
                String newStatus = (String) statusCombo.getSelectedItem();
                String newDesc = descArea.getText().trim();

                updateCaseInDB(caseNumber, newTitle, newLoc, newPriority, newStatus, newDesc, adminName);
                dispose();
                if (onSaved != null) onSaved.run();
            });

            actions.add(cancelBtn);
            actions.add(saveBtn);
            card.add(actions, BorderLayout.SOUTH);

            setContentPane(card);
        }

        private static JTextField createField() {
            JTextField tf = new JTextField();
            tf.setBackground(new Color(25, 33, 48));
            tf.setForeground(Color.WHITE);
            tf.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            tf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(56, 189, 248, 70), 1),
                    new EmptyBorder(3, 8, 3, 8)
            ));
            return tf;
        }

        private static JPanel wrapField(String label, JComponent comp) {
            JPanel p = new JPanel(new BorderLayout(0, 2));
            p.setOpaque(false);
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.BOLD, 10));
            l.setForeground(UITheme.TEXT_MUTED);
            p.add(l, BorderLayout.NORTH);
            p.add(comp, BorderLayout.CENTER);
            return p;
        }

        private static void loadCaseDataIntoFields(String caseNum, JTextField titleF, JTextField locF, JComboBox<String> prioC, JComboBox<String> statC, JTextArea descA) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                String title = "", loc = "", prio = "High", stat = "ACTIVE", desc = "";

                @Override
                protected Void doInBackground() {
                    String sql = "SELECT title, location_jurisdiction, priority, status, incident_description FROM cases WHERE TRIM(case_number) = TRIM(?)";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, caseNum);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                title = rs.getString("title");
                                loc = rs.getString("location_jurisdiction");
                                prio = rs.getString("priority");
                                stat = rs.getString("status");
                                desc = rs.getString("incident_description");
                            }
                        }
                    } catch (Exception ignored) {}
                    return null;
                }

                @Override
                protected void done() {
                    titleF.setText(title != null ? title : "");
                    locF.setText(loc != null ? loc : "");
                    if (prio != null) prioC.setSelectedItem(prio);
                    if (stat != null) statC.setSelectedItem(stat.toUpperCase());
                    descA.setText(desc != null ? desc : "");
                }
            };
            worker.execute();
        }

        private static void updateCaseInDB(String caseNum, String title, String loc, String priority, String status, String desc, String admin) {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    String updateSql = "UPDATE cases SET title = ?, location_jurisdiction = ?, priority = ?, status = ?, incident_description = ?, updated_at = NOW() WHERE TRIM(case_number) = TRIM(?)";
                    try (Connection conn = DBConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, title);
                        ps.setString(2, loc);
                        ps.setString(3, priority);
                        ps.setString(4, status);
                        ps.setString(5, desc);
                        ps.setString(6, caseNum);
                        ps.executeUpdate();
                        AuditLogger.log(admin, "CASE MODIFICATION: Case [" + caseNum + "] Updated (Status: " + status + ") by " + admin);
                    } catch (Exception ex) {
                        System.err.println("Update error: " + ex.getMessage());
                    }
                    return null;
                }
            };
            worker.execute();
        }
    }

    private static class VectorRefreshButton extends JButton {
        private boolean isHovered = false;

        public VectorRefreshButton() {
            setPreferredSize(new Dimension(34, 28));
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

            g2.setColor(isHovered ? new Color(56, 189, 248, 40) : new Color(25, 33, 48));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.setColor(new Color(56, 189, 248, isHovered ? 140 : 70));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

            g2.setColor(UITheme.ACCENT_CYAN);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = 5;

            g2.drawArc(cx - r, cy - r, r * 2, r * 2, 45, 270);
            g2.drawLine(cx + r - 1, cy - r, cx + r - 1, cy - 1);
            g2.drawLine(cx + r - 1, cy - r, cx + r + 3, cy - r);

            g2.dispose();
        }
    }

    private static class FilterChip extends JButton {
        private boolean isSelected;

        public FilterChip(String text, boolean selected) {
            super(text);
            this.isSelected = selected;
            setFont(new Font("Segoe UI", Font.BOLD, 10));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(4, 11, 4, 11));
        }

        public void setSelected(boolean sel) {
            this.isSelected = sel;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isSelected) {
                g2.setColor(new Color(56, 189, 248, 45));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(UITheme.ACCENT_CYAN);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                setForeground(UITheme.ACCENT_CYAN);
            } else {
                g2.setColor(new Color(25, 33, 48));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(255, 255, 255, 25));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                setForeground(new Color(148, 163, 184));
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class CleanRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setBorder(new EmptyBorder(0, 10, 0, 10));

            // Check if status is CLOSED to render matte black
            Object stVal = table.getValueAt(row, 4);
            boolean isClosed = stVal != null && "CLOSED".equalsIgnoreCase(stVal.toString().trim());

            if (isClosed) {
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? new Color(7, 10, 15) : new Color(4, 6, 10));
                    c.setForeground(new Color(100, 116, 139));
                } else {
                    c.setBackground(new Color(30, 41, 59, 160));
                    c.setForeground(new Color(148, 163, 184));
                }
            } else {
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140));
                    c.setForeground(col == 0 ? UITheme.ACCENT_CYAN : Color.WHITE);
                } else {
                    c.setBackground(new Color(56, 189, 248, 45));
                    c.setForeground(Color.WHITE);
                }
            }
            return c;
        }
    }

    private static class BadgePillRenderer extends DefaultTableCellRenderer {
        private final boolean isStatus;

        public BadgePillRenderer(boolean isStatus) {
            this.isStatus = isStatus;
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString().trim().toUpperCase() : "--";
            Object stVal = table.getValueAt(row, 4);
            boolean isRowClosed = stVal != null && "CLOSED".equalsIgnoreCase(stVal.toString().trim());

            JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            badgeWrapper.setOpaque(true);

            if (isRowClosed) {
                badgeWrapper.setBackground(isSelected ? new Color(30, 41, 59, 160) : (row % 2 == 0 ? new Color(7, 10, 15) : new Color(4, 6, 10)));
            } else {
                badgeWrapper.setBackground(isSelected ? new Color(56, 189, 248, 45) : (row % 2 == 0 ? new Color(17, 24, 39, 140) : new Color(13, 19, 32, 140)));
            }

            Color badgeBg;
            Color badgeText;

            if (isStatus) {
                if ("CLOSED".equals(text)) {
                    badgeBg = new Color(20, 27, 40, 220);
                    badgeText = new Color(100, 116, 139);
                } else if ("ACTIVE".equals(text)) {
                    badgeBg = new Color(52, 211, 153, 35);
                    badgeText = new Color(52, 211, 153);
                } else if ("CLAIM_PENDING".equals(text)) {
                    badgeBg = new Color(56, 189, 248, 35);
                    badgeText = UITheme.ACCENT_CYAN;
                } else {
                    badgeBg = new Color(251, 191, 36, 35);
                    badgeText = new Color(251, 191, 36);
                }
            } else {
                if (isRowClosed) {
                    badgeBg = new Color(20, 27, 40, 180);
                    badgeText = new Color(100, 116, 139);
                } else {
                    if ("HIGH".equalsIgnoreCase(text) || "CRITICAL".equalsIgnoreCase(text)) {
                        badgeBg = new Color(239, 68, 68, 35);
                        badgeText = new Color(248, 113, 113);
                    } else if ("MEDIUM".equalsIgnoreCase(text)) {
                        badgeBg = new Color(251, 191, 36, 35);
                        badgeText = new Color(251, 191, 36);
                    } else {
                        badgeBg = new Color(56, 189, 248, 35);
                        badgeText = UITheme.ACCENT_CYAN;
                    }
                }
            }

            final Color fBg = badgeBg;
            final Color fTxt = badgeText;

            JLabel pill = new JLabel("  " + text + "  ") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(fBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(fTxt.getRed(), fTxt.getGreen(), fTxt.getBlue(), isRowClosed ? 80 : 120));
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

    public static class GlassToastModal extends JDialog {

        public static void showToast(JFrame parent, String title, String message, boolean isSuccess) {
            GlassToastModal modal = new GlassToastModal(parent, title, message, isSuccess);
            modal.setVisible(true);
        }

        private GlassToastModal(JFrame parent, String title, String message, boolean isSuccess) {
            super(parent, true);
            setUndecorated(true);
            setSize(380, 190);
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