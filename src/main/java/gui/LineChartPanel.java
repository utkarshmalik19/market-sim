package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

/** Minimal hand-drawn line chart — no external charting library required. */
public class LineChartPanel extends JPanel {

    public record Point(double x, double y) {
    }

    private List<Point> points = List.of();
    private Color lineColor = new Color(0x1E5AA8);
    private String emptyMessage = "Not enough data yet";

    public LineChartPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(400, 180));
    }

    public void setData(List<Point> points, Color lineColor) {
        this.points = points;
        this.lineColor = lineColor;
        repaint();
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int padLeft = 58, padRight = 15, padTop = 12, padBottom = 10;

        if (points.size() < 2) {
            g2.setColor(new Color(0x9AA0A6));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(emptyMessage);
            g2.drawString(emptyMessage, (w - textWidth) / 2, h / 2);
            g2.dispose();
            return;
        }

        double minY = points.get(0).y();
        double maxY = points.get(0).y();
        for (Point p : points) {
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        if (minY == maxY) {
            minY -= 1;
            maxY += 1;
        }
        double minX = points.get(0).x();
        double maxX = points.get(points.size() - 1).x();
        if (minX == maxX) {
            maxX += 1;
        }

        int plotW = w - padLeft - padRight;
        int plotH = h - padTop - padBottom;

        int gridLines = 4;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int i = 0; i <= gridLines; i++) {
            int y = padTop + plotH * i / gridLines;
            g2.setColor(new Color(0xEDEFF1));
            g2.drawLine(padLeft, y, w - padRight, y);
            double val = maxY - (maxY - minY) * i / gridLines;
            g2.setColor(new Color(0x757575));
            g2.drawString(String.format("%,.0f", val), 4, y + 4);
        }

        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            double px = padLeft + (p.x() - minX) / (maxX - minX) * plotW;
            double py = padTop + plotH - (p.y() - minY) / (maxY - minY) * plotH;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(2.2f));
        g2.draw(path);

        Point last = points.get(points.size() - 1);
        double lastPx = padLeft + (last.x() - minX) / (maxX - minX) * plotW;
        double lastPy = padTop + plotH - (last.y() - minY) / (maxY - minY) * plotH;
        g2.fillOval((int) lastPx - 4, (int) lastPy - 4, 8, 8);

        g2.dispose();
    }
}