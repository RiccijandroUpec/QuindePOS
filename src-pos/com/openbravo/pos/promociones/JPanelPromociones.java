package com.openbravo.pos.promociones;

import com.openbravo.basic.BasicException;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.JPanelView;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/**
 * Editor de promociones automaticas (tabla ecopos_promociones). Cada fila es
 * una regla: "Lleva N paga M" (2x1, 3x2) o "% de descuento", para un producto
 * o una categoria, con horario y dias opcionales (happy hour). La venta las
 * aplica sola (ver {@link MotorPromociones}).
 */
public class JPanelPromociones extends JPanel implements JPanelView {

    private static final String TIPO_NXM = "Lleva N paga M";
    private static final String TIPO_PORC = "% de descuento";
    private static final int C_ACTIVA = 0, C_NOMBRE = 1, C_TIPO = 2, C_APLICA = 3, C_N = 4, C_M = 5, C_PORC = 6,
            C_DESDE = 7, C_HASTA = 8, C_DIAS = 9;

    private final AppView app;
    private final List<String> ids = new ArrayList<String>();
    private final List<Opcion> opciones = new ArrayList<Opcion>();
    private final JComboBox comboAplica = new JComboBox();
    private final DefaultTableModel modelo = new DefaultTableModel(new Object[]{"Activa", "Nombre", "Tipo",
        "Aplica a", "N (lleva)", "M (paga)", "% desc.", "Desde (h)", "Hasta (h)", "D\u00EDas (1=lun...7=dom)"}, 0) {
        @Override
        public Class<?> getColumnClass(int c) {
            if (c == C_ACTIVA) {
                return Boolean.class;
            }
            if (c == C_N || c == C_M || c == C_DESDE || c == C_HASTA) {
                return Integer.class;
            }
            return c == C_PORC ? Double.class : Object.class;
        }
    };
    private final JTable tabla = new JTable(modelo);

    /** Producto o categoria elegible en "Aplica a". */
    private static final class Opcion {
        final String producto;
        final String categoria;
        final String texto;

        Opcion(String producto, String categoria, String texto) {
            this.producto = producto;
            this.categoria = categoria;
            this.texto = texto;
        }

        @Override
        public String toString() {
            return texto;
        }
    }

