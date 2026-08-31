package model;

/** Mirrors the joined holdings+companies row returned by Db.getPlayerHoldings(). */
public class HoldingView {
    private final String ticker;
    private final int qty;
    private final double avgCost;
    private final String name;
    private final String sector;
    private final double price;
    private final double prevPrice;

    public HoldingView(String ticker, int qty, double avgCost, String name, String sector,
                       double price, double prevPrice) {
        this.ticker = ticker;
        this.qty = qty;
        this.avgCost = avgCost;
        this.name = name;
        this.sector = sector;
        this.price = price;
        this.prevPrice = prevPrice;
    }

    public String getTicker() { return ticker; }
    public int getQty() { return qty; }
    public double getAvgCost() { return avgCost; }
    public String getName() { return name; }
    public String getSector() { return sector; }
    public double getPrice() { return price; }
    public double getPrevPrice() { return prevPrice; }
}
