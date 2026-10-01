package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.config.ClassLoaderPropio;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;

/**
 * Ventana con la pantalla {@link PanelFacturacion}. Dentro de EcoPos la
 * configuracion se muestra integrada (menu Facturacion electronica); esta
 * ventana queda para el modo standalone y para menus de versiones anteriores.
 */
public class ConfiguracionFrame extends JFrame {

    public ConfiguracionFrame() {
        super("Facturaci\u00F3n electr\u00F3nica");
        ClassLoaderPropio.fijarEnHiloActual();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(new PanelFacturacion());
        setMinimumSize(new Dimension(760, 640));
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // look and feel por defecto
        }
        SwingUtilities.invokeLater(() -> new ConfiguracionFrame().setVisible(true));
    }
}
