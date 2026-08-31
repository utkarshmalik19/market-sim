package gui;

import javax.swing.*;
import java.awt.*;

/** A JPanel with a rounded-rectangle background and border — Swing has no built-in for this. */
public class RoundedPanel extends JPanel {
    private final int radius;
    private final Color bg;
    private final Color borderColor;

    public RoundedPanel(int radius, Color bg, Color borderColor) {
        this.radius = radius;
        this.bg = bg;
        this.borderColor = borderColor;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        g2.dispose();
        super.paintComponent(g);
    }
}