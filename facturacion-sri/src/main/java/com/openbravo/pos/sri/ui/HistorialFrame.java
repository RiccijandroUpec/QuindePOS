package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.config.ClassLoaderPropio;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;

/**
 * Ventana con la pantalla {@link PanelComprobantes}. Dentro de EcoPos los
 * comprobantes se muestran integrados (menu Comprobantes electronicos); esta
 * ventana queda para el modo standalone y para menus de versiones anteriores.
 */
public class HistorialFrame extends JFrame {

    public HistorialFrame() {
        super("Comprobantes electr\u00F3nicos");
        ClassLoaderPropio.fijarEnHiloActual();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(new PanelComprobantes());
        setMinimumSize(new Dimension(1100, 640));
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // look and feel por defecto
        }
        SwingUtilities.invokeLater(() -> new HistorialFrame().setVisible(true));
    }
}
