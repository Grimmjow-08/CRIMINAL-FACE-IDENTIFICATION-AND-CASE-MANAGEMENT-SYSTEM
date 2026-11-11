package com.criminaldb.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class UITheme {
    // Charcoal / Navy Canvas Palette
    public static final Color BG_DARK = new Color(11, 15, 23);
    public static final Color CARD_BG = new Color(22, 28, 42, 210);       // Translucent Frosted Glass
    public static final Color DRAWER_BG = new Color(16, 21, 33, 245);     // Glass Overlay
    public static final Color ACCENT_CYAN = new Color(56, 189, 248);
    public static final Color ACCENT_HOVER = new Color(14, 165, 233);
    public static final Color TEXT_WHITE = new Color(248, 250, 252);
    public static final Color TEXT_MUTED = new Color(148, 163, 184);
    public static final Color BORDER_GLASS = new Color(255, 255, 255, 30);
    public static final Color BORDER_ACTIVE = new Color(56, 189, 248, 120);
    public static final Color INPUT_BG = new Color(15, 20, 31, 200);

    public static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font HEADER_FONT = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font REGULAR_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 13);

    // Glass Card Panel with smooth rounded borders and subtle translucent shadow
    public static class GlassCardPanel extends JPanel {
        private final int radius;
        private final Color bgColor;

        public GlassCardPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Frosted translucent surface
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

            // Subtle glass border
            g2.setColor(BORDER_GLASS);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
        }
    }

    public static JTextField createStyledTextField() {
        JTextField field = new JTextField();
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_WHITE);
        field.setCaretColor(ACCENT_CYAN);
        field.setFont(REGULAR_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_GLASS, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        return field;
    }

    public static JPasswordField createStyledPasswordField() {
        JPasswordField field = new JPasswordField();
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_WHITE);
        field.setCaretColor(ACCENT_CYAN);
        field.setFont(REGULAR_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_GLASS, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        return field;
    }
}