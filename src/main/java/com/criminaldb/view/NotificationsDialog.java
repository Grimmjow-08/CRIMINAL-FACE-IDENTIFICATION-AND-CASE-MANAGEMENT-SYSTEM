package com.criminaldb.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class NotificationsDialog extends JDialog {

    public static class NotificationItem {
        public enum Type { ACCESS_REQUEST, CASE_REQUEST }
        public final Type type;
        public final String title;
        public final String description;
        public final String timestamp;
        public final String targetId;

        public NotificationItem(Type type, String title, String description, String timestamp, String targetId) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.timestamp = timestamp;
            this.targetId = targetId;
        }
    }

    private final Consumer<String> navigationCallback;
    private final List<NotificationItem> items;
    private final CardLayout tabLayout;
    private final JPanel tabContainer;
    private JButton allTabBtn;
    private JButton accessTabBtn;
    private JButton caseTabBtn;

    public NotificationsDialog(Frame owner, List<NotificationItem> items, Consumer<String> navigationCallback) {
        super(owner, true);
        this.items = items;
        this.navigationCallback = navigationCallback;

        setUndecorated(true);
        setSize(480, 560);
        setBackground(new Color(0, 0, 0, 0));

        Point ownerLoc = owner.getLocationOnScreen();
        int x = ownerLoc.x + owner.getWidth() - getWidth() - 25;
        int y = ownerLoc.y + 65;
        setLocation(x, y);

        JPanel rootCard = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(15, 23, 42, 248));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(56, 189, 248, 120));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
                g2.dispose();
            }
        };
        rootCard.setOpaque(false);
        rootCard.setBorder(new EmptyBorder(18, 18, 16, 18));

        // Header Bar
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titleLbl = new JLabel("INCIDENT & ACCESS DISPATCH");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLbl.setForeground(UITheme.ACCENT_CYAN);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        closeBtn.setForeground(new Color(148, 163, 184));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());

        header.add(titleLbl, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        rootCard.add(header, BorderLayout.NORTH);

        // Tab Filter Bar
        JPanel tabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        tabsBar.setOpaque(false);

        allTabBtn = createTabBtn("All (" + items.size() + ")", true);
        long accessCount = items.stream().filter(i -> i.type == NotificationItem.Type.ACCESS_REQUEST).count();
        long caseCount = items.stream().filter(i -> i.type == NotificationItem.Type.CASE_REQUEST).count();

        accessTabBtn = createTabBtn("Access Requests (" + accessCount + ")", false);
        caseTabBtn = createTabBtn("Case Requests (" + caseCount + ")", false);

        tabLayout = new CardLayout();
        tabContainer = new JPanel(tabLayout);
        tabContainer.setOpaque(false);

        tabContainer.add(buildListScroller(null), "ALL");
        tabContainer.add(buildListScroller(NotificationItem.Type.ACCESS_REQUEST), "ACCESS");
        tabContainer.add(buildListScroller(NotificationItem.Type.CASE_REQUEST), "CASES");

        allTabBtn.addActionListener(e -> {
            highlightTab(allTabBtn);
            tabLayout.show(tabContainer, "ALL");
        });
        accessTabBtn.addActionListener(e -> {
            highlightTab(accessTabBtn);
            tabLayout.show(tabContainer, "ACCESS");
        });
        caseTabBtn.addActionListener(e -> {
            highlightTab(caseTabBtn);
            tabLayout.show(tabContainer, "CASES");
        });

        tabsBar.add(allTabBtn);
        tabsBar.add(accessTabBtn);
        tabsBar.add(caseTabBtn);

        JPanel centerContent = new JPanel(new BorderLayout(0, 8));
        centerContent.setOpaque(false);
        centerContent.add(tabsBar, BorderLayout.NORTH);
        centerContent.add(tabContainer, BorderLayout.CENTER);

        rootCard.add(centerContent, BorderLayout.CENTER);
        setContentPane(rootCard);
    }

    private JScrollPane buildListScroller(NotificationItem.Type filterType) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(new EmptyBorder(6, 4, 6, 4));

        boolean found = false;
        for (NotificationItem it : items) {
            if (filterType == null || it.type == filterType) {
                list.add(createNotificationCard(it));
                list.add(Box.createVerticalStrut(8));
                found = true;
            }
        }

        if (!found) {
            JPanel emptyBox = new JPanel(new GridBagLayout());
            emptyBox.setOpaque(false);
            JLabel emptyLbl = new JLabel("No pending notifications in queue.");
            emptyLbl.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            emptyLbl.setForeground(new Color(148, 163, 184));
            emptyBox.add(emptyLbl);
            list.add(emptyBox);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        return scroll;
    }

    private JPanel createNotificationCard(NotificationItem item) {
        Color accent = (item.type == NotificationItem.Type.ACCESS_REQUEST)
                ? new Color(251, 191, 36)
                : new Color(56, 189, 248);

        JPanel card = new JPanel(new BorderLayout(8, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(24, 32, 47, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel titleLbl = new JLabel(item.title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLbl.setForeground(accent);

        JLabel timeLbl = new JLabel(item.timestamp != null ? item.timestamp : "");
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(new Color(148, 163, 184));

        top.add(titleLbl, BorderLayout.WEST);
        top.add(timeLbl, BorderLayout.EAST);

        JLabel descLbl = new JLabel(item.description);
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLbl.setForeground(Color.WHITE);

        JButton actionBtn = new JButton("Review →");
        actionBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        actionBtn.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
        actionBtn.setForeground(accent);
        actionBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120), 1, true),
                new EmptyBorder(3, 8, 3, 8)
        ));
        actionBtn.setFocusPainted(false);
        actionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        actionBtn.addActionListener(e -> {
            dispose();
            if (navigationCallback != null) {
                if (item.type == NotificationItem.Type.ACCESS_REQUEST) {
                    navigationCallback.accept("Investigators");
                } else {
                    navigationCallback.accept("Case Requests"); // Direct route to Case Requests
                }
            }
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(descLbl, BorderLayout.CENTER);
        bottom.add(actionBtn, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(bottom, BorderLayout.CENTER);
        return card;
    }

    private JButton createTabBtn(String label, boolean active) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        setTabStyle(btn, active);
        return btn;
    }

    private void highlightTab(JButton active) {
        setTabStyle(allTabBtn, active == allTabBtn);
        setTabStyle(accessTabBtn, active == accessTabBtn);
        setTabStyle(caseTabBtn, active == caseTabBtn);
    }

    private void setTabStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(UITheme.ACCENT_CYAN);
            btn.setForeground(new Color(15, 23, 42));
            btn.setBorder(new EmptyBorder(5, 12, 5, 12));
        } else {
            btn.setBackground(new Color(30, 41, 59));
            btn.setForeground(new Color(148, 163, 184));
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.BORDER_GLASS, 1, true),
                    new EmptyBorder(4, 11, 4, 11)
            ));
        }
    }
}