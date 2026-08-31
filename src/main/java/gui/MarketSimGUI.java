package gui;

import db.Db;
import db.ExcelLoader;
import market.Market;
import model.Company;
import model.EventRow;
import model.HoldingView;
import model.Player;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Swing GUI for a single save's play session. */
public class MarketSimGUI extends JFrame {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");

    private final Connection conn;
    private final String saveName;
    private String selectedTicker;

    private JComboBox<String> playerCombo;
    private JLabel dayLabel;
    private JLabel playerLabel;

    private DefaultTableModel marketModel;
    private JTable marketTable;
    private JLabel selectedLabel;
    private JLabel companyInfoLabel;
    private JTextField qtyField;

    private JLabel cashLabel, valueLabel, networthLabel, pnlLabel, dayChangeLabel;
    private DefaultTableModel portfolioModel;
    private JTable portfolioTable;

    private JTextArea newsArea;

    private DefaultTableModel leaderboardModel;
    private JTable leaderboardTable;

    private static final String[] MARKET_COLUMNS = {
            "Ticker", "Company", "Sector", "Price", "Prev", "Day Change", "Day %", "EPS", "Revenue Growth", "Volatility"
    };
    private static final String[] PORTFOLIO_COLUMNS = {
            "Ticker", "Company", "Qty", "Avg Cost", "Price", "Value", "P&L", "Total %", "Day $", "Day %"
    };
    private static final String[] LEADERBOARD_COLUMNS = {"Rank", "Player", "Net Worth"};

