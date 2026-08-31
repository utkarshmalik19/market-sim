package gui;

/** Shared display formatting for money, large numbers, and ratios. */
public final class Formatters {

    private Formatters() {
    }

    public static String money(double v) {
        return String.format("$%,.2f", v);
    }

    public static String marketCap(double cap) {
        if (cap >= 1_000_000_000) return String.format("$%.2fB", cap / 1_000_000_000);
        if (cap >= 1_000_000) return String.format("$%.2fM", cap / 1_000_000);
        if (cap >= 1_000) return String.format("$%.2fK", cap / 1_000);
        return String.format("$%,.0f", cap);
    }

    public static String volume(double vol) {
        if (vol >= 1_000_000) return String.format("%.2fM", vol / 1_000_000);
        if (vol >= 1_000) return String.format("%.1fK", vol / 1_000);
        return String.format("%,.0f", vol);
    }

    /** "N/A" when EPS isn't positive, since P/E is undefined for a loss-making company. */
    public static String peRatio(double eps, double price) {
        return eps > 0 ? String.format("%.2f", price / eps) : "N/A";
    }

    /** fraction is e.g. 0.08 for 8%. */
    public static String percent(double fraction) {
        return String.format("%.2f%%", fraction * 100);
    }

    public static String signedPercent(double fraction) {
        return String.format("%+.2f%%", fraction * 100);
    }

    public static String signedMoney(double v) {
        return String.format("$%+,.2f", v);
    }
}