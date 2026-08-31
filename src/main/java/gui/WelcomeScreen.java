package gui;

import db.Db;
import db.ExcelLoader;
import db.SaveManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** First screen shown on launch: New Game, Load Game, or Exit. */
public class WelcomeScreen extends JFrame {

    private static final Color BG = new Color(0x0F1B2D);
    private static final Color ACCENT = new Color(0x2E7D32);
    private static final Color ACCENT_BLUE = new Color(0x1E5AA8);
    private static final Color TEXT_LIGHT = Color.WHITE;
    private static final Color MUTED = new Color(0xB0BEC5);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    // --- New Game / Add Players state ---
    private JTextField saveNameField;
    private DefaultListModel<String> pendingPlayersModel;
    private JList<String> pendingPlayersList;
    private JButton playBtn;
    private Connection pendingConn;
    private String pendingSaveName;

    // --- Load Game state ---
    private DefaultListModel<String> loadListModel;
    private JList<String> loadList;

    public WelcomeScreen() {
        super("MarketSim");
        setSize(560, 500);
        setMinimumSize(new Dimension(480, 440));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cards.add(buildHomeCard(), "home");
        cards.add(buildNewGameCard(), "new");
        cards.add(buildAddPlayersCard(), "players");
        cards.add(buildLoadGameCard(), "load");

        add(cards);
        cardLayout.show(cards, "home");
    }

    /** Lets MarketSimGUI jump straight to a specific card when returning to the menu. */
    public void goToNewGame() {
        resetNewGameCard();
        cardLayout.show(cards, "new");
    }

    public void goToLoadGame() {
        refreshLoadGameCard();
        cardLayout.show(cards, "load");
    }

    // ---------------------------------------------------------
    // HOME
    // ---------------------------------------------------------

