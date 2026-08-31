package model;

/** Mirrors a row of the `players` table. */
public class Player {
    private final String name;
    private double cash;

    public Player(String name, double cash) {
        this.name = name;
        this.cash = cash;
    }

    public String getName() { return name; }
    public double getCash() { return cash; }
    public void setCash(double cash) { this.cash = cash; }
}

