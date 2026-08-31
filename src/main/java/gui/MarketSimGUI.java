package gui;

import db.Db;
import db.ExcelLoader;
import market.Market;
import model.Company;
import model.EventRow;
import model.HoldingView;
import model.Player;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private static final Color BUY_COLOR = new Color(0x1E8E3E);
    private static final Color SELL_COLOR = new Color(0xC5221F);
    private static final Color NEXT_DAY_COLOR = new Color(0x1E5AA8);
    private static final Color POSITIVE = new Color(0x1E8E3E);
    private static final Color NEGATIVE = new Color(0xC5221F);
    private static final Color CARD_BORDER = new Color(0xD9DEE4);
    private static final Color MUTED = new Color(0x777777);

    private final Connection conn;
    private final String saveName;
    private String selectedTicker;

    private JComboBox<String> playerCombo;
    private JLabel dayLabel;
    private JLabel welcomeLabel;

    private DefaultTableModel marketModel;
    private JTable marketTable;
    private JLabel selectedLabel;
    private JLabel sectorValueLabel;
    private JTextField qtyField;

    private JLabel cashLabel, valueLabel, networthLabel, pnlLabel, dayChangeLabel;
    private DefaultTableModel portfolioModel;
    private JTable portfolioTable;
    private LineChartPanel netWorthChart;

    private JPanel newsListPanel;

    private DefaultTableModel leaderboardModel;
    private JTable leaderboardTable;

    private static final String[] MARKET_COLUMNS = {
            "Ticker", "Company", "Sector", "Price", "Prev", "Day $", "Day %", "Market Cap", "Volume", "P/E"
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

        setSize(1300, 800);
        setMinimumSize(new Dimension(1080, 680));
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

        welcomeLabel = new JLabel("", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        welcomeLabel.setForeground(new Color(0x444444));
        header.add(welcomeLabel, BorderLayout.CENTER);

        dayLabel = new JLabel("Day: —");
        dayLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        header.add(dayLabel, BorderLayout.EAST);

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
        JButton nextDayBtn = coloredButton("Next Day", NEXT_DAY_COLOR);
        nextDayBtn.addActionListener(e -> advanceDay());
        rightTop.add(refreshBtn);
        rightTop.add(nextDayBtn);
        top.add(rightTop, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);

        marketModel = nonEditableModel(MARKET_COLUMNS);
        marketTable = new JTable(marketModel);
        marketTable.setRowHeight(28);
        marketTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        marketTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        marketTable.getColumnModel().getColumn(5).setCellRenderer(new SignedValueRenderer());
        marketTable.getColumnModel().getColumn(6).setCellRenderer(new SignedValueRenderer());
        marketTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                stockSelected();
            }
        });
        marketTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openStockDetails();
                }
            }
        });
        panel.add(new JScrollPane(marketTable), BorderLayout.CENTER);

        panel.add(buildTradePanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTradePanel() {
        JPanel trade = new JPanel(new BorderLayout(20, 0));
        trade.setBorder(BorderFactory.createTitledBorder("Trade"));

        JPanel infoBlock = new JPanel();
        infoBlock.setLayout(new BoxLayout(infoBlock, BoxLayout.Y_AXIS));
        infoBlock.setBorder(new EmptyBorder(4, 4, 4, 4));

        selectedLabel = new JLabel("Select a stock");
        selectedLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        selectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel sectorRow = new JPanel(new GridLayout(1, 2, 14, 0));
        sectorRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectorRow.setBorder(new EmptyBorder(6, 0, 6, 0));
        JLabel sectorHeading = new JLabel("Sector");
        sectorHeading.setFont(new Font("SansSerif", Font.BOLD, 12));
        sectorHeading.setForeground(MUTED);
        sectorValueLabel = new JLabel("—");
        sectorValueLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sectorRow.add(sectorHeading);
        sectorRow.add(sectorValueLabel);

        JButton detailsBtn = new JButton("View Full Details \u2192");
        detailsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsBtn.addActionListener(e -> openStockDetails());

        infoBlock.add(selectedLabel);
        infoBlock.add(sectorRow);
        infoBlock.add(detailsBtn);
        trade.add(infoBlock, BorderLayout.WEST);

        JPanel tradeControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        tradeControls.add(new JLabel("Quantity:"));
        qtyField = new JTextField("1", 6);
        tradeControls.add(qtyField);

        JButton buyBtn = coloredButton("BUY", BUY_COLOR);
        buyBtn.addActionListener(e -> executeTrade("buy"));
        JButton sellBtn = coloredButton("SELL", SELL_COLOR);
        sellBtn.addActionListener(e -> executeTrade("sell"));
        tradeControls.add(buyBtn);
        tradeControls.add(sellBtn);

        trade.add(tradeControls, BorderLayout.EAST);
        return trade;
    }

    private JButton coloredButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        return btn;
    }

    private void openStockDetails() {
        if (selectedTicker == null) {
            Dialogs.showError(this, "No Stock Selected", "Select a stock first.");
            return;
        }
        try {
            new StockDetailDialog(this, conn, selectedTicker).setVisible(true);
        } catch (SQLException e) {
            Dialogs.showError(this, "Database Error", e.getMessage());
        }
    }

    // ---------------------------------------------------------
    // PORTFOLIO TAB
    // ---------------------------------------------------------

    private JPanel buildPortfolioTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel summary = new JPanel(new GridLayout(1, 5, 12, 0));
        cashLabel = addWidget(summary, "Cash");
        valueLabel = addWidget(summary, "Portfolio Value");
        networthLabel = addWidget(summary, "Net Worth");
        pnlLabel = addWidget(summary, "Total P&L");
        dayChangeLabel = addWidget(summary, "Day Change");
        panel.add(summary, BorderLayout.NORTH);

        portfolioModel = nonEditableModel(PORTFOLIO_COLUMNS);
        portfolioTable = new JTable(portfolioModel);
        portfolioTable.setRowHeight(28);
        portfolioTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        for (int col : new int[]{6, 7, 8, 9}) {
            portfolioTable.getColumnModel().getColumn(col).setCellRenderer(new SignedValueRenderer());
        }

        RoundedPanel chartCard = new RoundedPanel(10, Color.WHITE, CARD_BORDER);
        chartCard.setLayout(new BorderLayout());
        chartCard.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel chartTitle = new JLabel("Net Worth Over Time");
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        chartTitle.setBorder(new EmptyBorder(0, 0, 6, 0));
        chartCard.add(chartTitle, BorderLayout.NORTH);
        netWorthChart = new LineChartPanel();
        netWorthChart.setPreferredSize(new Dimension(0, 170));
        netWorthChart.setEmptyMessage("Advance a couple of days to see the trend");
        chartCard.add(netWorthChart, BorderLayout.CENTER);

        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        centerWrap.add(new JScrollPane(portfolioTable), BorderLayout.CENTER);
        centerWrap.add(chartCard, BorderLayout.SOUTH);
        panel.add(centerWrap, BorderLayout.CENTER);

        return panel;
    }

    private JLabel addWidget(JPanel parent, String title) {
        RoundedPanel card = new RoundedPanel(10, Color.WHITE, CARD_BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleLabel.setForeground(MUTED);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel valueLbl = new JLabel("$0.00");
        valueLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        valueLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLbl);
        parent.add(card);
        return valueLbl;
    }

    // ---------------------------------------------------------
    // NEWS TAB
    // ---------------------------------------------------------

    private JPanel buildNewsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(new Color(0xF5F7FA));

        newsListPanel = new JPanel();
        newsListPanel.setLayout(new BoxLayout(newsListPanel, BoxLayout.Y_AXIS));
        newsListPanel.setBackground(new Color(0xF5F7FA));
        newsListPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

        JScrollPane scroll = new JScrollPane(newsListPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
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
        leaderboardTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
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
            welcomeLabel.setText(player != null ? "Welcome, " + player : "");
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
                    Formatters.money(c.getPrice()), Formatters.money(c.getPrevPrice()),
                    String.format("%+,.2f", change), Formatters.signedPercent(changePct / 100),
                    Formatters.marketCap(c.getMarketCap()), Formatters.volume(c.getVolume()),
                    Formatters.peRatio(c.getEps(), c.getPrice())
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
            selectedLabel.setText(c.getTicker() + " — " + c.getName() + " @ " + Formatters.money(c.getPrice()));
            sectorValueLabel.setText(c.getSector());
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
            netWorthChart.setData(List.of(), POSITIVE);
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
                    Formatters.money(h.getAvgCost()), Formatters.money(h.getPrice()), Formatters.money(value),
                    Formatters.signedMoney(pnl), Formatters.signedPercent(pnlPct / 100),
                    Formatters.signedMoney(holdingDayChange), Formatters.signedPercent(dayPct / 100)
            });
        }

        double netWorth = p.getCash() + portfolioValue;
        cashLabel.setText(Formatters.money(p.getCash()));
        valueLabel.setText(Formatters.money(portfolioValue));
        networthLabel.setText(Formatters.money(netWorth));
        pnlLabel.setText(Formatters.signedMoney(totalPnl));
        pnlLabel.setForeground(totalPnl >= 0 ? POSITIVE : NEGATIVE);
        dayChangeLabel.setText(Formatters.signedMoney(dayChange));
        dayChangeLabel.setForeground(dayChange >= 0 ? POSITIVE : NEGATIVE);

        List<double[]> history = Db.getNetWorthHistory(conn, player);
        List<LineChartPanel.Point> points = new ArrayList<>();
        for (double[] pair : history) {
            points.add(new LineChartPanel.Point(pair[0], pair[1]));
        }
        boolean trendingUp = points.size() < 2 || points.get(points.size() - 1).y() >= points.get(0).y();
        netWorthChart.setData(points, trendingUp ? POSITIVE : NEGATIVE);
    }

    private void refreshNews() throws SQLException {
        newsListPanel.removeAll();
        List<EventRow> events = Db.getRecentEvents(conn, 40);

        if (events.isEmpty()) {
            JLabel empty = new JLabel("No headlines yet — advance a day to see the market move.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
            empty.setForeground(MUTED);
            empty.setBorder(new EmptyBorder(20, 4, 0, 0));
            newsListPanel.add(empty);
        } else {
            for (EventRow e : events) {
                JPanel card = NewsCard.create(e);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                newsListPanel.add(card);
                newsListPanel.add(Box.createVerticalStrut(8));
            }
        }

        newsListPanel.revalidate();
        newsListPanel.repaint();
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
            leaderboardModel.addRow(new Object[]{rank++, row[0], Formatters.money((Double) row[1])});
        }
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