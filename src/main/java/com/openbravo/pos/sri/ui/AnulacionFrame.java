package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.anulacion.AnulacionService;
import com.openbravo.pos.sri.config.ClassLoaderPropio;
import com.openbravo.pos.sri.config.ConexionLoader;
import com.openbravo.pos.sri.config.ConfiguracionLoader;
import com.openbravo.pos.sri.config.RutasConector;
import com.openbravo.pos.sri.dominio.DatosEmisor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Emite una Nota de Credito contra una factura ya AUTORIZADA
 * ({@link AnulacionService}) - dialogo modal lanzado desde el boton "Anular
 * factura" del {@link HistorialFrame}. Permite devolver la factura completa o
 * solo algunos productos / cantidades (Nota de Credito parcial); lo que ya se
 * devolvio en notas anteriores de la misma factura no se puede devolver otra
 * vez. Bloquea mientras firma/envia/consulta al SRI y al terminar refresca el
 * historial via {@code alTerminar}.
 */
public class AnulacionFrame extends JDialog {

    private static final int COL_DEVOLVER = 4;

    private final Path archivoConexion;
    private final Path archivoEmisor;
    private final String ticketIdFactura;
    private final Consumer<String> alTerminar;

    private final JTextArea campoMotivo = new JTextArea(3, 30);
    private final JButton botonEmitir = new JButton("Emitir Nota de Crédito");
    private final JLabel etiquetaEstado = new JLabel("Cargando la factura…");
    private final JLabel subtotal = new JLabel(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(
            new Object[]{"Producto", "Vendido", "Por devolver", "Precio unit.", "A devolver"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return columna == COL_DEVOLVER;
        }

        @Override
        public Class<?> getColumnClass(int columna) {
            return columna == COL_DEVOLVER ? BigDecimal.class : Object.class;
        }
    };
    private List<AnulacionService.LineaAnulable> lineas = new ArrayList<>();

    public AnulacionFrame(Frame propietario, Path archivoConexion, String ticketIdFactura,
                           String descripcionFactura, Consumer<String> alTerminar) {
        this(propietario, archivoConexion, RutasConector.resolver("config/datos-emisor.properties"), ticketIdFactura, descripcionFactura, alTerminar);
    }

    public AnulacionFrame(Frame propietario, Path archivoConexion, Path archivoEmisor, String ticketIdFactura,
                           String descripcionFactura, Consumer<String> alTerminar) {
        super(propietario, "Nota de Crédito (devolución)", true);
        this.archivoConexion = archivoConexion;
        this.archivoEmisor = archivoEmisor;
        this.ticketIdFactura = ticketIdFactura;
        this.alTerminar = alTerminar;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel panelSuperior = new JPanel(new GridLayout(0, 1, 4, 4));
        panelSuperior.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));
        panelSuperior.add(new JLabel("Factura: " + descripcionFactura));
        panelSuperior.add(new JLabel("Elige cuánto se devuelve de cada producto (por defecto, todo lo que queda)."));
        add(panelSuperior, BorderLayout.NORTH);

        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(28);
        tabla.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        modelo.addTableModelListener(e -> actualizarSubtotal());
        JButton todo = new JButton("Devolver todo");
        todo.addActionListener(e -> llenar(true));
        JButton nada = new JButton("Nada");
        nada.addActionListener(e -> llenar(false));
        JPanel botonesTabla = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        botonesTabla.add(todo);
        botonesTabla.add(nada);
        botonesTabla.add(subtotal);

