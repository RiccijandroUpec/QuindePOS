package com.openbravo.pos.sri;

import com.openbravo.pos.forms.AppView;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Aviso discreto, abajo a la derecha, despues de cobrar una venta con
 * factura electronica: "Enviando factura al SRI..." y luego "Factura
 * 001-001-000000123 autorizada" (o que hay que revisarla, con el motivo en
 * palabras simples). No bloquea la caja: se cierra solo, y al tocarlo abre
 * "Comprobantes electronicos".
 */
public final class AvisoFactura {

    private static final int CONSULTAR_CADA_MS = 1500;
    private static final int ESPERA_MAXIMA_MS = 45000;
    private static final String PANTALLA_COMPROBANTES = "com.openbravo.pos.sri.JPanelComprobantesSri";

    private final AppView app;
    private final EcoPosSriBridge puente;
    private final String ticketId;
    private final JWindow ventana;
    private final JLabel texto = new JLabel();
    private final JPanel caja = new JPanel(new BorderLayout());
    private final long inicio = System.currentTimeMillis();
    private Timer consulta;

    private AvisoFactura(Component padre, AppView app, EcoPosSriBridge puente, String ticketId) {
        this.app = app;
        this.puente = puente;
        this.ticketId = ticketId;
        Window duena = padre instanceof Window ? (Window) padre : SwingUtilities.getWindowAncestor(padre);
        ventana = new JWindow(duena);
        ventana.setFocusableWindowState(false);
        texto.setFont(texto.getFont().deriveFont(Font.BOLD, 13f));
        caja.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        caja.setBackground(UIManager.getColor("Panel.background"));
        caja.add(texto, BorderLayout.CENTER);
        caja.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        caja.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cerrar();
                try {
                    AvisoFactura.this.app.getAppUserView().showTask(PANTALLA_COMPROBANTES);
                } catch (Exception ignorado) {
                    // sin permiso para ver comprobantes: solo se cierra el aviso
                }
            }
        });
        ventana.setContentPane(caja);
    }

    /** Muestra el aviso y sigue el estado de la factura del ticket hasta que se resuelva. Nunca lanza. */
    public static void seguir(Component padre, AppView app, EcoPosSriBridge puente, String ticketId) {
        try {
            new AvisoFactura(padre, app, puente, ticketId).iniciar();
        } catch (Exception ignorado) {
            // un aviso que no se puede mostrar no debe afectar la venta
        }
    }

    private void iniciar() {
        mostrar("\u25CB  Enviando la factura al SRI\u2026", null);
        consulta = new Timer(CONSULTAR_CADA_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                revisar();
            }
        });
        consulta.start();
    }

    private void revisar() {
        String[] estado;
        try {
            estado = puente.estadoFacturaDeTicket(ticketId);
        } catch (Exception e) {
            estado = null;
        }
        boolean agotado = System.currentTimeMillis() - inicio > ESPERA_MAXIMA_MS;
        if (estado != null && "AUTORIZADO".equals(estado[0])) {
            consulta.stop();
            mostrar("\u2714  Factura " + estado[1] + " autorizada", new Color(0x2E7D32));
            cerrarEn(5000);
        } else if (estado != null && "RECHAZADO".equals(estado[0])) {
            consulta.stop();
            mostrar("<html><div style='width:320px'>\u26A0  La factura " + estado[1] + " fue rechazada por el SRI."
                    + (estado[2] == null ? "" : "<br><span style='font-weight:normal'>" + estado[2] + "</span>")
                    + "<br><u>Toca aqu\u00ED para revisarla</u></div></html>", new Color(0xC62828));
            cerrarEn(12000);
        } else if (agotado) {
            consulta.stop();
            String numero = estado == null ? "" : " " + estado[1];
            mostrar("<html><div style='width:320px'>\u25CB  La factura" + numero + " sigue en proceso."
                    + (estado != null && estado[2] != null ? "<br><span style='font-weight:normal'>" + estado[2] + "</span>" : "")
                    + "<br><span style='font-weight:normal'>EcoPos la reintenta sola.</span></div></html>", new Color(0xB26A00));
            cerrarEn(8000);
        }
    }

    private void mostrar(String mensaje, Color color) {
        texto.setText(mensaje);
        texto.setForeground(color == null ? UIManager.getColor("Label.foreground") : color);
        ventana.pack();
        Window duena = ventana.getOwner();
        if (duena != null && duena.isShowing()) {
            ventana.setLocation(duena.getX() + duena.getWidth() - ventana.getWidth() - 24,
                    duena.getY() + duena.getHeight() - ventana.getHeight() - 24);
        }
        ventana.setVisible(true);
    }

    private void cerrarEn(int milisegundos) {
        Timer t = new Timer(milisegundos, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cerrar();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    private void cerrar() {
        if (consulta != null) {
            consulta.stop();
        }
        ventana.setVisible(false);
        ventana.dispose();
    }
}
