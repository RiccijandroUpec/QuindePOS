package com.openbravo.pos.panels;

import com.openbravo.basic.BasicException;
import com.openbravo.format.Formats;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.JPanelView;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Resumen tributario del mes: ventas agrupadas por tarifa de IVA (base
 * imponible, IVA y numero de comprobantes), con las devoluciones aparte y
 * exportacion a CSV para el contador. Sirve de base para la seccion de ventas
 * del formulario 104; no reemplaza la revision del contador.
 */
public class JPanelResumenTributario extends JPanel implements JPanelView {

    private final AppView app;
    private final JComboBox mes = new JComboBox();
    private final DefaultTableModel modelo = new DefaultTableModel(
            new Object[]{"Tipo", "Tarifa", "Base imponible", "IVA", "Total", "Comprobantes"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JLabel totales = new JLabel();
    private final JLabel sri = new JLabel();
    private final List<Calendar> inicios = new ArrayList<Calendar>();

    public JPanelResumenTributario(AppView app) {
        this.app = app;
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titulo = new JLabel("Resumen tributario del mes");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        SimpleDateFormat formato = new SimpleDateFormat("MMMM yyyy", new Locale("es", "EC"));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 13; i++) {
            inicios.add((Calendar) c.clone());
            String nombre = formato.format(c.getTime());
            mes.addItem(Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1));
            c.add(Calendar.MONTH, -1);
        }
        mes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cargar();
            }
        });
        JButton exportar = new JButton("Exportar CSV");
        exportar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportarCsv();
            }
        });
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controles.setOpaque(false);
        controles.add(new JLabel("Mes:"));
        controles.add(mes);
        controles.add(exportar);
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(controles, BorderLayout.EAST);
        add(cabecera, BorderLayout.NORTH);

        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(30);
        DefaultTableCellRenderer derecha = new DefaultTableCellRenderer();
        derecha.setHorizontalAlignment(JLabel.RIGHT);
        for (int i = 1; i < modelo.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setCellRenderer(derecha);
        }
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        totales.setFont(totales.getFont().deriveFont(Font.BOLD, 15f));
        sri.setForeground(UIManager.getColor("Label.disabledForeground"));
        JLabel nota = new JLabel("<html>Base para la secci\u00F3n de ventas del formulario 104. Los valores salen de las ventas "
                + "registradas en Quinde POS; rev\u00EDsalos con tu contador antes de declarar.</html>");
        nota.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel pie = new JPanel(new BorderLayout(0, 6));
        pie.setOpaque(false);
        pie.add(totales, BorderLayout.NORTH);
        pie.add(sri, BorderLayout.CENTER);
        pie.add(nota, BorderLayout.SOUTH);
        add(pie, BorderLayout.SOUTH);
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
        return true;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    private void cargar() {
        modelo.setRowCount(0);
        Calendar inicio = inicios.get(Math.max(0, mes.getSelectedIndex()));
        Calendar fin = (Calendar) inicio.clone();
        fin.add(Calendar.MONTH, 1);
        double baseTotal = 0;
        double ivaTotal = 0;
        try {
            Connection con = app.getSession().getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT T.TICKETTYPE, TX.NAME, TX.RATE, SUM(TL.BASE), SUM(TL.AMOUNT), COUNT(DISTINCT R.ID) "
                    + "FROM TAXLINES TL JOIN RECEIPTS R ON R.ID = TL.RECEIPT JOIN TICKETS T ON T.ID = R.ID "
                    + "JOIN TAXES TX ON TX.ID = TL.TAXID WHERE R.DATENEW >= ? AND R.DATENEW < ? "
                    + "GROUP BY T.TICKETTYPE, TX.NAME, TX.RATE ORDER BY T.TICKETTYPE, TX.RATE DESC")) {
                ps.setTimestamp(1, new Timestamp(inicio.getTimeInMillis()));
                ps.setTimestamp(2, new Timestamp(fin.getTimeInMillis()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double base = rs.getDouble(4);
                        double iva = rs.getDouble(5);
                        baseTotal += base;
                        ivaTotal += iva;
                        modelo.addRow(new Object[]{
                            rs.getInt(1) == 0 ? "Ventas" : "Devoluciones",
                            rs.getString(2).contains("%") ? rs.getString(2) : rs.getString(2) + " (" + Formats.PERCENT.formatValue(rs.getDouble(3)) + ")",
                            Formats.CURRENCY.formatValue(base),
                            Formats.CURRENCY.formatValue(iva),
                            Formats.CURRENCY.formatValue(base + iva),
                            rs.getInt(6)});
                    }
                }
            }
            if (modelo.getRowCount() == 0) {
                modelo.addRow(new Object[]{"Sin ventas en este mes", "", "", "", "", ""});
            }
            totales.setText("Total del mes: base " + Formats.CURRENCY.formatValue(baseTotal) + "  \u00B7  IVA "
                    + Formats.CURRENCY.formatValue(ivaTotal) + "  \u00B7  total " + Formats.CURRENCY.formatValue(baseTotal + ivaTotal));
            sri.setText(resumenSri(con, inicio, fin));
        } catch (SQLException e) {
            totales.setText("No se pudo leer el resumen: " + e.getMessage());
        }
    }

    private static String resumenSri(Connection con, Calendar inicio, Calendar fin) {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT estado, COUNT(*) FROM ecopos_sri_comprobantes WHERE fecha_emision >= ? AND fecha_emision < ? GROUP BY estado")) {
            ps.setTimestamp(1, new Timestamp(inicio.getTimeInMillis()));
            ps.setTimestamp(2, new Timestamp(fin.getTimeInMillis()));
            StringBuilder sb = new StringBuilder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(sb.length() == 0 ? "" : " \u00B7 ").append(rs.getInt(2)).append(" ").append(rs.getString(1).toLowerCase(Locale.ROOT));
                }
            }
            return sb.length() == 0 ? "Comprobantes electr\u00F3nicos SRI del mes: ninguno" : "Comprobantes electr\u00F3nicos SRI del mes: " + sb;
        } catch (SQLException e) {
            return " "; // conector SRI no instalado
        }
    }

    private void exportarCsv() {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File("resumen-tributario-" + String.valueOf(mes.getSelectedItem()).replace(' ', '-').toLowerCase(Locale.ROOT) + ".csv"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (PrintWriter salida = new PrintWriter(new OutputStreamWriter(
                new java.io.FileOutputStream(selector.getSelectedFile()), StandardCharsets.UTF_8))) {
            salida.print('\uFEFF'); // BOM para que Excel lea bien las tildes
            for (int c = 0; c < modelo.getColumnCount(); c++) {
                salida.print((c == 0 ? "" : ";") + modelo.getColumnName(c));
            }
            salida.println();
            for (int f = 0; f < modelo.getRowCount(); f++) {
                for (int c = 0; c < modelo.getColumnCount(); c++) {
                    salida.print((c == 0 ? "" : ";") + String.valueOf(modelo.getValueAt(f, c)).replace(";", ","));
                }
                salida.println();
            }
            JOptionPane.showMessageDialog(this, "Guardado en " + selector.getSelectedFile(), "Exportar CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + e.getMessage(), "Exportar CSV", JOptionPane.WARNING_MESSAGE);
        }
    }
}
