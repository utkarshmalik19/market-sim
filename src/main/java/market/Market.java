package market;

import db.Db;
import model.Company;
import model.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/** Algorithmic price engine and trading logic. Direct port of market.py. */
public class Market {

    private record EventTemplate(String template, double lo, double hi) {}

    private static final EventTemplate[] EVENTS = {
            new EventTemplate("%s beats earnings expectations", 0.03, 0.08),
            new EventTemplate("%s misses earnings expectations", -0.08, -0.03),
            new EventTemplate("%s is the subject of acquisition rumors", 0.05, 0.12),
            new EventTemplate("%s faces regulatory scrutiny", -0.10, -0.04),
            new EventTemplate("%s unveils a major new product", 0.02, 0.06),
            new EventTemplate("%s announces surprise executive departure", -0.06, -0.02),
    };

    private static final double EVENT_CHANCE = 0.08;
    private static final double SECTOR_SHOCK_STDEV = 0.008;
    private static final double BASE_NOISE_FLOOR = 0.005;
    private static final double MIN_PRICE = 0.50;

    private static final Random RNG = new Random();

    /** Advances the simulation by one tick. Returns the new tick number. */
    public static int runTick(Connection conn) throws SQLException {
        int tickNum = Db.getTick(conn) + 1;

        List<Company> companies = Db.getAllCompanies(conn);

        Set<String> sectors = new LinkedHashSet<>();
        for (Company c : companies) {
            sectors.add(c.getSector());
        }
        Map<String, Double> sectorShock = new HashMap<>();
        for (String sector : sectors) {
            sectorShock.put(sector, RNG.nextGaussian() * SECTOR_SHOCK_STDEV);
        }

        try (PreparedStatement updatePrice = conn.prepareStatement(
                "UPDATE companies SET price = ?, prev_price = ? WHERE ticker = ?");
             PreparedStatement insertHistory = conn.prepareStatement(
                     "INSERT INTO price_history (tick, ticker, price) VALUES (?, ?, ?)");
             PreparedStatement insertEvent = conn.prepareStatement(
                     "INSERT INTO events (tick, ticker, headline) VALUES (?, ?, ?)")) {

            for (Company c : companies) {
                double oldPrice = c.getPrice();

                double fundamentalDrift = 0.01 * c.getEps() / 10 + 0.15 * c.getRevenueGrowth();
                fundamentalDrift = Math.max(Math.min(fundamentalDrift, 0.01), -0.01);

                double noise = RNG.nextGaussian() * Math.max(c.getVolatility(), BASE_NOISE_FLOOR);

                double eventReturn = 0.0;
                String headline = null;
                if (RNG.nextDouble() < EVENT_CHANCE) {
                    EventTemplate ev = EVENTS[RNG.nextInt(EVENTS.length)];
                    eventReturn = ev.lo() + RNG.nextDouble() * (ev.hi() - ev.lo());
                    headline = String.format(ev.template(), c.getName());
                }

                double pctChange = sectorShock.get(c.getSector()) + fundamentalDrift + noise + eventReturn;
                double newPrice = Math.max(oldPrice * (1 + pctChange), MIN_PRICE);

                updatePrice.setDouble(1, newPrice);
                updatePrice.setDouble(2, oldPrice);
                updatePrice.setString(3, c.getTicker());
                updatePrice.executeUpdate();

                insertHistory.setInt(1, tickNum);
                insertHistory.setString(2, c.getTicker());
                insertHistory.setDouble(3, newPrice);
                insertHistory.executeUpdate();

                if (headline != null) {
                    insertEvent.setInt(1, tickNum);
                    insertEvent.setString(2, c.getTicker());
                    insertEvent.setString(3, headline);
                    insertEvent.executeUpdate();
                }
            }
        }

        Db.setTick(conn, tickNum);
        conn.commit();
        return tickNum;
    }

    /**
     * side must be "buy" or "sell". Throws IllegalArgumentException on any invalid
     * trade (equivalent of the Python ValueError), with a message safe to show the user.
     */
    public static String executeTrade(Connection conn, String player, String tickerIn, int qty, String sideIn)
            throws SQLException {
        String ticker = tickerIn.toUpperCase();
        String side = sideIn.toLowerCase();

        Player p = Db.getPlayer(conn, player);
        if (p == null) {
            throw new IllegalArgumentException("No player named '" + player + "'.");
        }
        Company c = Db.getCompany(conn, ticker);
        if (c == null) {
            throw new IllegalArgumentException("No company with ticker '" + ticker + "'.");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }

        double price = c.getPrice();
        double cost = price * qty;
        int tickNum = Db.getTick(conn);

        Integer heldQty = null;
        Double heldAvgCost = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT qty, avg_cost FROM holdings WHERE player = ? AND ticker = ?")) {
            ps.setString(1, player);
            ps.setString(2, ticker);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    heldQty = rs.getInt("qty");
                    heldAvgCost = rs.getDouble("avg_cost");
                }
            }
        }

        double newCash;

        if (side.equals("buy")) {
            if (cost > p.getCash()) {
                throw new IllegalArgumentException(String.format(
                        "Not enough cash: need $%,.2f, have $%,.2f.", cost, p.getCash()));
            }
            newCash = p.getCash() - cost;

            if (heldQty != null) {
                int newQty = heldQty + qty;
                double newAvgCost = (heldAvgCost * heldQty + cost) / newQty;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE holdings SET qty = ?, avg_cost = ? WHERE player = ? AND ticker = ?")) {
                    ps.setInt(1, newQty);
                    ps.setDouble(2, newAvgCost);
                    ps.setString(3, player);
                    ps.setString(4, ticker);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO holdings (player, ticker, qty, avg_cost) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, player);
                    ps.setString(2, ticker);
                    ps.setInt(3, qty);
                    ps.setDouble(4, price);
                    ps.executeUpdate();
                }
            }

        } else if (side.equals("sell")) {
            int owned = heldQty != null ? heldQty : 0;
            if (qty > owned) {
                throw new IllegalArgumentException(
                        "You only own " + owned + " shares of " + ticker + ".");
            }
            newCash = p.getCash() + cost;
            int newQty = owned - qty;

            if (newQty == 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM holdings WHERE player = ? AND ticker = ?")) {
                    ps.setString(1, player);
                    ps.setString(2, ticker);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE holdings SET qty = ? WHERE player = ? AND ticker = ?")) {
                    ps.setInt(1, newQty);
                    ps.setString(2, player);
                    ps.setString(3, ticker);
                    ps.executeUpdate();
                }
            }
        } else {
            throw new IllegalArgumentException("side must be 'buy' or 'sell'");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET cash = ? WHERE name = ?")) {
            ps.setDouble(1, newCash);
            ps.setString(2, player);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO transactions (tick, player, ticker, side, qty, price, timestamp) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, tickNum);
            ps.setString(2, player);
            ps.setString(3, ticker);
            ps.setString(4, side);
            ps.setInt(5, qty);
            ps.setDouble(6, price);
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        }

        conn.commit();

        return String.format("%s %d %s @ $%,.2f = $%,.2f", side.toUpperCase(), qty, ticker, price, cost);
    }
}
