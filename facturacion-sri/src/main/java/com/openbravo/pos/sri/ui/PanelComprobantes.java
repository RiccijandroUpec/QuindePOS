package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.config.ClassLoaderPropio;
import com.openbravo.pos.sri.config.ConexionLoader;
import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.dominio.TipoComprobante;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.HierarchyEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla "Comprobantes electronicos" dentro de EcoPos: resumen del periodo,
 * filtros (periodo, estado, tipo, busqueda por numero/cliente/cedula/ticket),
 * la lista con estados en color y, al elegir un comprobante, su detalle con
 * el error explicado en palabras simples y las acciones (RIDE, correo,
 * reintentar, nota de credito, XML).
 */
public class PanelComprobantes extends JPanel {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Duration ATASCADO = Duration.ofHours(24);

    private static final String[] PERIODOS = {"Hoy", "Últimos 7 días", "Este mes", "Mes anterior", "Todo"};
    private static final String[] ESTADOS = {"Todos los estados", "Autorizados", "En proceso", "Por revisar"};
    private static final String[] TIPOS = {"Facturas y notas de crédito", "Solo facturas", "Solo notas de crédito"};

    private final JComboBox<String> periodo = new JComboBox<>(PERIODOS);
    private final JComboBox<String> estado = new JComboBox<>(ESTADOS);
    private final JComboBox<String> tipo = new JComboBox<>(TIPOS);
    private final JTextField buscar = new JTextField(18);
    private final JLabel kpiAutorizados = new JLabel();
    private final JLabel kpiProceso = new JLabel();
    private final JLabel kpiRevisar = new JLabel();
    private final JLabel kpiTotal = new JLabel();

    private final DefaultTableModel modelo = new DefaultTableModel(
            new Object[]{"Número", "Tipo", "Fecha", "Cliente", "Cédula / RUC", "Total", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final List<FilaComprobante> todas = new ArrayList<>();
    private final List<FilaComprobante> visibles = new ArrayList<>();

    private final JLabel detalleTitulo = new JLabel("Elige un comprobante");
    private final JLabel detalleEstado = new JLabel(" ");
    private final JLabel detalleDatos = new JLabel(" ");
    private final JTextArea detalleExplicacion = new JTextArea(4, 20);
    private final JButton botonRide = new JButton("Ver RIDE (PDF)");
    private final JButton botonCorreo = new JButton("Enviar por correo");
    private final JButton botonReintentar = new JButton("Reintentar");
    private final JButton botonNota = new JButton("Nota de crédito");
    private final JButton botonXml = new JButton("Ver XML");

    public PanelComprobantes() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titulo = new JLabel("Comprobantes electrónicos");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 10, 0));
        kpis.add(tarjeta("Autorizados", kpiAutorizados, verde()));
        kpis.add(tarjeta("En proceso", kpiProceso, ambar()));
        kpis.add(tarjeta("Por revisar", kpiRevisar, rojo()));
        kpis.add(tarjeta("Total autorizado", kpiTotal, null));

