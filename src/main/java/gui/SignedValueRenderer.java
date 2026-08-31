package gui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** Colors gain/loss-looking cell text green/red — used for Day $, Day %, and P&L columns. */
public class SignedValueRenderer extends DefaultTableCellRenderer {

    private static final Color POSITIVE = new Color(0x1E8E3E);
    private static final Color NEGATIVE = new Color(0xC5221F);
    private static final Color NEUTRAL = new Color(0x333333);

    public SignedValueRenderer() {
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int col) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
        if (!isSelected) {
            String text = value == null ? "" : value.toString();
            String stripped = text.startsWith("$") ? text.substring(1) : text;
            if (stripped.startsWith("+")) {
                c.setForeground(POSITIVE);
            } else if (stripped.startsWith("-")) {
                c.setForeground(NEGATIVE);
            } else {
                c.setForeground(NEUTRAL);
            }
        }
        return c;
    }
}