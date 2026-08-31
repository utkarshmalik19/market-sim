package model;

/** Mirrors a row of the `events` table. */
public class EventRow {
    private final int tick;
    private final String ticker;
    private final String headline;

    public EventRow(int tick, String ticker, String headline) {
        this.tick = tick;
        this.ticker = ticker;
        this.headline = headline;
    }

    public int getTick() { return tick; }
    public String getTicker() { return ticker; }
    public String getHeadline() { return headline; }
}