        buscar.putClientProperty("JTextField.placeholderText", "Número, cliente, cédula o ticket");
        buscar.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        JButton actualizar = new JButton("Actualizar");
        actualizar.addActionListener(e -> cargar());
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        filtros.add(periodo);
        filtros.add(estado);
        filtros.add(tipo);
        filtros.add(buscar);
        filtros.add(actualizar);
        periodo.addActionListener(e -> cargar());
        estado.addActionListener(e -> filtrar());
        tipo.addActionListener(e -> filtrar());
        buscar.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filtrar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtrar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filtrar();
            }
        });

        JPanel norte = new JPanel(new BorderLayout(0, 10));
        norte.add(titulo, BorderLayout.NORTH);
        norte.add(kpis, BorderLayout.CENTER);
        norte.add(filtros, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);

        tabla.setRowHeight(30);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setDefaultRenderer(Object.class, new Renderizador());
        tabla.getColumnModel().getColumn(0).setPreferredWidth(150);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(220);
        tabla.getColumnModel().getColumn(6).setPreferredWidth(170);
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrarDetalle();
            }
        });

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tabla), construirDetalle());
        division.setResizeWeight(1.0);
        division.setBorder(null);
        add(division, BorderLayout.CENTER);

        // Se recarga cada vez que la pantalla se muestra.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                cargar();
            }
        });
        periodo.setSelectedIndex(2);
    }

    private JPanel construirDetalle() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        panel.setPreferredSize(new Dimension(330, 400));
        detalleTitulo.setFont(detalleTitulo.getFont().deriveFont(Font.BOLD, 17f));
        detalleEstado.setFont(detalleEstado.getFont().deriveFont(Font.BOLD, 14f));
        detalleExplicacion.setEditable(false);
        detalleExplicacion.setLineWrap(true);
        detalleExplicacion.setWrapStyleWord(true);
        detalleExplicacion.setOpaque(false);
        detalleExplicacion.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        JPanel arriba = new JPanel();
        arriba.setLayout(new BoxLayout(arriba, BoxLayout.Y_AXIS));
        for (javax.swing.JComponent c : new javax.swing.JComponent[]{detalleTitulo, detalleEstado, detalleDatos, detalleExplicacion}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            arriba.add(c);
        }
        panel.add(arriba, BorderLayout.NORTH);

        JPanel acciones = new JPanel(new GridLayout(0, 1, 0, 6));
        for (JButton b : new JButton[]{botonRide, botonCorreo, botonReintentar, botonNota, botonXml}) {
            b.setEnabled(false);
            acciones.add(b);
        }
        botonRide.addActionListener(e -> accion(f -> AccionesComprobante.verRide(this, f)));
        botonCorreo.addActionListener(e -> accion(f -> AccionesComprobante.enviarPorCorreo(this, f)));
        botonReintentar.addActionListener(e -> accion(f -> AccionesComprobante.reintentar(this, f, this::cargar)));
        botonNota.addActionListener(e -> accion(f -> AccionesComprobante.notaCredito(this, f, this::cargar)));
        botonXml.addActionListener(e -> accion(f -> AccionesComprobante.verXml(this, f)));
        panel.add(acciones, BorderLayout.SOUTH);
        return panel;
    }

    private void accion(java.util.function.Consumer<FilaComprobante> hacer) {
        FilaComprobante f = seleccionada();
        if (f != null) {
            hacer.accept(f);
        }
    }

    private FilaComprobante seleccionada() {
        int fila = tabla.getSelectedRow();
        return fila < 0 ? null : visibles.get(tabla.convertRowIndexToModel(fila));
    }

    /** Recarga desde la base el periodo elegido (en segundo plano). */
    public void cargar() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime desde;
        LocalDateTime hasta = null;
        switch (periodo.getSelectedIndex()) {
            case 0: desde = hoy.atStartOfDay(); break;
            case 1: desde = hoy.minusDays(6).atStartOfDay(); break;
            case 2: desde = hoy.withDayOfMonth(1).atStartOfDay(); break;
            case 3: desde = hoy.withDayOfMonth(1).minusMonths(1).atStartOfDay(); hasta = hoy.withDayOfMonth(1).atStartOfDay(); break;
            default: desde = null;
        }
        final LocalDateTime d = desde;
        final LocalDateTime h = hasta;
        new SwingWorker<List<FilaComprobante>, Void>() {
            private Exception error;

            @Override
            protected List<FilaComprobante> doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try (Connection con = ConexionLoader.cargar(AccionesComprobante.rutaConexion()).getConnection()) {
                    return FilaComprobante.listar(con, d, h);
                } catch (Exception e) {
                    error = e;
                    return new ArrayList<>();
                }
            }

            @Override
            protected void done() {
                todas.clear();
                try {
                    todas.addAll(get());
                } catch (Exception ignorado) {
                    // error ya capturado
                }
                if (error != null) {
                    detalleTitulo.setText("No se pudieron leer los comprobantes");
                    detalleExplicacion.setText(error.getMessage());
                }
                actualizarKpis();
                filtrar();
            }
        }.execute();
    }

    private void actualizarKpis() {
        int autorizados = 0;
        int proceso = 0;
        int revisar = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (FilaComprobante f : todas) {
            switch (grupo(f)) {
                case 0:
                    autorizados++;
                    if (f.total != null) {
                        total = f.tipo == TipoComprobante.NOTA_CREDITO ? total.subtract(f.total) : total.add(f.total);
                    }
                    break;
                case 1: proceso++; break;
                default: revisar++;
            }
        }
        kpiAutorizados.setText(String.valueOf(autorizados));
        kpiProceso.setText(String.valueOf(proceso));
        kpiRevisar.setText(String.valueOf(revisar));
        kpiTotal.setText(dinero(total));
    }

    private void filtrar() {
        String texto = buscar.getText().trim().toLowerCase(Locale.ROOT);
        FilaComprobante antes = tabla.getSelectedRow() >= 0 ? seleccionada() : null;
        visibles.clear();
        modelo.setRowCount(0);
        for (FilaComprobante f : todas) {
            if (estado.getSelectedIndex() > 0 && grupo(f) != estado.getSelectedIndex() - 1) {
                continue;
            }
            if (tipo.getSelectedIndex() == 1 && f.tipo != TipoComprobante.FACTURA
                    || tipo.getSelectedIndex() == 2 && f.tipo != TipoComprobante.NOTA_CREDITO) {
                continue;
            }
            if (!texto.isEmpty() && !(contiene(f.numero, texto) || contiene(f.cliente, texto)
                    || contiene(f.identificacion, texto) || contiene(f.numeroTicket == null ? null : String.valueOf(f.numeroTicket), texto))) {
                continue;
            }
            visibles.add(f);
            modelo.addRow(new Object[]{f.numero, f.tipo == TipoComprobante.NOTA_CREDITO ? "Nota de crédito" : "Factura",
                    f.fechaEmision == null ? "" : f.fechaEmision.format(FECHA), f.cliente,
                    f.identificacion == null ? "" : f.identificacion, f.total == null ? "" : dinero(f.total), textoEstado(f)});
        }
        int reseleccionar = antes == null ? -1 : visibles.indexOf(buscarPorId(antes.id));
        if (reseleccionar >= 0) {
            tabla.setRowSelectionInterval(reseleccionar, reseleccionar);
        } else {
            mostrarDetalle();
        }
    }

    private FilaComprobante buscarPorId(String id) {
        for (FilaComprobante f : visibles) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }

    private void mostrarDetalle() {
        FilaComprobante f = tabla.getSelectedRow() >= 0 ? seleccionada() : null;
        boolean hay = f != null;
        boolean factura = hay && f.tipo == TipoComprobante.FACTURA;
        boolean autorizado = hay && f.estado == EstadoComprobante.AUTORIZADO;
        botonRide.setEnabled(hay);
        botonXml.setEnabled(hay);
        botonCorreo.setEnabled(autorizado);
        botonNota.setEnabled(factura && autorizado);
        botonReintentar.setEnabled(factura && !autorizado);
        if (!hay) {
            detalleTitulo.setText("<html><div style='width:280px'>" + (todas.isEmpty()
                    ? "No hay comprobantes en este periodo</div><div style='width:280px;font-weight:normal;font-size:11px'>Prueba con “Todo” en el filtro de periodo."
                    : "Elige un comprobante") + "</div></html>");
            detalleEstado.setText(" ");
            detalleDatos.setText(" ");
            detalleExplicacion.setText("");
            return;
        }
        detalleTitulo.setText("<html><div style='width:280px'>" + (f.tipo == TipoComprobante.NOTA_CREDITO ? "Nota de crédito" : "Factura")
                + "<br>" + f.numero + "</div></html>");
        detalleEstado.setText("●  " + textoEstado(f));
        detalleEstado.setForeground(colorEstado(f));
        detalleDatos.setText("<html><div style='width:280px'>"
                + "<b>Cliente:</b> " + html(f.cliente) + (f.identificacion == null ? "" : " (" + html(f.identificacion) + ")") + "<br>"
                + "<b>Total:</b> " + (f.total == null ? "—" : dinero(f.total)) + "<br>"
                + "<b>Emitida:</b> " + (f.fechaEmision == null ? "" : f.fechaEmision.format(FECHA)) + "<br>"
                + (f.numeroTicket == null ? "" : "<b>Ticket:</b> " + f.numeroTicket + "<br>")
                + (f.numeroAutorizacion == null ? "" : "<b>Autorización:</b> " + f.numeroAutorizacion + "<br>")
                + (f.motivo == null ? "" : "<b>Motivo:</b> " + html(f.motivo) + "<br>")
                + "</div></html>");
        String explicacion = f.estado == EstadoComprobante.AUTORIZADO ? null : MensajesSri.explicar(f.mensajeError);
        if (explicacion == null && (f.estado == EstadoComprobante.PENDIENTE || f.estado == EstadoComprobante.ENVIADO)) {
            explicacion = "En camino al SRI. Quinde POS consulta su autorización automáticamente.";
        }
        detalleExplicacion.setText(explicacion == null ? "" : explicacion);
        detalleExplicacion.setForeground(f.estado == EstadoComprobante.AUTORIZADO ? UIManager.getColor("Label.foreground") : colorEstado(f));
    }

    /** 0 = autorizado, 1 = en proceso, 2 = por revisar (rechazado, error, o en proceso hace mas de 24 h). */
    private static int grupo(FilaComprobante f) {
        if (f.estado == EstadoComprobante.AUTORIZADO) {
            return 0;
        }
        if (f.estado == EstadoComprobante.RECHAZADO || f.estado == EstadoComprobante.ERROR) {
            return 2;
        }
        boolean atascado = f.fechaEmision != null && Duration.between(f.fechaEmision, LocalDateTime.now()).compareTo(ATASCADO) > 0;
        return atascado ? 2 : 1;
    }

    private static String textoEstado(FilaComprobante f) {
        switch (f.estado) {
            case AUTORIZADO: return "Autorizada";
            case RECHAZADO: return "Rechazada por el SRI";
            case ERROR: return "Con error";
            default: return grupo(f) == 2 ? "Atascada (más de 24 h)" : "En proceso";
        }
    }

    private static Color colorEstado(FilaComprobante f) {
        switch (grupo(f)) {
            case 0: return verde();
            case 1: return ambar();
            default: return rojo();
        }
    }

    private static boolean oscuro() {
        Color fondo = UIManager.getColor("Panel.background");
        return fondo != null && (fondo.getRed() + fondo.getGreen() + fondo.getBlue()) / 3 < 128;
    }

    private static Color verde() {
        return oscuro() ? new Color(0xA8E6C1) : new Color(0x1B5E3F);
    }

    private static Color ambar() {
        return oscuro() ? new Color(0xFFD54F) : new Color(0xB26A00);
    }

    private static Color rojo() {
        return oscuro() ? new Color(0xEF9A9A) : new Color(0xC62828);
    }

    private static JPanel tarjeta(String etiqueta, JLabel valor, Color color) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel l = new JLabel(etiqueta);
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        valor.setFont(valor.getFont().deriveFont(Font.BOLD, 22f));
        if (color != null) {
            valor.setForeground(color);
        }
        p.add(l);
        p.add(valor);
        return p;
    }

    private static boolean contiene(String valor, String texto) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(texto);
    }

    private static String dinero(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "EC")).format(valor);
    }

    private static String html(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Estado en color con un punto; Total alineado a la derecha. */
    private final class Renderizador extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco, int fila, int col) {
            Component c = super.getTableCellRendererComponent(t, valor, sel, foco, fila, col);
            setHorizontalAlignment(col == 5 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (col == 6 && !sel) {
                FilaComprobante f = visibles.get(t.convertRowIndexToModel(fila));
                setText("●  " + valor);
                setForeground(colorEstado(f));
            } else if (!sel) {
                setForeground(UIManager.getColor("Table.foreground"));
            }
            return c;
        }
    }
}
