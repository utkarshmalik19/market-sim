package db;

import model.Company;
import model.EventRow;
import model.HoldingView;
import model.Player;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** SQLite persistence layer. Each save is its own database file (see SaveManager). */
public class Db {

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS companies (" +
                    "ticker              TEXT PRIMARY KEY," +
                    "name                TEXT NOT NULL," +
                    "sector              TEXT NOT NULL," +
                    "price               REAL NOT NULL," +
                    "prev_price          REAL NOT NULL," +
                    "eps                 REAL NOT NULL," +
                    "revenue_growth      REAL NOT NULL," +
                    "volatility          REAL NOT NULL," +
                    "shares_outstanding  REAL NOT NULL DEFAULT 0," +
                    "avg_volume          REAL NOT NULL DEFAULT 0," +
                    "volume              REAL NOT NULL DEFAULT 0," +
                    "dividend_per_share  REAL NOT NULL DEFAULT 0" +
                    ")",
            "CREATE TABLE IF NOT EXISTS players (" +
                    "name    TEXT PRIMARY KEY," +
                    "cash    REAL NOT NULL" +
                    ")",
            "CREATE TABLE IF NOT EXISTS holdings (" +
                    "player      TEXT NOT NULL," +
                    "ticker      TEXT NOT NULL," +
                    "qty         INTEGER NOT NULL," +
                    "avg_cost    REAL NOT NULL," +
                    "PRIMARY KEY (player, ticker)" +
                    ")",
            "CREATE TABLE IF NOT EXISTS transactions (" +
                    "id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "tick        INTEGER NOT NULL," +
                    "player      TEXT NOT NULL," +
                    "ticker      TEXT NOT NULL," +
                    "side        TEXT NOT NULL," +
                    "qty         INTEGER NOT NULL," +
                    "price       REAL NOT NULL," +
                    "timestamp   TEXT NOT NULL" +
                    ")",
            "CREATE TABLE IF NOT EXISTS price_history (" +
                    "tick    INTEGER NOT NULL," +
                    "ticker  TEXT NOT NULL," +
                    "price   REAL NOT NULL" +
                    ")",
            "CREATE TABLE IF NOT EXISTS events (" +
                    "id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "tick        INTEGER NOT NULL," +
                    "ticker      TEXT NOT NULL," +
                    "headline    TEXT NOT NULL" +
                    ")",
            "CREATE TABLE IF NOT EXISTS game_state (" +
                    "key     TEXT PRIMARY KEY," +
                    "value   TEXT NOT NULL" +
                    ")",
            "CREATE TABLE IF NOT EXISTS networth_history (" +
                    "tick        INTEGER NOT NULL," +
                    "player      TEXT NOT NULL," +
                    "net_worth   REAL NOT NULL" +
                    ")",
    };

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("sqlite-jdbc driver not found on classpath", e);
        }
    }

    public static Connection connect(String saveName) throws SQLException {
        File dbFile = SaveManager.fileFor(saveName);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    public static void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
        }

        Set<String> columns = new LinkedHashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(companies)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }

        // Migrations for databases created before these columns existed.
        try (Statement st = conn.createStatement()) {
            if (!columns.contains("prev_price")) {
                st.execute("ALTER TABLE companies ADD COLUMN prev_price REAL NOT NULL DEFAULT 0");
                st.execute("UPDATE companies SET prev_price = price WHERE prev_price = 0");
            }
            if (!columns.contains("shares_outstanding")) {
                st.execute("ALTER TABLE companies ADD COLUMN shares_outstanding REAL NOT NULL DEFAULT 0");
            }
            if (!columns.contains("avg_volume")) {
                st.execute("ALTER TABLE companies ADD COLUMN avg_volume REAL NOT NULL DEFAULT 0");
            }
            if (!columns.contains("volume")) {
                st.execute("ALTER TABLE companies ADD COLUMN volume REAL NOT NULL DEFAULT 0");
                st.execute("UPDATE companies SET volume = avg_volume WHERE volume = 0");
            }
            if (!columns.contains("dividend_per_share")) {
                st.execute("ALTER TABLE companies ADD COLUMN dividend_per_share REAL NOT NULL DEFAULT 0");
            }
        }

        conn.commit();
    }

    public static int getTick(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM game_state WHERE key = 'tick'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
            return 0;
        }
    }

    public static void setTick(Connection conn, int tick) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO game_state (key, value) VALUES ('tick', ?) " +
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, String.valueOf(tick));
            ps.executeUpdate();
        }
    }

    public static LocalDate getStartDate(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM game_state WHERE key = 'start_date'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return LocalDate.parse(rs.getString("value"));
            }
            return null;
        }
    }

    public static void setStartDate(Connection conn, LocalDate date) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO game_state (key, value) VALUES ('start_date', ?) " +
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, date.toString());
            ps.executeUpdate();
        }
    }

    public static LocalDate getCurrentDate(Connection conn) throws SQLException {
        LocalDate start = getStartDate(conn);
        if (start == null) {
            start = LocalDate.now();
        }
        return start.plusDays(getTick(conn));
    }

    public static int countPlayers(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS n FROM players")) {
            return rs.next() ? rs.getInt("n") : 0;
        }
    }

    public static Company getCompany(Connection conn, String ticker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM companies WHERE ticker = ?")) {
            ps.setString(1, ticker.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapCompany(rs) : null;
            }
        }
    }

    public static Player getPlayer(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM players WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Player(rs.getString("name"), rs.getDouble("cash")) : null;
            }
        }
    }

    public static List<Company> getAllCompanies(Connection conn) throws SQLException {
        List<Company> result = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM companies ORDER BY sector, ticker")) {
            while (rs.next()) {
                result.add(mapCompany(rs));
            }
        }
        return result;
    }

    public static List<EventRow> getRecentEvents(Connection conn, int limit) throws SQLException {
        List<EventRow> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tick, ticker, headline FROM events ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new EventRow(rs.getInt("tick"), rs.getString("ticker"),
                            rs.getString("headline")));
                }
            }
        }
        return result;
    }

    public static List<HoldingView> getPlayerHoldings(Connection conn, String player) throws SQLException {
        List<HoldingView> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT h.ticker, h.qty, h.avg_cost, c.name, c.sector, c.price, c.prev_price " +
                        "FROM holdings h JOIN companies c ON h.ticker = c.ticker " +
                        "WHERE h.player = ? ORDER BY h.ticker")) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new HoldingView(
                            rs.getString("ticker"), rs.getInt("qty"), rs.getDouble("avg_cost"),
                            rs.getString("name"), rs.getString("sector"),
                            rs.getDouble("price"), rs.getDouble("prev_price")));
                }
            }
        }
        return result;
    }

    public static Double getPlayerNetWorth(Connection conn, String player) throws SQLException {
        Player p = getPlayer(conn, player);
        if (p == null) {
            return null;
        }
        double holdingsValue = 0;
        for (HoldingView h : getPlayerHoldings(conn, player)) {
            holdingsValue += h.getQty() * h.getPrice();
        }
        return p.getCash() + holdingsValue;
    }

    /** Snapshots every player's current net worth against a given tick, for the portfolio chart. */
    public static void recordNetWorthHistory(Connection conn, int tick) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM players")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO networth_history (tick, player, net_worth) VALUES (?, ?, ?)")) {
            for (String name : names) {
                Double nw = getPlayerNetWorth(conn, name);
                if (nw == null) {
                    continue;
                }
                ps.setInt(1, tick);
                ps.setString(2, name);
                ps.setDouble(3, nw);
                ps.executeUpdate();
            }
        }
    }

    /** Each element is {tick, netWorth}, ordered oldest to newest. */
    public static List<double[]> getNetWorthHistory(Connection conn, String player) throws SQLException {
        List<double[]> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tick, net_worth FROM networth_history WHERE player = ? ORDER BY tick")) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new double[]{rs.getInt("tick"), rs.getDouble("net_worth")});
                }
            }
        }
        return result;
    }

    /** Each element is {tick, price}, ordered oldest to newest. */
    public static List<double[]> getPriceHistory(Connection conn, String ticker) throws SQLException {
        List<double[]> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tick, price FROM price_history WHERE ticker = ? ORDER BY tick")) {
            ps.setString(1, ticker);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new double[]{rs.getInt("tick"), rs.getDouble("price")});
                }
            }
        }
        return result;
    }

    private static Company mapCompany(ResultSet rs) throws SQLException {
        return new Company(
                rs.getString("ticker"), rs.getString("name"), rs.getString("sector"),
                rs.getDouble("price"), rs.getDouble("prev_price"), rs.getDouble("eps"),
                rs.getDouble("revenue_growth"), rs.getDouble("volatility"),
                rs.getDouble("shares_outstanding"), rs.getDouble("avg_volume"),
                rs.getDouble("volume"), rs.getDouble("dividend_per_share"));
    }
}