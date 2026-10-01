package com.openbravo.pos.asistente;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Caja e impuestos: revisa que las tarifas de IVA sean las del Ecuador (una tarifa que el SRI
 * no tiene hace que rechace las facturas) y activa el arqueo ciego y la copia de seguridad.
 */
final class PasoCaja extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoCaja.class.getName());
    /** Tarifas de IVA con codigo en el SRI (fracciones). */
    private static final double[] TARIFAS_SRI = {0, 0.05, 0.12, 0.13, 0.14, 0.15};

    private final JPanel impuestos = Ui.columna();
    private final JCheckBox arqueo = new JCheckBox("Arqueo al cerrar caja: el cajero cuenta el efectivo sin ver cu\u00E1nto deber\u00EDa haber");
    private final JCheckBox respaldo = new JCheckBox("Copia de seguridad autom\u00E1tica cada d\u00EDa");
    private final List<String[]> aCorregir = new ArrayList<>();

    PasoCaja(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "caja";
    }

    @Override
    String nombre() {
        return "Caja e impuestos";
    }

    @Override
    String titulo() {
        return "Caja e impuestos";
    }

    @Override
    String descripcion() {
        return "Revisamos que el IVA est\u00E9 bien para el SRI y c\u00F3mo quieres cerrar la caja.";
    }

    @Override
    protected JComponent crearPanel() {
        arqueo.setSelected(!"false".equalsIgnoreCase(ctx.props.getProperty("caja.arqueo")));
        respaldo.setSelected(!"false".equalsIgnoreCase(ctx.props.getProperty("backup.enabled")));
        JPanel col = Ui.columna();
        col.add(Ui.subtitulo("Tarifas de IVA"));
        col.add(impuestos);
        col.add(Ui.subtitulo("Caja"));
        for (JCheckBox cb : new JCheckBox[]{arqueo, respaldo}) {
            cb.setOpaque(false);
            cb.setFont(cb.getFont().deriveFont(15f));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(cb);
        }
        col.add(Ui.nota("La copia de seguridad se guarda en tu carpeta de usuario (EcoPos-respaldos) y se conservan las \u00FAltimas 14."));
        return col;
    }

    @Override
    void alMostrar() {
        impuestos.removeAll();
        aCorregir.clear();
        try {
            Connection con = ctx.session.getConnection();
            try (PreparedStatement ps = con.prepareStatement("SELECT ID, NAME, RATE, CATEGORY FROM TAXES ORDER BY RATE");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String nombre = rs.getString(2);
                    double tarifa = rs.getDouble(3);
                    boolean valida = esTarifaSri(tarifa);
                    if (valida) {
                        impuestos.add(Ui.texto("<span style='color:#2E9E6B'>\u2713</span> " + escapar(nombre)
                                + " <span style='color:gray'>(" + porcentaje(tarifa) + ")</span>"));
                    } else {
                        aCorregir.add(new String[]{id, rs.getString(4), nombre});
                        impuestos.add(Ui.texto("<span style='color:#C62828'><b>!</b></span> <b>" + escapar(nombre) + "</b> ("
                                + porcentaje(tarifa) + "): el SRI no tiene esa tarifa y rechazar\u00EDa las facturas."));
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron leer los impuestos", e);
            impuestos.add(Ui.nota("No se pudieron leer los impuestos."));
        }
        if (!aCorregir.isEmpty()) {
            JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
            fila.setOpaque(false);
            fila.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton corregir = new JButton("Cambiar a IVA 15%");
            corregir.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    corregir();
                }
            });
            fila.add(corregir);
            impuestos.add(fila);
            impuestos.add(Ui.nota("Los productos que usan ese impuesto pasar\u00E1n a cobrar 15%. Las ventas ya hechas no cambian."));
        } else {
            impuestos.add(Ui.nota("Todo bien: las tarifas son las del SRI. Las cambias en Administraci\u00F3n \u2192 Impuestos."));
        }
        impuestos.revalidate();
        impuestos.repaint();
    }

    private void corregir() {
        try {
            Connection con = ctx.session.getConnection();
            for (String[] t : aCorregir) {
                try (PreparedStatement ps = con.prepareStatement("UPDATE TAXES SET RATE = ?, NAME = ? WHERE ID = ?")) {
                    ps.setDouble(1, 0.15);
                    ps.setString(2, "IVA 15%");
                    ps.setString(3, t[0]);
                    ps.executeUpdate();
                }
                // La categoria con el mismo nombre viejo tambien se renombra (es la que eligen los productos).
                try (PreparedStatement ps = con.prepareStatement("UPDATE TAXCATEGORIES SET NAME = ? WHERE ID = ? AND NAME = ?")) {
                    ps.setString(1, "IVA 15%");
                    ps.setString(2, t[1]);
                    ps.setString(3, t[2]);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo corregir el IVA", e);
            javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(impuestos), "No se pudo cambiar: " + e.getMessage());
        }
        alMostrar();
    }

    @Override
    String guardar() {
        if (!aCorregir.isEmpty()) {
            int r = javax.swing.JOptionPane.showConfirmDialog(Asistente.padreDialogos(impuestos),
                    "Hay un impuesto con una tarifa que el SRI no acepta.\n\u00BFSeguir sin corregirlo?",
                    nombre(), javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
            if (r != javax.swing.JOptionPane.YES_OPTION) {
                return "Toca \u201CCambiar a IVA 15%\u201D para corregir la tarifa.";
            }
        }
        ctx.guardarOpcion("caja.arqueo", arqueo.isSelected() ? "true" : "false");
        ctx.guardarOpcion("backup.enabled", respaldo.isSelected() ? "true" : "false");
        return null;
    }

    static boolean esTarifaSri(double tarifa) {
        for (double t : TARIFAS_SRI) {
            if (Math.abs(t - tarifa) < 1e-9) {
                return true;
            }
        }
        return false;
    }

    private static String porcentaje(double tarifa) {
        double p = tarifa * 100;
        return (p == Math.rint(p) ? String.valueOf((long) p) : String.valueOf(p)) + "%";
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
