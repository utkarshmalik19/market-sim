package gui;

import db.Db;
import model.Company;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Full detail popup for a single stock: every metric plus a price history chart. */
public class StockDetailDialog extends JDialog {

    private static final Color HEADER_BG = new Color(0x1E3A5F);
    private static final Color POSITIVE = new Color(0x1E8E3E);
    private static final Color NEGATIVE = new Color(0xC5221F);
    private static final Color LABEL_COLOR = new Color(0x777777);

    public StockDetailDialog(Window owner, Connection conn, String ticker) throws SQLException {
        super(owner, "Stock Details", ModalityType.APPLICATION_MODAL);

        Company c = Db.getCompany(conn, ticker);
        if (c == null) {
            dispose();
            return;
        }

        setLayout(new BorderLayout());
        setSize(640, 600);
        setMinimumSize(new Dimension(560, 520));
        setLocationRelativeTo(owner);

        double change = c.getPrice() - c.getPrevPrice();
        double changePct = c.getPrevPrice() != 0 ? change / c.getPrevPrice() * 100 : 0;
        boolean up = change >= 0;

        add(buildHeader(c, change, changePct, up), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel chartTitle = new JLabel("Price History");
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        chartTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartTitle.setBorder(new EmptyBorder(0, 0, 6, 0));
        body.add(chartTitle);

        LineChartPanel chart = new LineChartPanel();
        chart.setAlignmentX(Component.LEFT_ALIGNMENT);
        chart.setPreferredSize(new Dimension(560, 200));
        chart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        chart.setEmptyMessage("Price history builds up as you advance days");
        chart.setData(toPoints(Db.getPriceHistory(conn, ticker)), up ? POSITIVE : NEGATIVE);
        body.add(chart);

        body.add(Box.createVerticalStrut(20));

        JLabel metricsTitle = new JLabel("Key Metrics");
        metricsTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        metricsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        metricsTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        body.add(metricsTitle);

        JPanel metrics = new JPanel(new GridLayout(4, 2, 16, 14));
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.setBackground(Color.WHITE);
        metrics.add(metricBox("Sector", c.getSector()));
        metrics.add(metricBox("Market Cap", Formatters.marketCap(c.getMarketCap())));
        metrics.add(metricBox("Volume", Formatters.volume(c.getVolume())));
        metrics.add(metricBox("P/E Ratio", Formatters.peRatio(c.getEps(), c.getPrice())));
        metrics.add(metricBox("Dividend Yield", Formatters.percent(c.getDividendYield())));
        metrics.add(metricBox("EPS", String.format("$%.2f", c.getEps())));
        metrics.add(metricBox("Revenue Growth", Formatters.percent(c.getRevenueGrowth())));
        metrics.add(metricBox("Volatility", Formatters.percent(c.getVolatility())));
        body.add(metrics);

        add(new JScrollPane(body), BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBorder(new EmptyBorder(0, 20, 14, 20));
        footer.add(closeBtn);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildHeader(Company c, double change, double changePct, boolean up) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel nameLabel = new JLabel(c.getTicker() + " — " + c.getName());
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String changeText = (up ? "+" : "-") + Formatters.money(Math.abs(change));
        JLabel priceLabel = new JLabel(String.format("%s   %s (%s%.2f%%)",
                Formatters.money(c.getPrice()), changeText, up ? "+" : "", changePct));
        priceLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        priceLabel.setForeground(up ? new Color(0x81C995) : new Color(0xF28B82));
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

        header.add(nameLabel);
        header.add(priceLabel);
        return header;
    }

    private JPanel metricBox(String label, String value) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(Color.WHITE);

        JLabel l = new JLabel(label);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(LABEL_COLOR);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.BOLD, 16));
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        v.setBorder(new EmptyBorder(2, 0, 0, 0));

        row.add(l);
        row.add(v);
        return row;
    }

    private List<LineChartPanel.Point> toPoints(List<double[]> raw) {
        List<LineChartPanel.Point> points = new ArrayList<>();
        for (double[] pair : raw) {
            points.add(new LineChartPanel.Point(pair[0], pair[1]));
        }
        return points;
    }
}