    private JPanel buildHomeCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));

        JLabel title = new JLabel("MarketSim");
        title.setFont(new Font("SansSerif", Font.BOLD, 36));
        title.setForeground(TEXT_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("A two-player stock market simulation");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(4, 0, 40, 0));

        JButton newGameBtn = bigButton("New Game", ACCENT);
        newGameBtn.addActionListener(e -> goToNewGame());

        boolean hasSaves = !playableSaves().isEmpty();
        JButton loadGameBtn = bigButton("Load Game", ACCENT_BLUE);
        loadGameBtn.setEnabled(hasSaves);
        loadGameBtn.setToolTipText(hasSaves ? null : "No saved games yet — start a New Game first.");
        loadGameBtn.addActionListener(e -> goToLoadGame());

        JButton exitBtn = bigButton("Exit", new Color(0x455A64));
        exitBtn.addActionListener(e -> System.exit(0));

        panel.add(title);
        panel.add(subtitle);
        panel.add(newGameBtn);
        panel.add(spacer(12));
        panel.add(loadGameBtn);
        panel.add(spacer(12));
        panel.add(exitBtn);

        return wrapCentered(panel);
    }

    // ---------------------------------------------------------
    // NEW GAME (name the save)
    // ---------------------------------------------------------

    private JPanel buildNewGameCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));

        JLabel title = new JLabel("Name your game");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        saveNameField = new JTextField();
        saveNameField.setMaximumSize(new Dimension(280, 34));
        saveNameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveNameField.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JButton continueBtn = bigButton("Continue", ACCENT);
        continueBtn.addActionListener(e -> onCreateNewGame());

        JButton backBtn = bigButton("Back", new Color(0x455A64));
        backBtn.addActionListener(e -> cardLayout.show(cards, "home"));

        panel.add(title);
        panel.add(spacer(20));
        panel.add(saveNameField);
        panel.add(spacer(20));
        panel.add(continueBtn);
        panel.add(spacer(10));
        panel.add(backBtn);

        return wrapCentered(panel);
    }

    private void resetNewGameCard() {
        saveNameField.setText("Game " + (SaveManager.listSaves().size() + 1));
    }

    private void onCreateNewGame() {
        String name = SaveManager.sanitize(saveNameField.getText());

        if (SaveManager.exists(name)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "A save named '" + name + "' already exists. Overwrite it?",
                    "Save Exists", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            SaveManager.delete(name);
        }

        try {
            Connection conn = Db.connect(name);
            Db.initSchema(conn);
            ExcelLoader.loadCompaniesFromResource(conn, true);
            Db.setStartDate(conn, LocalDate.now());
            Db.setTick(conn, 0);
            conn.commit();

            pendingConn = conn;
            pendingSaveName = name;
            pendingPlayersModel.clear();
            playBtn.setEnabled(false);
            cardLayout.show(cards, "players");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not create game: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------
    // ADD PLAYERS (required before a new game can start)
    // ---------------------------------------------------------

    private JPanel buildAddPlayersCard() {
        pendingPlayersModel = new DefaultListModel<>();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(40, 60, 40, 60));

        JLabel title = new JLabel("Add players");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("You need at least one player to start.");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 13));
        hint.setForeground(MUTED);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setBorder(new EmptyBorder(4, 0, 20, 0));

        pendingPlayersList = new JList<>(pendingPlayersModel);
        pendingPlayersList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(pendingPlayersList);
        scroll.setMaximumSize(new Dimension(300, 110));
        scroll.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton addBtn = bigButton("+ Add Player", ACCENT_BLUE);
        addBtn.addActionListener(e -> onAddPendingPlayer());

        playBtn = bigButton("Start Playing", ACCENT);
        playBtn.setEnabled(false);
        playBtn.addActionListener(e -> onStartPlaying());

        JButton cancelBtn = bigButton("Cancel", new Color(0x455A64));
        cancelBtn.addActionListener(e -> onCancelNewGame());

        panel.add(title);
        panel.add(hint);
        panel.add(scroll);
        panel.add(spacer(16));
        panel.add(addBtn);
        panel.add(spacer(10));
        panel.add(playBtn);
        panel.add(spacer(10));
        panel.add(cancelBtn);

        return wrapCentered(panel);
    }

    private void onAddPendingPlayer() {
        String name = JOptionPane.showInputDialog(this, "Player name:");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        name = name.trim();
        String cashStr = JOptionPane.showInputDialog(this, "Starting cash:", "100000");
        if (cashStr == null) {
            return;
        }
        double cash;
        try {
            cash = Double.parseDouble(cashStr.trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cash must be a number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            if (Db.getPlayer(pendingConn, name) != null) {
                JOptionPane.showMessageDialog(this, "Player '" + name + "' already exists.");
                return;
            }
            try (PreparedStatement ps = pendingConn.prepareStatement(
                    "INSERT INTO players (name, cash) VALUES (?, ?)")) {
                ps.setString(1, name);
                ps.setDouble(2, cash);
                ps.executeUpdate();
            }
            pendingConn.commit();
            pendingPlayersModel.addElement(name + "   —   $" + String.format("%,.2f", cash));
            playBtn.setEnabled(true);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCancelNewGame() {
        try {
            if (pendingConn != null) {
                pendingConn.close();
            }
        } catch (SQLException ignored) {
        }
        if (pendingSaveName != null) {
            SaveManager.delete(pendingSaveName);
        }
        pendingConn = null;
        pendingSaveName = null;
        cardLayout.show(cards, "home");
    }

    private void onStartPlaying() {
        launchGame(pendingConn, pendingSaveName);
    }

    // ---------------------------------------------------------
    // LOAD GAME
    // ---------------------------------------------------------

    private JPanel buildLoadGameCard() {
        loadListModel = new DefaultListModel<>();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(40, 60, 40, 60));

        JLabel title = new JLabel("Load a saved game");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_LIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 0, 20, 0));

        loadList = new JList<>(loadListModel);
        loadList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(loadList);
        scroll.setMaximumSize(new Dimension(320, 150));
        scroll.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton loadBtn = bigButton("Load Selected", ACCENT);
        loadBtn.addActionListener(e -> onLoadSelected());

        JButton deleteBtn = bigButton("Delete Selected", new Color(0xB71C1C));
        deleteBtn.addActionListener(e -> onDeleteSelected());

        JButton backBtn = bigButton("Back", new Color(0x455A64));
        backBtn.addActionListener(e -> cardLayout.show(cards, "home"));

        panel.add(title);
        panel.add(scroll);
        panel.add(spacer(16));
        panel.add(loadBtn);
        panel.add(spacer(10));
        panel.add(deleteBtn);
        panel.add(spacer(10));
        panel.add(backBtn);

        return wrapCentered(panel);
    }

    private void refreshLoadGameCard() {
        loadListModel.clear();
        for (String name : playableSaves()) {
            loadListModel.addElement(name);
        }
    }

    /** A save with zero players isn't worth loading, so it's filtered out of the list. */
    private List<String> playableSaves() {
        List<String> result = new ArrayList<>();
        for (String name : SaveManager.listSaves()) {
            try (Connection conn = Db.connect(name)) {
                Db.initSchema(conn);
                if (Db.countPlayers(conn) > 0) {
                    result.add(name);
                }
            } catch (Exception ignored) {
                // Corrupt/unreadable save file — skip it rather than crash the menu.
            }
        }
        return result;
    }

    private void onLoadSelected() {
        String name = loadList.getSelectedValue();
        if (name == null) {
            JOptionPane.showMessageDialog(this, "Select a save first.");
            return;
        }
        try {
            Connection conn = Db.connect(name);
            Db.initSchema(conn);
            if (Db.getStartDate(conn) == null) {
                Db.setStartDate(conn, LocalDate.now());
                conn.commit();
            }
            launchGame(conn, name);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load game: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeleteSelected() {
        String name = loadList.getSelectedValue();
        if (name == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Permanently delete save '" + name + "'? This cannot be undone.",
                "Delete Save", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            SaveManager.delete(name);
            refreshLoadGameCard();
        }
    }

    // ---------------------------------------------------------
    // SHARED HELPERS
    // ---------------------------------------------------------

    private void launchGame(Connection conn, String saveName) {
        try {
            MarketSimGUI gui = new MarketSimGUI(conn, saveName);
            gui.setLocationRelativeTo(null);
            gui.setVisible(true);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to start game: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JButton bigButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(color);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(14, 0, 14, 0));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(260, 50));
        btn.setOpaque(true);
        return btn;
    }

    private Component spacer(int h) {
        return Box.createRigidArea(new Dimension(0, h));
    }

    private JPanel wrapCentered(JPanel inner) {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG);
        outer.add(inner);
        return outer;
    }
}