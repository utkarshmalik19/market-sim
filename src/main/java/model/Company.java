package model;

/** Mirrors a row of the `companies` table, plus computed metrics for the dashboard. */
public class Company {
    private String ticker;
    private String name;
    private String sector;
    private double price;
    private double prevPrice;
    private double eps;
    private double revenueGrowth;
    private double volatility;
    private double sharesOutstanding;
    private double avgVolume;
    private double volume;
    private double dividendPerShare;

    public Company(String ticker, String name, String sector, double price, double prevPrice,
                   double eps, double revenueGrowth, double volatility,
                   double sharesOutstanding, double avgVolume, double volume, double dividendPerShare) {
        this.ticker = ticker;
        this.name = name;
        this.sector = sector;
        this.price = price;
        this.prevPrice = prevPrice;
        this.eps = eps;
        this.revenueGrowth = revenueGrowth;
        this.volatility = volatility;
        this.sharesOutstanding = sharesOutstanding;
        this.avgVolume = avgVolume;
        this.volume = volume;
        this.dividendPerShare = dividendPerShare;
    }

    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getSector() { return sector; }
    public double getPrice() { return price; }
    public double getPrevPrice() { return prevPrice; }
    public double getEps() { return eps; }
    public double getRevenueGrowth() { return revenueGrowth; }
    public double getVolatility() { return volatility; }
    public double getSharesOutstanding() { return sharesOutstanding; }
    public double getAvgVolume() { return avgVolume; }
    public double getVolume() { return volume; }
    public double getDividendPerShare() { return dividendPerShare; }

    public void setPrice(double price) { this.price = price; }
    public void setPrevPrice(double prevPrice) { this.prevPrice = prevPrice; }
    public void setVolume(double volume) { this.volume = volume; }

    /** Price * shares outstanding. */
    public double getMarketCap() {
        return price * sharesOutstanding;
    }

    /** Price / EPS. Undefined (NaN) when EPS isn't positive — callers should check getEps() > 0. */
    public double getPeRatio() {
        return eps > 0 ? price / eps : Double.NaN;
    }

    /** Annual dividend per share / price, as a fraction (e.g. 0.02 = 2%). */
    public double getDividendYield() {
        return price > 0 ? dividendPerShare / price : 0;
    }
}