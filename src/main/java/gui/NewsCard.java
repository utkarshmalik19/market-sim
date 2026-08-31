package gui;

import model.EventRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** A single "news app" style card for one market headline. */
public final class NewsCard {

    private static final Color POSITIVE = new Color(0x1E8E3E);
    private static final Color NEGATIVE = new Color(0xC5221F);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER = new Color(0xE3E6EA);
    private static final Color HEADLINE_COLOR = new Color(0x202124);
    private static final Color MUTED = new Color(0x9AA0A6);

    private NewsCard() {
    }

    public static JPanel create(EventRow e) {
        boolean positive = looksPositive(e.getHeadline());
        Color accent = positive ? POSITIVE : NEGATIVE;

        RoundedPanel outer = new RoundedPanel(12, CARD_BG, BORDER);
        outer.setLayout(new BorderLayout(12, 0));
        outer.setBorder(new EmptyBorder(12, 6, 12, 14));
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        outer.setPreferredSize(new Dimension(500, 76));

        JPanel stripe = new JPanel();
        stripe.setOpaque(true);
        stripe.setBackground(accent);
        stripe.setPreferredSize(new Dimension(5, 1));
        outer.add(stripe, BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(0, 10, 0, 0));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel ticker = new JLabel(e.getTicker());
        ticker.setFont(new Font("SansSerif", Font.BOLD, 13));
        ticker.setForeground(accent);
        topRow.add(ticker, BorderLayout.WEST);

        JLabel dayBadge = new JLabel("Day " + e.getTick());
        dayBadge.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dayBadge.setForeground(MUTED);
        topRow.add(dayBadge, BorderLayout.EAST);

        JLabel headline = new JLabel("<html><body style='width: 340px'>" + e.getHeadline() + "</body></html>");
        headline.setFont(new Font("SansSerif", Font.PLAIN, 13));
        headline.setForeground(HEADLINE_COLOR);
        headline.setBorder(new EmptyBorder(4, 0, 0, 0));

        center.add(topRow);
        center.add(headline);
        outer.add(center, BorderLayout.CENTER);

        JLabel icon = new JLabel(positive ? "\uD83D\uDCC8" : "\uD83D\uDCC9"); // chart up / down
        icon.setFont(new Font("SansSerif", Font.PLAIN, 24));
        outer.add(icon, BorderLayout.EAST);

        return outer;
    }

    private static boolean looksPositive(String headline) {
        String h = headline.toLowerCase();
        return h.contains("beats") || h.contains("rumor") || h.contains("unveils");
    }
}