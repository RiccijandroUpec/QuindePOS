package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.ConectorPrincipal;
import com.openbravo.pos.sri.config.ClassLoaderPropio;
import com.openbravo.pos.sri.config.ConexionLoader;
import com.openbravo.pos.sri.config.ConfiguracionCorreoLoader;
import com.openbravo.pos.sri.config.ConfiguracionLoader;
import com.openbravo.pos.sri.config.RutasConector;
import com.openbravo.pos.sri.correo.NotificadorCorreo;
import com.openbravo.pos.sri.dominio.ConfiguracionCorreo;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.repository.ComprobanteRepository;
import com.openbravo.pos.sri.ride.RideGenerator;
import com.openbravo.pos.sri.ride.RideNotaCreditoGenerator;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Frame;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/**
 * Acciones sobre un comprobante electronico (ver RIDE/XML, reintentar,
 * enviar por correo, nota de credito), compartidas por la pantalla
 * "Comprobantes electronicos" y por la vista de una venta en EcoPos. Todas
 * trabajan dentro de la misma ventana de EcoPos (dialogos modales encima), y
 * lo que va a la red corre en segundo plano.
 */
public final class AccionesComprobante {

    private AccionesComprobante() {
    }

    static Path rutaConexion() {
        return RutasConector.resolver("config/conexion.properties");
    }

    static Path rutaEmisor() {
        return RutasConector.resolver("config/datos-emisor.properties");
    }

    static Path rutaCorreo() {
        return RutasConector.resolver("config/correo.properties");
    }

