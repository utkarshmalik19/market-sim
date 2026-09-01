# MarketSim

A multi-player offline stock market simulation game, built as a desktop Java Swing app. Play head-to-head against a friend, buy and sell shares in a simulated market driven by sector trends, company fundamentals, and random news events, and track your portfolio's growth over time.

---

## Features

- **Turn-free trading** — both players can buy/sell anytime; the market only moves when someone advances the day.
- **Simulated market** — prices move based on sector-wide shocks, company fundamentals (EPS, revenue growth), volatility, and random news events (earnings beats/misses, acquisition rumors, regulatory scrutiny, etc.).
- **Real metrics** — Market Cap, Volume, P/E Ratio, and Dividend Yield, computed live from price and fundamentals.
- **Expandable stock details** — double-click any stock (or hit "View Full Details") to see a dedicated screen with its full metrics and a price history chart.
- **Portfolio tracking** — cash, holdings, P&L, and a net worth chart over time, all in a modern card-based layout.
- **News feed** — a scrollable, news-app-style feed of every headline that's moved the market.
- **Leaderboard** — ranks both players by net worth.
- **Multiple saves** — each game is its own save file; start as many independent games as you like, or load one back up later.
- **In-game calendar** — the market advances one real calendar day per "Next Day," starting from the day you create the save.

---

## Tech Stack

- **Java 21** (Swing for the UI — no external UI framework)
- **SQLite** via [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) — each save is its own `.db` file
- **Apache POI** — reads company data from a bundled `.xlsx` file
- **Maven** — build and dependency management

No other dependencies. Charts are hand-drawn with `Graphics2D` — no charting library required.

---

## Getting Started

### Prerequisites

- JDK 21 or newer
- Maven

### Build

```bash
mvn clean package
```

This produces a single runnable jar at `target/marketsim.jar` (dependencies are bundled in via the Maven Shade plugin).

### Run

```bash
java -jar target/marketsim.jar
```

A `saves/` folder is created next to the jar the first time you run it — every game you create lives there as its own `.db` file.

---

## How to Play

1. **New Game** — name your save, then add at least one player (name + starting cash). You need at least one player before you can start playing.
2. **Load Game** — pick from your existing saves. Only saves that already have players show up here.
3. **Trading** — select a player from the dropdown, click a stock in the Market tab, set a quantity, and hit BUY or SELL.
4. **Next Day** — advances the market by one day. Prices move, some stocks generate news, and you'll see a summary popup of what happened.
5. **Portfolio / Leaderboard / News** — track your standing, your net worth over time, and everything that's happened in the market so far.

You can return to the main menu anytime via **Game → New Game / Load Game**, or add another player mid-game via **Player → Add Player**.

---

## Customizing the Market

Company data comes from `src/main/resources/companies.xlsx`, on a sheet named `Companies`. To add, remove, or tweak companies, edit that sheet and rebuild with `mvn package` (since it's bundled into the jar, editing the file alone isn't enough — you need to repackage).

### Required columns

| Column | Meaning |
|---|---|
| `Ticker` | Short unique code, e.g. `AAPL` |
| `Company` | Full company name |
| `Sector` | Companies in the same sector move together each day |
| `StartingPrice` | Price per share at game start ($) |
| `EPS` | Earnings per share — drives price drift and P/E ratio |
| `RevenueGrowth` | Fraction, e.g. `0.08` = 8% — drives price drift |
| `Volatility` | Fraction, e.g. `0.02` = 2% typical daily swing |
| `SharesOutstanding` | Used to compute Market Cap (`price × shares`) |
| `AvgVolume` | Baseline daily trading volume; actual volume fluctuates around this each day |
| `DividendPerShare` | Annual dividend in dollars; Dividend Yield is computed as `dividend / price` |

An existing save already loaded won't pick up Excel changes automatically — use the in-game reload if your build includes it, or start a new save.


---

## How the Price Engine Works

Each "Next Day," every company's price moves based on four combined factors:

1. **Sector shock** — a random move shared by every company in that sector, so related companies tend to drift together.
2. **Fundamental drift** — a small persistent bias from EPS and revenue growth.
3. **Random noise** — scaled by the company's `Volatility`.
4. **News events** (~8% chance per company per day) — a bigger one-off jump paired with a headline (earnings beat/miss, acquisition rumor, regulatory scrutiny, etc.), shown in the Next Day summary and the News tab.

Trading volume fluctuates around each company's `AvgVolume` baseline, with heavier volume on days that have news.

---

## Known Limitations / Ideas for Extension

- No networked multiplayer — both players use the same save file, typically on the same machine.
- No short selling or margin trading.
- No M&A events (one company acquiring another) — a natural next feature given the two-player IB/trading angle this project started from.
- Player net worth history only starts recording from the first "Next Day" — there's no day-0 baseline point on the chart.

---

## License

*(Add your preferred license here — MIT is a common choice for hobby projects.)*