    public JPanelPromociones(AppView app) {
        this.app = app;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titulo = new JLabel("Promociones autom\u00E1ticas");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        JLabel ayuda = new JLabel("<html>Ejemplos: <b>2x1</b> = Lleva N paga M con N=2, M=1 \u00B7 <b>3x2</b> = N=3, M=2 \u00B7 "
                + "<b>Happy hour</b> = % de descuento de 17 a 19 h, d\u00EDas 12345. Desde = Hasta significa todo el d\u00EDa; "
                + "d\u00EDas vac\u00EDo = todos. Si varias aplican, el cliente recibe la mejor.</html>");
        ayuda.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel norte = new JPanel(new BorderLayout(0, 6));
        norte.add(titulo, BorderLayout.NORTH);
        norte.add(ayuda, BorderLayout.CENTER);
        add(norte, BorderLayout.NORTH);

        tabla.setRowHeight(30);
        tabla.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        tabla.getColumnModel().getColumn(C_TIPO).setCellEditor(new DefaultCellEditor(new JComboBox(new Object[]{TIPO_NXM, TIPO_PORC})));
        tabla.getColumnModel().getColumn(C_APLICA).setCellEditor(new DefaultCellEditor(comboAplica));
        tabla.getColumnModel().getColumn(C_NOMBRE).setPreferredWidth(160);
        tabla.getColumnModel().getColumn(C_APLICA).setPreferredWidth(220);
        tabla.getColumnModel().getColumn(C_TIPO).setPreferredWidth(130);
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        JButton agregar = new JButton("Agregar promoci\u00F3n");
        JButton eliminar = new JButton("Eliminar");
        JButton guardar = new JButton("Guardar cambios");
        guardar.setFont(guardar.getFont().deriveFont(Font.BOLD));
        agregar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ids.add(null);
                modelo.addRow(new Object[]{Boolean.TRUE, "2x1", TIPO_NXM, opciones.isEmpty() ? null : opciones.get(0), 2, 1, 0.0, 0, 0, ""});
            }
        });
        eliminar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int fila = tabla.getSelectedRow();
                if (fila >= 0) {
                    if (tabla.isEditing()) {
                        tabla.getCellEditor().cancelCellEditing();
                    }
                    ids.remove(fila);
                    modelo.removeRow(fila);
                }
            }
        });
        guardar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                guardar();
            }
        });
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sur.add(agregar);
        sur.add(eliminar);
        sur.add(guardar);
        add(sur, BorderLayout.SOUTH);
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public void activate() throws BasicException {
        cargar();
    }

    @Override
    public boolean deactivate() {
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }
        return true;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    private void cargar() {
        modelo.setRowCount(0);
        ids.clear();
        opciones.clear();
        comboAplica.removeAllItems();
        try {
            Connection con = app.getSession().getConnection();
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT ID, NAME FROM CATEGORIES WHERE ID <> 'xxx999' ORDER BY NAME")) {
                while (rs.next()) {
                    opciones.add(new Opcion(null, rs.getString(1), "Categor\u00EDa: " + rs.getString(2)));
                }
            }
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT ID, NAME FROM PRODUCTS WHERE ID <> 'xxx999' ORDER BY NAME")) {
                while (rs.next()) {
                    opciones.add(new Opcion(rs.getString(1), null, "Producto: " + rs.getString(2)));
                }
            }
            for (Opcion o : opciones) {
                comboAplica.addItem(o);
            }
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, activo, nombre, tipo, producto, categoria, n, m, porcentaje, "
                         + "hora_desde, hora_hasta, dias FROM ecopos_promociones ORDER BY nombre")) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                    modelo.addRow(new Object[]{rs.getInt(2) == 1, rs.getString(3),
                        MotorPromociones.TIPO_PORCENTAJE.equals(rs.getString(4)) ? TIPO_PORC : TIPO_NXM,
                        buscarOpcion(rs.getString(5), rs.getString(6)), rs.getInt(7), rs.getInt(8), rs.getDouble(9),
                        rs.getInt(10), rs.getInt(11), rs.getString(12) == null ? "" : rs.getString(12)});
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron leer las promociones: " + e.getMessage(),
                    "Promociones", JOptionPane.WARNING_MESSAGE);
        }
    }

    private Opcion buscarOpcion(String producto, String categoria) {
        for (Opcion o : opciones) {
            if ((producto != null && producto.equals(o.producto)) || (producto == null && categoria != null && categoria.equals(o.categoria))) {
                return o;
            }
        }
        return null;
    }

    private void guardar() {
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }
        // Validar antes de tocar la base.
        for (int f = 0; f < modelo.getRowCount(); f++) {
            String problema = validar(f);
            if (problema != null) {
                tabla.setRowSelectionInterval(f, f);
                JOptionPane.showMessageDialog(this, "Fila " + (f + 1) + ": " + problema, "Promociones", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        try {
            Connection con = app.getSession().getConnection();
            try (Statement st = con.createStatement()) {
                st.execute("DELETE FROM ecopos_promociones");
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO ecopos_promociones (id, activo, nombre, tipo, "
                    + "producto, categoria, n, m, porcentaje, hora_desde, hora_hasta, dias) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int f = 0; f < modelo.getRowCount(); f++) {
                    Opcion o = (Opcion) modelo.getValueAt(f, C_APLICA);
                    boolean nxm = TIPO_NXM.equals(modelo.getValueAt(f, C_TIPO));
                    ps.setString(1, ids.get(f) == null ? UUID.randomUUID().toString() : ids.get(f));
                    ps.setInt(2, Boolean.TRUE.equals(modelo.getValueAt(f, C_ACTIVA)) ? 1 : 0);
                    ps.setString(3, String.valueOf(modelo.getValueAt(f, C_NOMBRE)).trim());
                    ps.setString(4, nxm ? MotorPromociones.TIPO_NXM : MotorPromociones.TIPO_PORCENTAJE);
                    ps.setString(5, o.producto);
                    ps.setString(6, o.categoria);
                    ps.setInt(7, entero(modelo.getValueAt(f, C_N)));
                    ps.setInt(8, entero(modelo.getValueAt(f, C_M)));
                    ps.setDouble(9, decimal(modelo.getValueAt(f, C_PORC)));
                    ps.setInt(10, entero(modelo.getValueAt(f, C_DESDE)));
                    ps.setInt(11, entero(modelo.getValueAt(f, C_HASTA)));
                    ps.setString(12, String.valueOf(modelo.getValueAt(f, C_DIAS) == null ? "" : modelo.getValueAt(f, C_DIAS)).trim());
                    ps.executeUpdate();
                }
            }
            JOptionPane.showMessageDialog(this, "Promociones guardadas. Se aplican en la venta en menos de un minuto.",
                    "Promociones", JOptionPane.INFORMATION_MESSAGE);
            cargar();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron guardar: " + e.getMessage(), "Promociones", JOptionPane.WARNING_MESSAGE);
        }
    }

    private String validar(int f) {
        Object nombre = modelo.getValueAt(f, C_NOMBRE);
        if (nombre == null || String.valueOf(nombre).trim().isEmpty()) {
            return "falta el nombre";
        }
        if (!(modelo.getValueAt(f, C_APLICA) instanceof Opcion)) {
            return "elige a qu\u00E9 producto o categor\u00EDa aplica";
        }
        if (TIPO_NXM.equals(modelo.getValueAt(f, C_TIPO))) {
            int n = entero(modelo.getValueAt(f, C_N));
            int m = entero(modelo.getValueAt(f, C_M));
            if (n < 2 || m < 0 || m >= n) {
                return "en \"Lleva N paga M\", N debe ser 2 o m\u00E1s y M menor que N (ej. 2x1: N=2, M=1)";
            }
        } else {
            double p = decimal(modelo.getValueAt(f, C_PORC));
            if (p <= 0 || p > 100) {
                return "el % de descuento debe estar entre 1 y 100";
            }
        }
        int desde = entero(modelo.getValueAt(f, C_DESDE));
        int hasta = entero(modelo.getValueAt(f, C_HASTA));
        if (desde < 0 || desde > 24 || hasta < 0 || hasta > 24) {
            return "las horas van de 0 a 24";
        }
        String dias = modelo.getValueAt(f, C_DIAS) == null ? "" : String.valueOf(modelo.getValueAt(f, C_DIAS)).trim();
        if (!dias.matches("[1-7]*")) {
            return "d\u00EDas: usa n\u00FAmeros del 1 (lunes) al 7 (domingo), por ejemplo 12345";
        }
        return null;
    }

    private static int entero(Object valor) {
        return valor instanceof Number ? ((Number) valor).intValue() : 0;
    }

    private static double decimal(Object valor) {
        return valor instanceof Number ? ((Number) valor).doubleValue() : 0;
    }
}