    /** Genera el RIDE (PDF) del comprobante y lo abre con el visor del sistema. */
    public static void verRide(Component padre, FilaComprobante fila) {
        try {
            byte[] pdf = generarRide(fila);
            if (pdf == null) {
                JOptionPane.showMessageDialog(padre, "Este comprobante todavía no tiene XML generado.", "Sin RIDE",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            File temporal = File.createTempFile("ride-" + fila.numero + "-", ".pdf");
            temporal.deleteOnExit();
            Files.write(temporal.toPath(), pdf);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(temporal);
            } else {
                JOptionPane.showMessageDialog(padre, "RIDE generado en:\n" + temporal.getAbsolutePath(), "RIDE",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(padre, "No se pudo generar el RIDE:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** RIDE del comprobante, o null si todavia no hay XML. */
    static byte[] generarRide(FilaComprobante fila) throws Exception {
        try (Connection con = ConexionLoader.cargar(rutaConexion()).getConnection()) {
            var xml = new ComprobanteRepository(con).obtenerXml(fila.ticketId);
            if (xml.isEmpty() || xml.get().masReciente() == null) {
                return null;
            }
            return fila.tipo == TipoComprobante.NOTA_CREDITO
                    ? RideNotaCreditoGenerator.generar(xml.get().masReciente(), xml.get().fechaAutorizacion)
                    : RideGenerator.generar(xml.get().masReciente(), xml.get().fechaAutorizacion);
        }
    }

    public static void verXml(Component padre, FilaComprobante fila) {
        try (Connection con = ConexionLoader.cargar(rutaConexion()).getConnection()) {
            var xml = new ComprobanteRepository(con).obtenerXml(fila.ticketId);
            if (xml.isEmpty() || xml.get().masReciente() == null) {
                JOptionPane.showMessageDialog(padre, "Este comprobante todavía no tiene XML generado.", "Sin XML",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String contenido = xml.get().masReciente();
            JTextArea area = new JTextArea(contenido, 28, 90);
            area.setEditable(false);
            JButton guardar = new JButton("Guardar como…");
            guardar.addActionListener(e -> {
                JFileChooser selector = new JFileChooser();
                selector.setSelectedFile(new File("comprobante-" + fila.numero + ".xml"));
                if (selector.showSaveDialog(padre) == JFileChooser.APPROVE_OPTION) {
                    try {
                        Files.write(selector.getSelectedFile().toPath(), contenido.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(padre, "No se pudo guardar:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.add(new JScrollPane(area), BorderLayout.CENTER);
            JPanel sur = new JPanel();
            sur.add(guardar);
            panel.add(sur, BorderLayout.SOUTH);
            JOptionPane.showMessageDialog(padre, panel, "XML del comprobante " + fila.numero, JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(padre, "No se pudo leer el XML:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Vuelve a generar, firmar y enviar la factura con los datos actuales del ticket. */
    public static void reintentar(Component padre, FilaComprobante fila, Runnable alTerminar) {
        int ok = JOptionPane.showConfirmDialog(padre,
                "Se vuelve a generar, firmar y enviar la factura " + fila.numero + " al SRI con los datos actuales de la venta.\n¿Continuar?",
                "Reintentar envío", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try (Connection con = ConexionLoader.cargar(rutaConexion()).getConnection()) {
                    DatosEmisor emisor = ConfiguracionLoader.cargar(rutaEmisor());
                    new ConectorPrincipal(emisor, con).procesarTicket(fila.ticketId);
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    String explicacion = MensajesSri.explicar(error.getMessage());
                    JOptionPane.showMessageDialog(padre, "No quedó autorizada todavía.\n\n"
                            + (explicacion != null ? explicacion : error.getMessage()), "Reintentar envío", JOptionPane.WARNING_MESSAGE);
                }
                if (alTerminar != null) {
                    alTerminar.run();
                }
            }
        }.execute();
    }

    /** Envia XML + RIDE por correo; propone el correo del cliente de la venta. */
    public static void enviarPorCorreo(Component padre, FilaComprobante fila) {
        if (!Files.exists(rutaCorreo())) {
            JOptionPane.showMessageDialog(padre,
                    "Todavía no configuraste el correo de salida. Hazlo en Facturación electrónica → Correo.",
                    "Falta configurar el correo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object respuesta = JOptionPane.showInputDialog(padre, "Correo del cliente:", "Enviar " + fila.numero + " por correo",
                JOptionPane.QUESTION_MESSAGE, null, null, fila.correoCliente == null ? "" : fila.correoCliente);
        if (respuesta == null || String.valueOf(respuesta).isBlank()) {
            return;
        }
        final String destinatario = String.valueOf(respuesta).trim();
        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                ClassLoaderPropio.fijarEnHiloActual();
                try (Connection con = ConexionLoader.cargar(rutaConexion()).getConnection()) {
                    var xml = new ComprobanteRepository(con).obtenerXml(fila.ticketId);
                    if (xml.isEmpty() || xml.get().masReciente() == null) {
                        throw new IllegalStateException("Este comprobante todavía no tiene XML generado.");
                    }
                    byte[] pdf = generarRide(fila);
                    ConfiguracionCorreo config = ConfiguracionCorreoLoader.cargar(rutaCorreo());
                    String xmlAutorizado = xml.get().masReciente();
                    byte[] logo = NotificadorCorreo.logoDelNegocio();
                    com.openbravo.pos.sri.correo.MensajeComprobante mensaje = com.openbravo.pos.sri.correo.MensajeComprobante
                            .armar(fila.tipo == TipoComprobante.NOTA_CREDITO, xmlAutorizado, logo != null);
                    new NotificadorCorreo(config).enviarComprobante(destinatario, mensaje,
                            xmlAutorizado.getBytes(StandardCharsets.UTF_8), pdf, logo);
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    JOptionPane.showMessageDialog(padre, "No se pudo enviar el correo:\n" + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(padre, "Enviado a " + destinatario + ".", "Correo", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }

    /** Nota de credito (total o parcial) de una factura autorizada. */
    public static void notaCredito(Component padre, FilaComprobante fila, Runnable alTerminar) {
        Frame ventana = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, padre);
        String descripcion = "Factura " + fila.numero + " · " + fila.cliente
                + (fila.numeroTicket == null ? "" : " · ticket " + fila.numeroTicket);
        new AnulacionFrame(ventana, rutaConexion(), fila.ticketId, descripcion, ticket -> {
            if (alTerminar != null) {
                alTerminar.run();
            }
        }).setVisible(true);
    }
}