        JPanel panelCentro = new JPanel(new BorderLayout(5, 8));
        panelCentro.setBorder(BorderFactory.createEmptyBorder(0, 15, 5, 15));
        JScrollPane scrollTabla = new JScrollPane(tabla);
        scrollTabla.setPreferredSize(new Dimension(620, 180));
        panelCentro.add(scrollTabla, BorderLayout.NORTH);
        panelCentro.add(botonesTabla, BorderLayout.CENTER);
        JPanel motivo = new JPanel(new BorderLayout(4, 4));
        motivo.add(new JLabel("Motivo (obligatorio):"), BorderLayout.NORTH);
        campoMotivo.setLineWrap(true);
        campoMotivo.setWrapStyleWord(true);
        motivo.add(new JScrollPane(campoMotivo), BorderLayout.CENTER);
        panelCentro.add(motivo, BorderLayout.SOUTH);
        add(panelCentro, BorderLayout.CENTER);

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));
        panelInferior.add(etiquetaEstado, BorderLayout.WEST);
        botonEmitir.setEnabled(false);
        botonEmitir.addActionListener(e -> emitir());
        JPanel panelBoton = new JPanel();
        panelBoton.add(botonEmitir);
        panelInferior.add(panelBoton, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(propietario);
        cargarLineas();
    }

    private void cargarLineas() {
        new SwingWorker<List<AnulacionService.LineaAnulable>, Void>() {
            private Exception error;

            @Override
            protected List<AnulacionService.LineaAnulable> doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try (Connection con = ConexionLoader.cargar(archivoConexion).getConnection()) {
                    return AnulacionService.lineasAnulables(con, ticketIdFactura);
                } catch (Exception e) {
                    error = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    etiquetaEstado.setText("No se pudo leer la factura: " + error.getMessage());
                    return;
                }
                try {
                    lineas = get();
                } catch (Exception e) {
                    lineas = new ArrayList<>();
                }
                for (AnulacionService.LineaAnulable l : lineas) {
                    modelo.addRow(new Object[]{l.descripcion, texto(l.vendido), texto(l.disponible),
                            dinero(l.precioUnitario), l.disponible.stripTrailingZeros()});
                }
                boolean quedaAlgo = false;
                for (AnulacionService.LineaAnulable l : lineas) {
                    quedaAlgo |= l.disponible.signum() > 0;
                }
                etiquetaEstado.setText(quedaAlgo ? " " : "Todo lo de esta factura ya fue devuelto.");
                botonEmitir.setEnabled(quedaAlgo);
                actualizarSubtotal();
            }
        }.execute();
    }

    private void llenar(boolean todo) {
        for (int i = 0; i < lineas.size(); i++) {
            modelo.setValueAt(todo ? lineas.get(i).disponible.stripTrailingZeros() : BigDecimal.ZERO, i, COL_DEVOLVER);
        }
    }

    private List<BigDecimal> cantidades() {
        List<BigDecimal> resultado = new ArrayList<>();
        for (int i = 0; i < modelo.getRowCount(); i++) {
            Object v = modelo.getValueAt(i, COL_DEVOLVER);
            resultado.add(v instanceof BigDecimal ? (BigDecimal) v : BigDecimal.ZERO);
        }
        return resultado;
    }

    private void actualizarSubtotal() {
        BigDecimal suma = BigDecimal.ZERO;
        List<BigDecimal> cantidades = cantidades();
        for (int i = 0; i < lineas.size() && i < cantidades.size(); i++) {
            suma = suma.add(lineas.get(i).precioUnitario.multiply(cantidades.get(i)));
        }
        subtotal.setText("   Subtotal a devolver (sin IVA): " + dinero(suma.setScale(2, RoundingMode.HALF_UP)));
    }

    private boolean esDevolucionTotalDeFacturaIntacta(List<BigDecimal> cantidades) {
        for (int i = 0; i < lineas.size(); i++) {
            AnulacionService.LineaAnulable l = lineas.get(i);
            if (l.disponible.compareTo(l.vendido) != 0 || cantidades.get(i).compareTo(l.vendido) != 0) {
                return false;
            }
        }
        return true;
    }

    private void emitir() {
        String motivo = campoMotivo.getText().trim();
        if (motivo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El motivo es obligatorio.", "Falta el motivo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<BigDecimal> cantidades = cantidades();
        for (int i = 0; i < lineas.size(); i++) {
            if (cantidades.get(i).signum() < 0 || cantidades.get(i).compareTo(lineas.get(i).disponible) > 0) {
                JOptionPane.showMessageDialog(this, "De \"" + lineas.get(i).descripcion + "\" solo se pueden devolver "
                        + texto(lineas.get(i).disponible) + ".", "Cantidad no válida", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        final boolean total = esDevolucionTotalDeFacturaIntacta(cantidades);
        int confirmacion = JOptionPane.showConfirmDialog(this,
                "Esto va a emitir y enviar al SRI una Nota de Crédito real "
                + (total ? "por el valor total de la factura." : "por los productos elegidos.") + "\n¿Continuar?",
                "Confirmar nota de crédito", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmacion != JOptionPane.YES_OPTION) {
            return;
        }

        botonEmitir.setEnabled(false);
        etiquetaEstado.setText("Firmando y enviando al SRI, un momento...");

        new SwingWorker<String, Void>() {
            private Exception error;

            @Override
            protected String doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try {
                    DatosEmisor emisor = ConfiguracionLoader.cargar(archivoEmisor);
                    var dataSource = ConexionLoader.cargar(archivoConexion);
                    AnulacionService servicio = new AnulacionService(emisor, dataSource);
                    return total ? servicio.anular(ticketIdFactura, motivo)
                            : servicio.anularParcial(ticketIdFactura, motivo, cantidades);
                } catch (Exception e) {
                    error = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    etiquetaEstado.setText("Error");
                    JOptionPane.showMessageDialog(AnulacionFrame.this,
                            "No se pudo emitir la nota de crédito:\n" + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    botonEmitir.setEnabled(true);
                    return;
                }
                String ticketIdNotaCredito;
                try {
                    ticketIdNotaCredito = get();
                } catch (Exception e) {
                    ticketIdNotaCredito = null;
                }
                JOptionPane.showMessageDialog(AnulacionFrame.this,
                        "Nota de crédito procesada. Revisa su estado en el Historial.",
                        "Listo", JOptionPane.INFORMATION_MESSAGE);
                if (alTerminar != null) {
                    alTerminar.accept(ticketIdNotaCredito);
                }
                dispose();
            }
        }.execute();
    }

    private static String texto(BigDecimal cantidad) {
        return cantidad.stripTrailingZeros().toPlainString();
    }

    private static String dinero(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "EC")).format(valor);
    }
}
