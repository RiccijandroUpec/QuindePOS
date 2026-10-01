package com.openbravo.pos.sri;

import com.openbravo.data.loader.Session;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 * "Chip" de estado de la facturacion SRI para la barra superior de EcoPos:
 * gris = apagada (interruptor "SRI: NO"), verde = al dia, ambar = enviando,
 * rojo = hay comprobantes con error/rechazados que alguien debe revisar en
 * el Historial. Se actualiza cada 30 s con una consulta liviana a la tabla
 * del conector. Si el conector no esta instalado, no se muestra.
 */
public final class IndicadorSri extends JLabel {

    private static final int INTERVALO_MS = 30000;

    private static final Color[] GRIS = {new Color(0xECEFF1), new Color(0x546E7A)};
    private static final Color[] VERDE = {new Color(0xE8F5E9), new Color(0x1B5E3F)};
    private static final Color[] AMBAR = {new Color(0xFFF8E1), new Color(0xB26A00)};
    private static final Color[] ROJO = {new Color(0xFFEBEE), new Color(0xC62828)};

    private static final String CONSULTA =
            "SELECT "
            + " SUM(CASE WHEN estado = 'ERROR' OR (estado = 'RECHAZADO' AND fecha_actualizacion >= NOW() - INTERVAL 1 DAY)"
            + "  OR (estado IN ('PENDIENTE','ENVIADO') AND fecha_creacion < NOW() - INTERVAL 1 HOUR) THEN 1 ELSE 0 END),"
            + " SUM(CASE WHEN estado IN ('PENDIENTE','ENVIADO') AND fecha_creacion >= NOW() - INTERVAL 1 HOUR THEN 1 ELSE 0 END)"
            + " FROM ecopos_sri_comprobantes";

    private final Session session;
    private final File carpetaConector;
    private Color fondo = GRIS[0];

    public IndicadorSri(Session session) {
        this.session = session;
        this.carpetaConector = new File(new File(System.getProperty("dirname.path", "./")), "sri-conector");
        setOpaque(false);
        setFont(getFont().deriveFont(java.awt.Font.BOLD, 12f));
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        actualizar();
        new Timer(INTERVALO_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actualizar();
            }
        }).start();
    }

    private void actualizar() {
        if (!new File(carpetaConector, "ecopos-sri-connector.jar").exists()) {
            setVisible(false);
            return;
        }
        setVisible(true);
        String problema = EcoPosSriGlue.getProblema();
        if (problema != null) {
            mostrar(ROJO, "Facturaci\u00F3n: actualizar m\u00F3dulo", problema);
            return;
        }
        if (!facturacionActiva()) {
            mostrar(GRIS, "Facturaci\u00F3n: apagada", "La facturaci\u00F3n electr\u00F3nica est\u00E1 apagada. Act\u00EDvala en Configuraci\u00F3n \u2192 Facturaci\u00F3n electr\u00F3nica");
            return;
        }
        try {
            Connection con = session.getConnection();
            try (PreparedStatement ps = con.prepareStatement(CONSULTA);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                int problemas = rs.getInt(1);
                int enviando = rs.getInt(2);
                if (problemas > 0) {
                    mostrar(ROJO, "Facturaci\u00F3n: " + problemas + " por revisar",
                            "Hay comprobantes con error o rechazados. Toca para revisarlos.");
                } else if (enviando > 0) {
                    mostrar(AMBAR, "Facturaci\u00F3n: enviando " + enviando, "Comprobantes en camino al SRI. Toca para verlos.");
                } else {
                    mostrar(VERDE, "Facturaci\u00F3n al d\u00EDa", "Todas las facturas est\u00E1n autorizadas. Toca para verlas.");
                }
            }
        } catch (Exception e) {
            mostrar(GRIS, "Facturaci\u00F3n: sin datos", "No se pudo leer el estado de la facturaci\u00F3n: " + e.getMessage());
        }
    }

    private boolean facturacionActiva() {
        File archivo = new File(carpetaConector, "facturacion-global.properties");
        if (!archivo.exists()) {
            return false;
        }
        Properties estado = new Properties();
        try (FileReader lector = new FileReader(archivo)) {
            estado.load(lector);
        } catch (Exception e) {
            return false;
        }
        return "true".equals(estado.getProperty("activo", "false"));
    }

    private void mostrar(Color[] colores, String texto, String ayuda) {
        fondo = colores[0];
        setForeground(colores[1]);
        setText("\u25CF  " + texto);
        setToolTipText(ayuda);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fondo);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
