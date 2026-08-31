package model;


/** Mirrors a row of the `companies` table. */
public class Company {
    private String ticker;
    private String name;
    private String sector;
    private double price;
    private double prevPrice;
    private double eps;
    private double revenueGrowth;
    private double volatility;

    public Company(String ticker, String name, String sector, double price, double prevPrice,
                   double eps, double revenueGrowth, double volatility) {
        this.ticker = ticker;
        this.name = name;
        this.sector = sector;
        this.price = price;
        this.prevPrice = prevPrice;
        this.eps = eps;
        this.revenueGrowth = revenueGrowth;
        this.volatility = volatility;
    }

    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getSector() { return sector; }
    public double getPrice() { return price; }
    public double getPrevPrice() { return prevPrice; }
    public double getEps() { return eps; }
    public double getRevenueGrowth() { return revenueGrowth; }
    public double getVolatility() { return volatility; }

    public void setPrice(double price) { this.price = price; }
    public void setPrevPrice(double prevPrice) { this.prevPrice = prevPrice; }
}
