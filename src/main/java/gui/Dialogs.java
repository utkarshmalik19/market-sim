package gui;

import model.EventRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Nicer-looking popups than plain JOptionPane text dumps, used for day-advance and trade results. */
public final class Dialogs {

    private static final Color HEADER_BG = new Color(0x1E3A5F);
    private static final Color HEADER_FG = Color.WHITE;
    private static final Color BUY_COLOR = new Color(0x1E8E3E);
    private static final Color SELL_COLOR = new Color(0xB05A00);
    private static final Color POSITIVE = new Color(0x1E8E3E);
    private static final Color NEGATIVE = new Color(0xC5221F);
    private static final Color CARD_BG = new Color(0xF5F7FA);
    private static final Color BORDER = new Color(0xD9DEE4);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");

    private Dialogs() {
    }

    // ---------------------------------------------------------
    // DAY ADVANCE
    // ---------------------------------------------------------

    public static void showDayAdvance(Component parent, LocalDate date, List<EventRow> events) {
        JDialog dialog = base(parent, "Day Advanced", "\uD83D\uDCC5  " + date.format(DATE_FMT), HEADER_BG);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);

        if (events.isEmpty()) {
            JLabel none = new JLabel("No notable news today");
            none.setFont(new Font("SansSerif", Font.PLAIN, 13));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(none);
        } else {
            JLabel newsTitle = new JLabel("Today's headlines");
            newsTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
            newsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            newsTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
            content.add(newsTitle);

            for (EventRow e : events) {
                content.add(newsCard(e));
                content.add(Box.createVerticalStrut(6));
            }
        }

        finish(dialog, parent, content);
    }

    private static JPanel newsCard(EventRow e) {
        boolean positive = looksPositive(e.getHeadline());

        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JLabel tag = new JLabel(e.getTicker());
        tag.setOpaque(true);
        tag.setBackground(positive ? POSITIVE : NEGATIVE);
        tag.setForeground(Color.WHITE);
        tag.setFont(new Font("SansSerif", Font.BOLD, 12));
        tag.setBorder(new EmptyBorder(4, 8, 4, 8));
        card.add(tag, BorderLayout.WEST);

        JLabel headline = new JLabel("<html>" + e.getHeadline() + "</html>");
        headline.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(headline, BorderLayout.CENTER);

        return card;
    }

    private static boolean looksPositive(String headline) {
        String h = headline.toLowerCase();
        return h.contains("beats") || h.contains("rumor") || h.contains("unveils");
    }

    // ---------------------------------------------------------
    // TRADE RESULT
    // ---------------------------------------------------------

    public static void showTradeResult(Component parent, String side, int qty, String ticker,
                                       double price, double cost) {
        boolean buy = side.equalsIgnoreCase("buy");
        Color headerColor = buy ? BUY_COLOR : SELL_COLOR;
        String icon = buy ? "\u2705" : "\uD83D\uDCB0";
        String verb = buy ? "Bought" : "Sold";

        JDialog dialog = base(parent, "Trade Executed", icon + "  " + verb + " " + qty + " " + ticker, headerColor);

        JPanel content = new JPanel(new GridLayout(3, 2, 10, 10));
        content.setBackground(Color.WHITE);
        content.add(rowLabel("Side"));
        content.add(valueLabel(side.toUpperCase(), buy ? POSITIVE : NEGATIVE));
        content.add(rowLabel("Price per share"));
        content.add(valueLabel(String.format("$%,.2f", price), null));
        content.add(rowLabel("Total"));
        content.add(valueLabel(String.format("$%,.2f", cost), null));

        finish(dialog, parent, content);
    }

    // ---------------------------------------------------------
    // SIMPLE ERROR / INFO (styled wrappers around JOptionPane for consistency)
    // ---------------------------------------------------------

    public static void showError(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfo(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------------------------------------------------
    // SHARED SCAFFOLDING
    // ---------------------------------------------------------

    private static JDialog base(Component parent, String title, String headerText, Color headerBg) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title,
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JLabel header = new JLabel(headerText);
        header.setOpaque(true);
        header.setBackground(headerBg);
        header.setForeground(HEADER_FG);
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setBorder(new EmptyBorder(14, 18, 14, 18));
        dialog.add(header, BorderLayout.NORTH);

        return dialog;
    }

    private static void finish(JDialog dialog, Component parent, JPanel content) {
        content.setBorder(new EmptyBorder(16, 18, 16, 18));
        content.setBackground(Color.WHITE);
        dialog.add(content, BorderLayout.CENTER);

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBorder(new EmptyBorder(0, 18, 14, 18));
        footer.setBackground(Color.WHITE);
        footer.add(ok);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(ok);
        dialog.pack();
        Dimension size = dialog.getSize();
        dialog.setMinimumSize(new Dimension(Math.max(380, size.width), size.height));
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private static JLabel rowLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setForeground(new Color(0x555555));
        return l;
    }

    private static JLabel valueLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        if (color != null) {
            l.setForeground(color);
        }
        return l;
    }
}