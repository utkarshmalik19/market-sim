package db;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads companies from the bundled companies.xlsx resource into the database.
 *
 * Expected header row in the "Companies" sheet:
 * Ticker, Company, Sector, StartingPrice, EPS, RevenueGrowth, Volatility,
 * SharesOutstanding, AvgVolume, DividendPerShare
 */
public class ExcelLoader {

    private static final String RESOURCE_NAME = "/companies.xlsx";

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "Ticker", "Company", "Sector", "StartingPrice", "EPS", "RevenueGrowth", "Volatility",
            "SharesOutstanding", "AvgVolume", "DividendPerShare");

    public static int loadCompaniesFromResource(Connection conn, boolean reset) throws Exception {
        try (InputStream in = ExcelLoader.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IOException("Bundled resource " + RESOURCE_NAME + " not found. " +
                        "Make sure companies.xlsx is in src/main/resources and you rebuilt with 'mvn package'.");
            }
            try (Workbook workbook = WorkbookFactory.create(in)) {
                return loadFromWorkbook(conn, workbook, reset);
            }
        }
    }

    private static int loadFromWorkbook(Connection conn, Workbook workbook, boolean reset) throws Exception {
        Sheet sheet = workbook.getSheet("Companies");
        if (sheet == null) {
            throw new IllegalArgumentException("Excel file has no 'Companies' sheet.");
        }

        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new IllegalArgumentException("Companies sheet is empty.");
        }

        Map<String, Integer> colIndex = new HashMap<>();
        for (Cell cell : headerRow) {
            colIndex.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
        }

        List<String> missing = new ArrayList<>();
        for (String col : REQUIRED_COLUMNS) {
            if (!colIndex.containsKey(col)) {
                missing.add(col);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Excel is missing columns: " + missing);
        }

        Db.initSchema(conn);

        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM companies");
            st.execute("DELETE FROM price_history");
            st.execute("DELETE FROM events");
        }
        Db.setTick(conn, 0);

        if (reset) {
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM players");
                st.execute("DELETE FROM holdings");
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM networth_history");
            }
        }

        DataFormatter formatter = new DataFormatter();
        int loaded = 0;

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO companies (ticker, name, sector, price, prev_price, eps, " +
                        "revenue_growth, volatility, shares_outstanding, avg_volume, volume, dividend_per_share) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankCell(row, colIndex.get("Ticker"))) {
                    continue; // skip blank rows
                }

                String ticker = cellString(formatter, row, colIndex.get("Ticker")).toUpperCase();
                String name = cellString(formatter, row, colIndex.get("Company"));
                String sector = cellString(formatter, row, colIndex.get("Sector"));
                double startingPrice = numericValue(row, colIndex.get("StartingPrice"));
                double eps = numericValue(row, colIndex.get("EPS"));
                double revenueGrowth = numericValue(row, colIndex.get("RevenueGrowth"));
                double volatility = numericValue(row, colIndex.get("Volatility"));
                double sharesOutstanding = numericValue(row, colIndex.get("SharesOutstanding"));
                double avgVolume = numericValue(row, colIndex.get("AvgVolume"));
                double dividendPerShare = numericValue(row, colIndex.get("DividendPerShare"));

                ps.setString(1, ticker);
                ps.setString(2, name);
                ps.setString(3, sector);
                ps.setDouble(4, startingPrice);
                ps.setDouble(5, startingPrice);
                ps.setDouble(6, eps);
                ps.setDouble(7, revenueGrowth);
                ps.setDouble(8, volatility);
                ps.setDouble(9, sharesOutstanding);
                ps.setDouble(10, avgVolume);
                ps.setDouble(11, avgVolume); // day-1 volume starts at the baseline
                ps.setDouble(12, dividendPerShare);
                ps.executeUpdate();
                loaded++;
            }
        }

        conn.commit();
        return loaded;
    }

    private static String cellString(DataFormatter formatter, Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static boolean isBlankCell(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        return cell == null || cell.getCellType() == CellType.BLANK;
    }

    private static double numericValue(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        String s = cell.getStringCellValue().trim();
        return s.isEmpty() ? 0.0 : Double.parseDouble(s);
    }
}