    public MarketSimGUI(Connection conn, String saveName) throws SQLException {
        super("MarketSim — " + saveName);
        this.conn = conn;
        this.saveName = saveName;
        Db.initSchema(conn);
        if (Db.getStartDate(conn) == null) {
            Db.setStartDate(conn, LocalDate.now());
            conn.commit();
        }

        setSize(1250, 750);
        setMinimumSize(new Dimension(1050, 650));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                close();
            }
        });

        buildMenuBar();
        buildUI();
        autoLoadCompaniesIfEmpty();
        refresh();
    }

    /** Safety net: a directly-loaded old save with no companies still gets a market. */
    private void autoLoadCompaniesIfEmpty() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS n FROM companies")) {
            if (rs.next() && rs.getInt("n") == 0) {
                ExcelLoader.loadCompaniesFromResource(conn, false);
            }
        } catch (Exception e) {
            Dialogs.showError(this, "Load Error",
                    "Could not auto-load companies.xlsx from resources: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------
    // MENU BAR
    // ---------------------------------------------------------

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu gameMenu = new JMenu("Game");
        JMenuItem newGame = new JMenuItem("New Game...");
        newGame.addActionListener(e -> returnToMenu(WelcomeScreen::goToNewGame));
        JMenuItem loadGame = new JMenuItem("Load Game...");
        loadGame.addActionListener(e -> returnToMenu(WelcomeScreen::goToLoadGame));
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> close());
        gameMenu.add(newGame);
        gameMenu.add(loadGame);
        gameMenu.addSeparator();
        gameMenu.add(exit);

        JMenu playerMenu = new JMenu("Player");
        JMenuItem addPlayer = new JMenuItem("Add Player...");
        addPlayer.addActionListener(e -> onAddPlayer());
        playerMenu.add(addPlayer);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About MarketSim");
        about.addActionListener(e -> Dialogs.showInfo(this, "About MarketSim",
                "MarketSim — a two-player stock market simulation.\nSave: " + saveName));
        helpMenu.add(about);

        menuBar.add(gameMenu);
        menuBar.add(playerMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    /** Closes this save and hands off to a fresh WelcomeScreen, optionally jumping to a card. */
    private void returnToMenu(Consumer<WelcomeScreen> afterShow) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Leave this game and return to the menu? Your progress is saved automatically.",
                "Return to Menu", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
        dispose();
        WelcomeScreen welcome = new WelcomeScreen();
        afterShow.accept(welcome);
        welcome.setVisible(true);
    }

    private void onAddPlayer() {
        String name = JOptionPane.showInputDialog(this, "Player name:");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        name = name.trim();
        String cashStr = JOptionPane.showInputDialog(this, "Starting cash:", "100000");
        if (cashStr == null) {
            return;
        }
        double cash;
        try {
            cash = Double.parseDouble(cashStr.trim());
        } catch (NumberFormatException ex) {
            Dialogs.showError(this, "Error", "Cash must be a number.");
            return;
        }

        try {
            if (Db.getPlayer(conn, name) != null) {
                JOptionPane.showMessageDialog(this, "Player '" + name + "' already exists.");
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO players (name, cash) VALUES (?, ?)")) {
                ps.setString(1, name);
                ps.setDouble(2, cash);
                ps.executeUpdate();
            }
            conn.commit();
            refresh();
        } catch (SQLException ex) {
            Dialogs.showError(this, "Database Error", ex.getMessage());
        }
    }

    // ---------------------------------------------------------
    // MAIN UI
    // ---------------------------------------------------------

    private void buildUI() {
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("MarketSim");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        header.add(title, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        playerLabel = new JLabel("");
        dayLabel = new JLabel("Day: —");
        rightHeader.add(playerLabel);
        rightHeader.add(dayLabel);
        header.add(rightHeader, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Market", buildMarketTab());
        tabs.addTab("Portfolio", buildPortfolioTab());
        tabs.addTab("News", buildNewsTab());
        tabs.addTab("Leaderboard", buildLeaderboardTab());
        tabs.addChangeListener(e -> refresh());
        add(tabs, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------
    // MARKET TAB
    // ---------------------------------------------------------

    private JPanel buildMarketTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel top = new JPanel(new BorderLayout());
        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftTop.add(new JLabel("Player:"));
        playerCombo = new JComboBox<>();
        playerCombo.addActionListener(e -> refresh());
        leftTop.add(playerCombo);
        top.add(leftTop, BorderLayout.WEST);

        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JButton nextDayBtn = new JButton("Next Day");
        nextDayBtn.addActionListener(e -> advanceDay());
        rightTop.add(refreshBtn);
        rightTop.add(nextDayBtn);
        top.add(rightTop, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);

        marketModel = nonEditableModel(MARKET_COLUMNS);
        marketTable = new JTable(marketModel);
        marketTable.setRowHeight(28);
        marketTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        marketTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                stockSelected();
            }
        });
        panel.add(new JScrollPane(marketTable), BorderLayout.CENTER);

        JPanel trade = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        trade.setBorder(BorderFactory.createTitledBorder("Trade"));

        selectedLabel = new JLabel("Select a stock");
        selectedLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        trade.add(selectedLabel);

        trade.add(new JLabel("      Quantity:"));
        qtyField = new JTextField("1", 6);
        trade.add(qtyField);

        JButton buyBtn = new JButton("BUY");
        buyBtn.addActionListener(e -> executeTrade("buy"));
        JButton sellBtn = new JButton("SELL");
        sellBtn.addActionListener(e -> executeTrade("sell"));
        trade.add(buyBtn);
        trade.add(sellBtn);

        companyInfoLabel = new JLabel("");
        trade.add(Box.createHorizontalStrut(30));
        trade.add(companyInfoLabel);

        panel.add(trade, BorderLayout.SOUTH);
        return panel;
    }

    // ---------------------------------------------------------
    // PORTFOLIO TAB
    // ---------------------------------------------------------

    private JPanel buildPortfolioTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel summary = new JPanel(new GridLayout(1, 5, 10, 0));
        cashLabel = addMetric(summary, "Cash");
        valueLabel = addMetric(summary, "Portfolio Value");
        networthLabel = addMetric(summary, "Net Worth");
        pnlLabel = addMetric(summary, "Total P&L");
        dayChangeLabel = addMetric(summary, "Day Change");
        panel.add(summary, BorderLayout.NORTH);

        portfolioModel = nonEditableModel(PORTFOLIO_COLUMNS);
        portfolioTable = new JTable(portfolioModel);
        portfolioTable.setRowHeight(28);
        panel.add(new JScrollPane(portfolioTable), BorderLayout.CENTER);

        return panel;
    }

    private JLabel addMetric(JPanel parent, String title) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel valueLbl = new JLabel("$0.00");
        valueLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        valueLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(titleLabel);
        box.add(valueLbl);
        parent.add(box);
        return valueLbl;
    }

    // ---------------------------------------------------------
    // NEWS TAB
    // ---------------------------------------------------------

    private JPanel buildNewsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        newsArea = new JTextArea();
        newsArea.setEditable(false);
        newsArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        newsArea.setLineWrap(true);
        newsArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(newsArea), BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------
    // LEADERBOARD TAB
    // ---------------------------------------------------------

    private JPanel buildLeaderboardTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        leaderboardModel = nonEditableModel(LEADERBOARD_COLUMNS);
        leaderboardTable = new JTable(leaderboardModel);
        leaderboardTable.setRowHeight(28);
        panel.add(new JScrollPane(leaderboardTable), BorderLayout.CENTER);
        return panel;
    }

    private DefaultTableModel nonEditableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ---------------------------------------------------------
    // REFRESH
    // ---------------------------------------------------------

    private void refresh() {
        try {
            refreshPlayers();
            refreshMarket();
            refreshPortfolio();
            refreshNews();
            refreshLeaderboard();

            dayLabel.setText("Day: " + Db.getCurrentDate(conn).format(DATE_FMT));
            String player = (String) playerCombo.getSelectedItem();
            playerLabel.setText(player != null ? "Player: " + player : "");
        } catch (SQLException e) {
            Dialogs.showError(this, "Database Error", e.getMessage());
        }
    }

    private void refreshPlayers() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM players ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        String current = (String) playerCombo.getSelectedItem();
        playerCombo.removeAllItems();
        for (String n : names) {
            playerCombo.addItem(n);
        }
        if (current != null && names.contains(current)) {
            playerCombo.setSelectedItem(current);
        } else if (!names.isEmpty()) {
            playerCombo.setSelectedItem(names.get(0));
        }
    }

    private void refreshMarket() throws SQLException {
        marketModel.setRowCount(0);
        for (Company c : Db.getAllCompanies(conn)) {
            double change = c.getPrice() - c.getPrevPrice();
            double changePct = c.getPrevPrice() != 0 ? change / c.getPrevPrice() * 100 : 0;
            marketModel.addRow(new Object[]{
                    c.getTicker(), c.getName(), c.getSector(),
                    money(c.getPrice()), money(c.getPrevPrice()),
                    String.format("%+,.2f", change), String.format("%+.2f%%", changePct),
                    String.format("%.2f", c.getEps()), String.format("%.2f%%", c.getRevenueGrowth() * 100),
                    String.format("%.2f%%", c.getVolatility() * 100)
            });
        }
    }

    private void stockSelected() {
        int row = marketTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        selectedTicker = (String) marketModel.getValueAt(row, 0);
        try {
            Company c = Db.getCompany(conn, selectedTicker);
            if (c == null) {
                return;
            }
            selectedLabel.setText(c.getTicker() + " — " + c.getName() + " @ " + money(c.getPrice()));
            companyInfoLabel.setText(String.format(
                    "Sector: %s    EPS: $%.2f    Revenue Growth: %.1f%%    Volatility: %.1f%%",
                    c.getSector(), c.getEps(), c.getRevenueGrowth() * 100, c.getVolatility() * 100));
        } catch (SQLException e) {
            Dialogs.showError(this, "Database Error", e.getMessage());
        }
    }

    private void executeTrade(String side) {
        String player = (String) playerCombo.getSelectedItem();
        if (player == null) {
            Dialogs.showError(this, "Trade Error", "Select a player first.");
            return;
        }
        if (selectedTicker == null) {
            Dialogs.showError(this, "Trade Error", "Select a stock first.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
        } catch (NumberFormatException e) {
            Dialogs.showError(this, "Trade Error", "Quantity must be a whole number.");
            return;
        }

        try {
            Market.executeTrade(conn, player, selectedTicker, qty, side);
            Company c = Db.getCompany(conn, selectedTicker);
            Dialogs.showTradeResult(this, side, qty, selectedTicker, c.getPrice(), c.getPrice() * qty);
            refresh();
        } catch (IllegalArgumentException e) {
            Dialogs.showError(this, "Trade Error", e.getMessage());
        } catch (SQLException e) {
            Dialogs.showError(this, "Database Error", e.getMessage());
        }
    }

    private void advanceDay() {
        try {
            int tick = Market.runTick(conn);
            refresh();

            List<EventRow> events = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ticker, headline FROM events WHERE tick = ? ORDER BY id")) {
                ps.setInt(1, tick);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        events.add(new EventRow(tick, rs.getString("ticker"), rs.getString("headline")));
                    }
                }
            }

            Dialogs.showDayAdvance(this, Db.getCurrentDate(conn), events);
        } catch (SQLException e) {
            Dialogs.showError(this, "Market Error", e.getMessage());
        }
    }

    private void refreshPortfolio() throws SQLException {
        portfolioModel.setRowCount(0);
        String player = (String) playerCombo.getSelectedItem();
        if (player == null) {
            cashLabel.setText("$0.00");
            valueLabel.setText("$0.00");
            networthLabel.setText("$0.00");
            pnlLabel.setText("$0.00");
            dayChangeLabel.setText("$0.00");
            return;
        }

        Player p = Db.getPlayer(conn, player);
        if (p == null) {
            return;
        }

        double portfolioValue = 0;
        double totalPnl = 0;
        double dayChange = 0;

        for (HoldingView h : Db.getPlayerHoldings(conn, player)) {
            double value = h.getQty() * h.getPrice();
            double cost = h.getQty() * h.getAvgCost();
            double pnl = value - cost;
            double pnlPct = cost != 0 ? pnl / cost * 100 : 0;
            double dayChangePerShare = h.getPrice() - h.getPrevPrice();
            double holdingDayChange = dayChangePerShare * h.getQty();
            double dayPct = h.getPrevPrice() != 0 ? dayChangePerShare / h.getPrevPrice() * 100 : 0;

            portfolioValue += value;
            totalPnl += pnl;
            dayChange += holdingDayChange;

            portfolioModel.addRow(new Object[]{
                    h.getTicker(), h.getName(), h.getQty(),
                    money(h.getAvgCost()), money(h.getPrice()), money(value),
                    String.format("$%+,.2f", pnl), String.format("%+.2f%%", pnlPct),
                    String.format("$%+,.2f", holdingDayChange), String.format("%+.2f%%", dayPct)
            });
        }

        double netWorth = p.getCash() + portfolioValue;
        cashLabel.setText(money(p.getCash()));
        valueLabel.setText(money(portfolioValue));
        networthLabel.setText(money(netWorth));
        pnlLabel.setText(String.format("$%+,.2f", totalPnl));
        dayChangeLabel.setText(String.format("$%+,.2f", dayChange));
    }

    private void refreshNews() throws SQLException {
        List<EventRow> events = Db.getRecentEvents(conn, 30);
        StringBuilder sb = new StringBuilder();
        for (int i = events.size() - 1; i >= 0; i--) {
            EventRow e = events.get(i);
            sb.append("[Day ").append(e.getTick()).append("] [").append(e.getTicker())
                    .append("] ").append(e.getHeadline()).append("\n\n");
        }
        newsArea.setText(sb.toString());
    }

    private void refreshLeaderboard() throws SQLException {
        leaderboardModel.setRowCount(0);
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM players")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }

        List<Object[]> ranked = new ArrayList<>();
        for (String name : names) {
            Double netWorth = Db.getPlayerNetWorth(conn, name);
            ranked.add(new Object[]{name, netWorth != null ? netWorth : 0.0});
        }
        ranked.sort((a, b) -> Double.compare((Double) b[1], (Double) a[1]));

        int rank = 1;
        for (Object[] row : ranked) {
            leaderboardModel.addRow(new Object[]{rank++, row[0], money((Double) row[1])});
        }
    }

    private static String money(double v) {
        return String.format("$%,.2f", v);
    }

    // ---------------------------------------------------------
    // CLOSE
    // ---------------------------------------------------------

    private void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
        dispose();
        System.exit(0);
    }
}