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
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Anula una factura ya AUTORIZADA emitiendo una Nota de Credito
 * ({@link AnulacionService}) - dialogo modal lanzado desde el boton "Anular
 * factura" del {@link HistorialFrame} (misma JVM, no un proceso nuevo: la
 * ventana de Historial ya esta corriendo, solo hace falta un dialogo
 * encima). Bloquea la ventana mientras firma/envia/consulta (llamada
 * sincrona de red) mostrando un aviso de progreso, y al terminar informa el
 * resultado y refresca el historial via el callback {@code alTerminar}.
 */
public class AnulacionFrame extends JDialog {

    private final Path archivoConexion;
    private final Path archivoEmisor;
    private final String ticketIdFactura;
    private final Consumer<String> alTerminar;

    private final JTextArea campoMotivo = new JTextArea(4, 30);
    private final JButton botonEmitir = new JButton("Emitir Nota de Crédito");
    private final JLabel etiquetaEstado = new JLabel(" ");

    public AnulacionFrame(Frame propietario, Path archivoConexion, String ticketIdFactura,
                           String descripcionFactura, Consumer<String> alTerminar) {
        this(propietario, archivoConexion, RutasConector.resolver("config/datos-emisor.properties"), ticketIdFactura, descripcionFactura, alTerminar);
    }

    public AnulacionFrame(Frame propietario, Path archivoConexion, Path archivoEmisor, String ticketIdFactura,
                           String descripcionFactura, Consumer<String> alTerminar) {
        super(propietario, "Anular factura (Nota de Crédito)", true);
        this.archivoConexion = archivoConexion;
        this.archivoEmisor = archivoEmisor;
        this.ticketIdFactura = ticketIdFactura;
        this.alTerminar = alTerminar;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel panelSuperior = new JPanel(new GridLayout(0, 1, 4, 4));
        panelSuperior.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        panelSuperior.add(new JLabel("Factura a anular: " + descripcionFactura));
        panelSuperior.add(new JLabel("Se emitirá una Nota de Crédito por el valor total de la factura."));
        add(panelSuperior, BorderLayout.NORTH);

        JPanel panelCentro = new JPanel(new BorderLayout(5, 5));
        panelCentro.setBorder(BorderFactory.createEmptyBorder(0, 15, 5, 15));
        panelCentro.add(new JLabel("Motivo de la anulación (obligatorio):"), BorderLayout.NORTH);
        campoMotivo.setLineWrap(true);
        campoMotivo.setWrapStyleWord(true);
        panelCentro.add(new JScrollPane(campoMotivo), BorderLayout.CENTER);
        add(panelCentro, BorderLayout.CENTER);

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));
        panelInferior.add(etiquetaEstado, BorderLayout.WEST);
        botonEmitir.addActionListener(e -> emitir());
        JPanel panelBoton = new JPanel();
        panelBoton.add(botonEmitir);
        panelInferior.add(panelBoton, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(480, 260));
        pack();
        setLocationRelativeTo(propietario);
    }

    private void emitir() {
        String motivo = campoMotivo.getText().trim();
        if (motivo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El motivo es obligatorio.", "Falta el motivo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirmacion = JOptionPane.showConfirmDialog(this,
                "Esto va a emitir y enviar al SRI una Nota de Crédito real por el valor total de la factura.\n¿Continuar?",
                "Confirmar anulación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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
                    return servicio.anular(ticketIdFactura, motivo);
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
}
