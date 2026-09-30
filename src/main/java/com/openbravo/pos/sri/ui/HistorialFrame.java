package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.ConectorPrincipal;
import com.openbravo.pos.sri.config.ClassLoaderPropio;
import com.openbravo.pos.sri.config.ConexionLoader;
import com.openbravo.pos.sri.config.ConfiguracionCorreoLoader;
import com.openbravo.pos.sri.config.ConfiguracionLoader;
import com.openbravo.pos.sri.correo.NotificadorCorreo;
import com.openbravo.pos.sri.dominio.ConfiguracionCorreo;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.config.RutasConector;
import com.openbravo.pos.sri.repository.ComprobanteRepository;
import com.openbravo.pos.sri.ride.RideGenerator;
import com.openbravo.pos.sri.ride.RideNotaCreditoGenerator;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Historial de facturacion electronica: lista todo lo que hay en
 * {@code ecopos_sri_comprobantes} (mas reciente primero, facturas y notas de
 * credito por igual), con el numero de ticket de ECOPos en vez del UUID
 * interno. Permite ver el XML/RIDE, anular una factura (Nota de Credito),
 * reintentar un envio fallido, y mandar el comprobante por correo. Ventana
 * independiente (ver {@link #main(String[])}), lanzada desde ECOPos via un
 * boton data-only (mismo patron que {@link ConfiguracionFrame}).
 */
public class HistorialFrame extends JFrame {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static Path rutaConexionPorDefecto() {
        return RutasConector.resolver("config/conexion.properties");
    }

    private static Path rutaEmisorPorDefecto() {
        return RutasConector.resolver("config/datos-emisor.properties");
    }

    private static Path rutaCorreoPorDefecto() {
        return RutasConector.resolver("config/correo.properties");
    }

    /** Comprobantes ENVIADO/ERROR con mas de este tiempo desde su emision se marcan como atascados - aviso operativo, no un plazo legal verificado. */
    private static final Duration UMBRAL_ATASCADO = Duration.ofHours(24);

    private final Path archivoConexion;
    private final DefaultTableModel modelo;
    private final JLabel etiquetaEstado = new JLabel(" ");
    /** Fila -> registro completo (mismo orden que el modelo de la tabla). */
    private final List<ComprobanteRepository.RegistroHistorial> registrosPorFila = new ArrayList<>();
    private final JTable tabla;

    private final JButton botonAnular = new JButton("Anular factura");
    private final JButton botonReintentar = new JButton("Reintentar envío");
    private final JButton botonCorreo = new JButton("Enviar por correo");

    public HistorialFrame() {
        this(rutaConexionPorDefecto());
    }

    public HistorialFrame(Path archivoConexion) {
        super("EcoPos SRI Connector - Historial de facturación electrónica");
        this.archivoConexion = archivoConexion;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        modelo = new DefaultTableModel(
                new Object[]{"Tipo", "Ticket/Ref", "Fecha", "Estado", "Secuencial", "Clave de acceso",
                        "N° Autorización", "Intentos", "Motivo", "Error"}, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        tabla.setDefaultRenderer(Object.class, new RenderizadorPorEstado());
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tabla.getColumnModel().getColumn(5).setPreferredWidth(280);
        tabla.getColumnModel().getColumn(6).setPreferredWidth(130);
        tabla.getColumnModel().getColumn(8).setPreferredWidth(200);
        tabla.getColumnModel().getColumn(9).setPreferredWidth(250);
        tabla.getSelectionModel().addListSelectionListener(e -> actualizarBotones());

        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(construirPanelInferior(), BorderLayout.SOUTH);

        cargarHistorial();
        actualizarBotones();

        setMinimumSize(new Dimension(1050, 540));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel construirPanelInferior() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(etiquetaEstado, BorderLayout.WEST);

        JPanel botones = new JPanel();
        JButton botonVerXml = new JButton("Ver XML");
        botonVerXml.addActionListener(e -> verXml());
        botones.add(botonVerXml);

        JButton botonVerRide = new JButton("Ver RIDE (PDF)");
        botonVerRide.addActionListener(e -> verRide());
        botones.add(botonVerRide);

        botonAnular.addActionListener(e -> anular());
        botones.add(botonAnular);

        botonReintentar.addActionListener(e -> reintentar());
        botones.add(botonReintentar);

        botonCorreo.addActionListener(e -> enviarPorCorreo());
        botones.add(botonCorreo);

        JButton botonConfigurarCorreo = new JButton("Configurar correo...");
        botonConfigurarCorreo.addActionListener(e -> new ConfiguracionCorreoFrame(rutaCorreoPorDefecto()).setVisible(true));
        botones.add(botonConfigurarCorreo);

        JButton botonActualizar = new JButton("Actualizar");
        botonActualizar.addActionListener(e -> cargarHistorial());
        botones.add(botonActualizar);

        panel.add(botones, BorderLayout.EAST);
        return panel;
    }

    private void cargarHistorial() {
        modelo.setRowCount(0);
        registrosPorFila.clear();
        try {
            DataSource dataSource = ConexionLoader.cargar(archivoConexion);
            List<ComprobanteRepository.RegistroHistorial> historial =
                    new ComprobanteRepository(dataSource.getConnection()).listarHistorial();

            for (ComprobanteRepository.RegistroHistorial registro : historial) {
                modelo.addRow(new Object[]{
                        registro.tipoComprobante == TipoComprobante.NOTA_CREDITO ? "N. CRÉDITO" : "FACTURA",
                        registro.tipoComprobante == TipoComprobante.NOTA_CREDITO
                                ? "NC (ref. " + textoOVacio(registro.comprobanteOriginalId) + ")"
                                : (registro.numeroTicket != null ? registro.numeroTicket : ""),
                        registro.fechaEmision != null ? registro.fechaEmision.format(FORMATO_FECHA) : "",
                        registro.estado,
                        registro.secuencial,
                        registro.claveAcceso,
                        registro.numeroAutorizacion != null ? registro.numeroAutorizacion : "",
                        registro.intentos,
                        registro.motivo != null ? registro.motivo : "",
                        registro.mensajeError != null ? registro.mensajeError : ""
                });
                registrosPorFila.add(registro);
            }
            etiquetaEstado.setText(historial.size() + " comprobante(s).");
        } catch (Exception e) {
            etiquetaEstado.setText("No se pudo cargar el historial: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "No se pudo cargar el historial:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ComprobanteRepository.RegistroHistorial registroSeleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            return null;
        }
        return registrosPorFila.get(tabla.convertRowIndexToModel(fila));
    }

    private ComprobanteRepository.RegistroHistorial registroSeleccionadoConAviso() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionado();
        if (registro == null) {
            JOptionPane.showMessageDialog(this, "Selecciona primero una fila de la tabla.", "Nada seleccionado", JOptionPane.WARNING_MESSAGE);
        }
        return registro;
    }

    private void actualizarBotones() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionado();
        boolean esFactura = registro != null && registro.tipoComprobante == TipoComprobante.FACTURA;
        botonAnular.setEnabled(esFactura && registro.estado == EstadoComprobante.AUTORIZADO);
        botonReintentar.setEnabled(esFactura &&
                (registro.estado == EstadoComprobante.ERROR || registro.estado == EstadoComprobante.RECHAZADO
                        || registro.estado == EstadoComprobante.ENVIADO));
        botonCorreo.setEnabled(registro != null && registro.estado == EstadoComprobante.AUTORIZADO);
    }

    private void verXml() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionadoConAviso();
        if (registro == null) {
            return;
        }
        try {
            DataSource dataSource = ConexionLoader.cargar(archivoConexion);
            var xml = new ComprobanteRepository(dataSource.getConnection()).obtenerXml(registro.ticketId);
            if (xml.isEmpty() || xml.get().masReciente() == null) {
                JOptionPane.showMessageDialog(this, "Este comprobante todavía no tiene ningún XML generado.", "Sin XML", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            mostrarDialogoXml(xml.get().masReciente());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo obtener el XML:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarDialogoXml(String xml) {
        JTextArea area = new JTextArea(xml, 30, 90);
        area.setEditable(false);
        area.setLineWrap(false);

        JButton botonGuardar = new JButton("Guardar como...");
        botonGuardar.addActionListener(e -> guardarArchivo(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8), "comprobante.xml"));

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        JPanel panelBoton = new JPanel();
        panelBoton.add(botonGuardar);
        panel.add(panelBoton, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, panel, "XML del comprobante", JOptionPane.PLAIN_MESSAGE);
    }

    private void verRide() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionadoConAviso();
        if (registro == null) {
            return;
        }
        try {
            byte[] pdf = generarRide(registro);
            if (pdf == null) {
                return;
            }
            File temporal = File.createTempFile("ride-" + registro.ticketId, ".pdf");
            temporal.deleteOnExit();
            Files.write(temporal.toPath(), pdf);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(temporal);
            } else {
                JOptionPane.showMessageDialog(this, "RIDE generado en:\n" + temporal.getAbsolutePath(), "RIDE generado", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo generar el RIDE:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Genera el RIDE del registro dado (factura o nota de credito) contra el XML mas reciente guardado. Null si no hay XML todavia (ya avisado al usuario). */
    private byte[] generarRide(ComprobanteRepository.RegistroHistorial registro) throws Exception {
        DataSource dataSource = ConexionLoader.cargar(archivoConexion);
        var xml = new ComprobanteRepository(dataSource.getConnection()).obtenerXml(registro.ticketId);
        if (xml.isEmpty() || xml.get().masReciente() == null) {
            JOptionPane.showMessageDialog(this, "Este comprobante todavía no tiene ningún XML generado.", "Sin XML", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        if (registro.tipoComprobante == TipoComprobante.NOTA_CREDITO) {
            return RideNotaCreditoGenerator.generar(xml.get().masReciente(), xml.get().fechaAutorizacion);
        }
        return RideGenerator.generar(xml.get().masReciente(), xml.get().fechaAutorizacion);
    }

    private void anular() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionadoConAviso();
        if (registro == null) {
            return;
        }
        String descripcion = "Ticket #" + registro.numeroTicket + " (secuencial " + registro.secuencial + ")";
        new AnulacionFrame(this, archivoConexion, registro.ticketId, descripcion, ticketIdNc -> cargarHistorial())
                .setVisible(true);
    }

    private void reintentar() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionadoConAviso();
        if (registro == null) {
            return;
        }
        int confirmacion = JOptionPane.showConfirmDialog(this,
                "Esto vuelve a generar, firmar y enviar este comprobante al SRI con los datos actuales del ticket.\n¿Continuar?",
                "Confirmar reintento", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmacion != JOptionPane.YES_OPTION) {
            return;
        }

        etiquetaEstado.setText("Reintentando envío, un momento...");
        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try {
                    DatosEmisor emisor = ConfiguracionLoader.cargar(rutaEmisorPorDefecto());
                    DataSource dataSource = ConexionLoader.cargar(archivoConexion);
                    new ConectorPrincipal(emisor, dataSource.getConnection()).procesarTicket(registro.ticketId);
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(HistorialFrame.this,
                            "El reintento no terminó en AUTORIZADO:\n" + error.getMessage(),
                            "Reintento", JOptionPane.WARNING_MESSAGE);
                }
                cargarHistorial();
            }
        }.execute();
    }

    private void enviarPorCorreo() {
        ComprobanteRepository.RegistroHistorial registro = registroSeleccionadoConAviso();
        if (registro == null) {
            return;
        }
        if (!Files.exists(rutaCorreoPorDefecto())) {
            JOptionPane.showMessageDialog(this,
                    "Todavía no configuraste el correo saliente. Usa el botón \"Configurar correo...\" primero.",
                    "Falta configuración", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String destinatario = JOptionPane.showInputDialog(this, "Correo del comprador:", "Enviar por correo", JOptionPane.QUESTION_MESSAGE);
        if (destinatario == null || destinatario.isBlank()) {
            return;
        }

        etiquetaEstado.setText("Enviando por correo, un momento...");
        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try {
                    DataSource dataSource = ConexionLoader.cargar(archivoConexion);
                    var xml = new ComprobanteRepository(dataSource.getConnection()).obtenerXml(registro.ticketId);
                    if (xml.isEmpty() || xml.get().masReciente() == null) {
                        throw new IllegalStateException("Este comprobante todavía no tiene ningún XML generado.");
                    }
                    byte[] pdf = generarRide(registro);
                    ConfiguracionCorreo config = ConfiguracionCorreoLoader.cargar(rutaCorreoPorDefecto());
                    NotificadorCorreo notificador = new NotificadorCorreo(config);

                    String tipo = registro.tipoComprobante == TipoComprobante.NOTA_CREDITO ? "Nota de Crédito" : "Factura";
                    notificador.enviarComprobante(destinatario,
                            tipo + " electrónica - " + registro.secuencial,
                            "Adjunto el comprobante electrónico autorizado por el SRI (XML y representación impresa en PDF).",
                            "comprobante-" + registro.secuencial + ".xml", xml.get().masReciente().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "comprobante-" + registro.secuencial + ".pdf", pdf);
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(HistorialFrame.this,
                            "No se pudo enviar el correo:\n" + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(HistorialFrame.this, "Correo enviado a " + destinatario + ".", "Listo", JOptionPane.INFORMATION_MESSAGE);
                }
                etiquetaEstado.setText(" ");
            }
        }.execute();
    }

    private void guardarArchivo(byte[] contenido, String nombreSugerido) {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File(nombreSugerido));
        if (selector.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(selector.getSelectedFile().toPath(), contenido);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String textoOVacio(String valor) {
        return valor == null ? "" : valor;
    }

    /**
     * Colorea cada fila segun el estado del comprobante (verde/rojo/amarillo,
     * para detectar rechazos/errores de un vistazo), con un cuarto color
     * (naranja) para los que llevan mas de {@link #UMBRAL_ATASCADO} sin
     * resolverse - aviso operativo para que no se olviden en ENVIADO/ERROR
     * indefinidamente, no una cita textual de un plazo legal del SRI.
     */
    private class RenderizadorPorEstado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionado,
                                                         boolean tieneFoco, int fila, int columna) {
            Component componente = super.getTableCellRendererComponent(tabla, valor, seleccionado, tieneFoco, fila, columna);
            if (!seleccionado) {
                int filaModelo = tabla.convertRowIndexToModel(fila);
                ComprobanteRepository.RegistroHistorial registro = registrosPorFila.get(filaModelo);
                componente.setBackground(colorParaRegistro(registro));
            }
            return componente;
        }

        private Color colorParaRegistro(ComprobanteRepository.RegistroHistorial registro) {
            if (estaAtascado(registro)) {
                return new Color(250, 224, 180);
            } else if (registro.estado == EstadoComprobante.AUTORIZADO) {
                return new Color(214, 245, 214);
            } else if (registro.estado == EstadoComprobante.RECHAZADO || registro.estado == EstadoComprobante.ERROR) {
                return new Color(250, 214, 214);
            } else {
                return new Color(255, 244, 214);
            }
        }
    }

    private static boolean estaAtascado(ComprobanteRepository.RegistroHistorial registro) {
        if (registro.fechaEmision == null) {
            return false;
        }
        boolean estadoPendiente = registro.estado == EstadoComprobante.ENVIADO || registro.estado == EstadoComprobante.ERROR;
        return estadoPendiente && Duration.between(registro.fechaEmision, LocalDateTime.now()).compareTo(UMBRAL_ATASCADO) > 0;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // se sigue con el look and feel por defecto si el del sistema no esta disponible
        }
        SwingUtilities.invokeLater(() -> new HistorialFrame().setVisible(true));
    }
}
