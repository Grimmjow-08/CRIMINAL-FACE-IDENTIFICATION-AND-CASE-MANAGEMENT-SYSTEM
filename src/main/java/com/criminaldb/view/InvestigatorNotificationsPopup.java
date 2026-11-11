package com.criminaldb.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InvestigatorNotificationsPopup extends JDialog {

    public enum NotificationType {
        ALL,
        ACCESS_REQUEST,
        CASE_REQUEST
    }

    public static class PopupItem {
        public final NotificationType type;
        public final String title;
        public final String description;
        public final String time;
        public final String targetKey;

        public PopupItem(NotificationType type, String title, String description, String time, String targetKey) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.time = time;
            this.targetKey = targetKey;
        }
    }

    private final List<PopupItem> allItems;
    private final Consumer<String> onNavigateCallback;

    private NotificationType activeFilter = NotificationType.ALL;
    private JPanel listContainer;

    private JButton allTabBtn;
    private JButton accessTabBtn;
    private JButton casesTabBtn;

    public InvestigatorNotificationsPopup(Window owner, List<PopupItem> items, Point bellScreenLocation, Consumer<String> onNavigateCallback) {
        super(owner);
        this.allItems = items != null ? items : new ArrayList<>();
        this.onNavigateCallback = onNavigateCallback;

        setUndecorated(true);
        setSize(390, 480);
        setBackground(new Color(0, 0, 0, 0));

        // Position directly underneath the bell icon aligned to the right edge
        if (bellScreenLocation != null) {
            int x = bellScreenLocation.x - 350;
            int y = bellScreenLocation.y + 36;
            setLocation(Math.max(10, x), Math.max(10, y));
        } else {
            setLocationRelativeTo(owner);
        }

        initUI();
    }

    private void initUI() {
        // Main Outer Card
        JPanel card = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Deep Cyber Dark Background
                g2.setColor(new Color(11, 19, 34, 250));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

                // Sleek Cyan Outline Glow
                g2.setColor(new Color(56, 189, 248, 140));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        // Header (Title + Close '×')
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titleLbl = new JLabel("INCIDENT & ACCESS DISPATCH");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLbl.setForeground(new Color(56, 189, 248));

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 17));
        closeBtn.setForeground(new Color(148, 163, 184));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());

        header.add(titleLbl, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        // Center: Tabs + List Container
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);

        // Filter Tabs Row
        centerPanel.add(buildFilterTabsRow(), BorderLayout.NORTH);

        // Scrollable Notification List
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        centerPanel.add(scroll, BorderLayout.CENTER);
        card.add(centerPanel, BorderLayout.CENTER);

        setContentPane(card);

        // Initial Filter Render
        renderFilteredNotifications();
    }

    private JPanel buildFilterTabsRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);

        long allCount = allItems.size();
        long accessCount = allItems.stream().filter(i -> i.type == NotificationType.ACCESS_REQUEST).count();
        long caseCount = allItems.stream().filter(i -> i.type == NotificationType.CASE_REQUEST).count();

        allTabBtn = createFilterTab("All (" + allCount + ")", true);
        accessTabBtn = createFilterTab("Access Requests (" + accessCount + ")", false);
        casesTabBtn = createFilterTab("Case Requests (" + caseCount + ")", false);

        allTabBtn.addActionListener(e -> setTabFilter(NotificationType.ALL, allTabBtn));
        accessTabBtn.addActionListener(e -> setTabFilter(NotificationType.ACCESS_REQUEST, accessTabBtn));
        casesTabBtn.addActionListener(e -> setTabFilter(NotificationType.CASE_REQUEST, casesTabBtn));

        row.add(allTabBtn);
        row.add(accessTabBtn);
        row.add(casesTabBtn);
        return row;
    }

    private void setTabFilter(NotificationType type, JButton activeBtn) {
        this.activeFilter = type;
        updateTabStyle(allTabBtn, activeBtn == allTabBtn);
        updateTabStyle(accessTabBtn, activeBtn == accessTabBtn);
        updateTabStyle(casesTabBtn, activeBtn == casesTabBtn);
        renderFilteredNotifications();
    }

    private void renderFilteredNotifications() {
        listContainer.removeAll();

        List<PopupItem> visibleItems = new ArrayList<>();
        for (PopupItem item : allItems) {
            if (activeFilter == NotificationType.ALL || item.type == activeFilter) {
                visibleItems.add(item);
            }
        }

        if (visibleItems.isEmpty()) {
            JPanel emptyPanel = new JPanel(new GridBagLayout());
            emptyPanel.setOpaque(false);
            emptyPanel.setPreferredSize(new Dimension(350, 240));

            JLabel emptyLbl = new JLabel("No pending notifications in queue.");
            emptyLbl.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            emptyLbl.setForeground(new Color(148, 163, 184, 180));
            emptyPanel.add(emptyLbl);

            listContainer.add(emptyPanel);
        } else {
            for (PopupItem item : visibleItems) {
                listContainer.add(createNotificationCard(item));
                listContainer.add(Box.createVerticalStrut(8));
            }
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel createNotificationCard(PopupItem item) {
        JPanel card = new JPanel(new BorderLayout(8, 4)) {
            private boolean isHovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        dispose();
                        if (onNavigateCallback != null) {
                            if (item.type == NotificationType.ACCESS_REQUEST) {
                                onNavigateCallback.accept("Profile");
                            } else {
                                onNavigateCallback.accept("My Case Requests");
                            }
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(isHovered ? new Color(28, 41, 65, 230) : new Color(18, 27, 44, 210));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                Color border = (item.type == NotificationType.ACCESS_REQUEST) ? new Color(52, 211, 153, 90) : new Color(56, 189, 248, 90);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));
        card.setBorder(new EmptyBorder(8, 12, 8, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));

        // Header row
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        Color titleColor = (item.type == NotificationType.ACCESS_REQUEST) ? new Color(52, 211, 153) : new Color(56, 189, 248);
        JLabel titleLbl = new JLabel(item.title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(titleColor);

        JLabel timeLbl = new JLabel(item.time);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(new Color(148, 163, 184));

        top.add(titleLbl, BorderLayout.WEST);
        top.add(timeLbl, BorderLayout.EAST);

        JLabel descLbl = new JLabel(item.description);
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLbl.setForeground(Color.WHITE);

        card.add(top, BorderLayout.NORTH);
        card.add(descLbl, BorderLayout.CENTER);
        return card;
    }

    private JButton createFilterTab(String text, boolean isSelected) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        updateTabStyle(btn, isSelected);
        return btn;
    }

    private void updateTabStyle(JButton btn, boolean isSelected) {
        if (isSelected) {
            btn.setBackground(new Color(56, 189, 248));
            btn.setForeground(new Color(15, 23, 42));
            btn.setBorder(new EmptyBorder(5, 10, 5, 10));
        } else {
            btn.setBackground(new Color(20, 29, 46));
            btn.setForeground(new Color(148, 163, 184));
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(56, 189, 248, 50), 1, true),
                    new EmptyBorder(4, 9, 4, 9)
            ));
        }
    }
}