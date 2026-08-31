import gui.WelcomeScreen;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WelcomeScreen welcome = new WelcomeScreen();
            welcome.setVisible(true);
        });
    }
}