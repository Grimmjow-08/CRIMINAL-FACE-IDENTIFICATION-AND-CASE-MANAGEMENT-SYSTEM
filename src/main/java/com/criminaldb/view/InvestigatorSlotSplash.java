package com.criminaldb.view;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class InvestigatorSlotSplash extends JWindow {

    private final String investigatorUsername;
    private final String fullBadgeId; // e.g. "INV-7777"
    private final String targetFourChars;

    // Animation control
    private int currentPhase = 0; // 0 = Blank initial, 1 = Spinning/Locking, 2 = Green Hold, 3 = Fade Out
    private float welcomeAlpha = 0f;
    private float greenGlowPulse = 0f;

    // Slot machine reels: 4 characters
    private final char[] currentReelChars = new char[]{'7', '7', '7', '7'};
    private final boolean[] reelLocked = new boolean[]{false, false, false, false};
    private final Random random = new Random();
    private static final String GLYPH_POOL = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Timer masterTimer;
    private long startTime;

    public InvestigatorSlotSplash(String username, String badgeId) {
        this.investigatorUsername = username;
        this.fullBadgeId = (badgeId != null && badgeId.startsWith("INV-") && badgeId.length() == 8)
                ? badgeId.toUpperCase()
                : "INV-7777";
        this.targetFourChars = this.fullBadgeId.substring(4);

        setSize(1080, 750);
        setLocationRelativeTo(null);
        setBackground(new Color(10, 15, 26));

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                renderSplashSequence((Graphics2D) g.create());
            }
        };
        canvas.setBackground(new Color(10, 15, 26));
        setContentPane(canvas);

        startAnimationPipeline();
    }

    private void startAnimationPipeline() {
        startTime = System.currentTimeMillis();

        masterTimer = new Timer(20, e -> {
            long elapsed = System.currentTimeMillis() - startTime;

            // Phase 0: Blank slate (0ms - 500ms)
            if (elapsed < 500) {
                currentPhase = 0;
            }
            // Phase 1: Welcome & Reel Spinning/Locking (500ms - 2700ms)
            else if (elapsed < 2700) {
                currentPhase = 1;
                long activeElapsed = elapsed - 500;

                welcomeAlpha = Math.min(1.0f, activeElapsed / 400f);

                // Slot reels sequentially lock
                for (int i = 0; i < 4; i++) {
                    long lockThreshold = 800 + (i * 350);
                    if (activeElapsed >= lockThreshold) {
                        currentReelChars[i] = targetFourChars.charAt(i);
                        reelLocked[i] = true;
                    } else {
                        currentReelChars[i] = GLYPH_POOL.charAt(random.nextInt(GLYPH_POOL.length()));
                    }
                }
            }
            // Phase 2: GREEN LIGHT PAUSE / DELAY (2700ms - 4600ms ~ 2 full seconds)
            else if (elapsed < 4600) {
                currentPhase = 2;
                welcomeAlpha = 1.0f;
                for (int i = 0; i < 4; i++) {
                    currentReelChars[i] = targetFourChars.charAt(i);
                    reelLocked[i] = true;
                }
                // Gentle neon pulsing effect during green hold
                greenGlowPulse = (float) ((Math.sin((elapsed - 2700) / 180.0) + 1.0) / 2.0);
            }
            // Phase 3: Smooth Fade Out to Blank Slate (4600ms - 5300ms)
            else if (elapsed < 5300) {
                currentPhase = 3;
                long fadeOutTime = elapsed - 4600;
                welcomeAlpha = Math.max(0.0f, 1.0f - (fadeOutTime / 600f));
            }
            // Finished: Terminate & launch dashboard
            else {
                masterTimer.stop();
                dispose();
                SwingUtilities.invokeLater(() -> {
                    new InvestigatorDashboardFrame(investigatorUsername).setVisible(true);
                });
            }

            repaint();
        });

        masterTimer.start();
    }

    private void renderSplashSequence(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        if (currentPhase == 0) {
            g2.setColor(new Color(10, 15, 26));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            return;
        }

        // Ambient cyber grid
        g2.setColor(new Color(255, 255, 255, (int) (welcomeAlpha * 8)));
        for (int x = 0; x < getWidth(); x += 40) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += 40) g2.drawLine(0, y, getWidth(), y);

        // Alpha composite for clean in/out fade
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, welcomeAlpha))));

        // 1. WELCOME Header
        g2.setFont(new Font("Segoe UI", Font.BOLD, 42));
        FontMetrics fmWelcome = g2.getFontMetrics();
        String welcomeTxt = "WELCOME";
        int wX = cx - (fmWelcome.stringWidth(welcomeTxt) / 2);
        int wY = cy - 70;

        g2.setColor(new Color(56, 189, 248, 50));
        g2.drawString(welcomeTxt, wX - 1, wY + 1);
        g2.drawString(welcomeTxt, wX + 1, wY - 1);
        g2.setColor(Color.WHITE);
        g2.drawString(welcomeTxt, wX, wY);

        // Subtitle
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g2.setColor(new Color(148, 163, 184));
        String sub = "CLEARANCE AUTHENTICATED • FIELD TERMINAL ACCESS";
        FontMetrics fmSub = g2.getFontMetrics();
        g2.drawString(sub, cx - (fmSub.stringWidth(sub) / 2), cy - 35);

        // 2. The Slot Machine "777" Reel Tumbler
        int boxW = 56;
        int boxH = 68;
        int spacing = 12;
        int totalReelsW = (boxW * 5) + (spacing * 4);
        int startX = cx - (totalReelsW / 2);
        int boxY = cy + 5;

        // "INV-" Prefix Box
        g2.setColor(new Color(24, 32, 47, 240));
        g2.fillRoundRect(startX, boxY, boxW + 20, boxH, 10, 10);
        g2.setColor(new Color(56, 189, 248, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(startX, boxY, boxW + 20, boxH, 10, 10);

        g2.setFont(new Font("Consolas", Font.BOLD, 22));
        g2.setColor(new Color(56, 189, 248));
        FontMetrics fmPrefix = g2.getFontMetrics();
        String pfx = "INV-";
        g2.drawString(pfx, startX + ((boxW + 20 - fmPrefix.stringWidth(pfx)) / 2), boxY + 43);

        int currentX = startX + boxW + 20 + spacing;

        // 4 Tumbler Reel Slots
        for (int i = 0; i < 4; i++) {
            boolean locked = reelLocked[i];

            // Background
            g2.setColor(locked ? new Color(16, 185, 129, 45) : new Color(20, 27, 40, 240));
            g2.fillRoundRect(currentX, boxY, boxW, boxH, 10, 10);

            // Glowing border (pulses brightly in green during the 2-second hold)
            Color borderColor;
            if (locked) {
                int glow = (int) (180 + (greenGlowPulse * 75));
                glow = Math.min(255, Math.max(0, glow));
                borderColor = new Color(52, 211, 153, glow);
            } else {
                borderColor = new Color(251, 191, 36, 180);
            }

            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(locked ? 2.2f : 1.2f));
            g2.drawRoundRect(currentX, boxY, boxW, boxH, 10, 10);

            // Reel Character
            g2.setFont(new Font("Consolas", Font.BOLD, 34));
            FontMetrics fmChar = g2.getFontMetrics();
            String reelChar = String.valueOf(currentReelChars[i]);
            int charX = currentX + ((boxW - fmChar.stringWidth(reelChar)) / 2);
            int charY = boxY + 47;

            g2.setColor(locked ? new Color(52, 211, 153) : new Color(251, 191, 36));
            g2.drawString(reelChar, charX, charY);

            // Glass reflection highlight
            GradientPaint glassGrad = new GradientPaint(
                    currentX, boxY, new Color(255, 255, 255, 30),
                    currentX, boxY + (boxH / 2), new Color(255, 255, 255, 0)
            );
            g2.setPaint(glassGrad);
            g2.fillRoundRect(currentX + 1, boxY + 1, boxW - 2, boxH / 2, 8, 8);

            currentX += boxW + spacing;
        }

        // Status pill underneath
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        boolean allLocked = reelLocked[0] && reelLocked[1] && reelLocked[2] && reelLocked[3];
        String badgeMsg = allLocked ? "IDENTITY CODE VERIFIED • CLEARANCE GRANTED" : "SYNCHRONIZING SECURE BADGE VECTOR...";
        FontMetrics fmStatus = g2.getFontMetrics();
        g2.setColor(allLocked ? new Color(52, 211, 153) : new Color(251, 191, 36));
        g2.drawString(badgeMsg, cx - (fmStatus.stringWidth(badgeMsg) / 2), boxY + boxH + 34);

        g2.dispose();
    }
}