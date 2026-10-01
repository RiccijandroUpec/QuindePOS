package com.openbravo.pos.sales;

import com.openbravo.format.Formats;
import com.openbravo.pos.payment.PaymentInfo;
import com.openbravo.pos.ticket.TicketInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Aviso del cambio a entregar al terminar una venta en efectivo (lo llama el recurso
 * Ticket.Close). Suma todos los pagos en efectivo (tambien en un pago dividido), no sale
 * si no hay cambio, no quita el foco a la venta siguiente y se cierra solo o con un toque.
 */
public final class AvisoCambio {

    private static final int SEGUNDOS_VISIBLE = 8;
    private static JDialog actual;

    private AvisoCambio() {
    }

    public static void mostrar(TicketInfo ticket) {
        if (ticket == null || ticket.getPayments() == null) {
            return;
        }
        double cambio = 0;
        double recibido = 0;
        for (PaymentInfo p : ticket.getPayments()) {
            if ("cash".equals(p.getName())) {
                cambio += p.getChange();
                recibido += p.getPaid();
            }
        }
        if (cambio < 0.005) {
            return; // pago exacto o sin efectivo: no hace falta avisar
        }
        final String textoCambio = Formats.CURRENCY.formatValue(cambio);
        final String detalle = "Recibido " + Formats.CURRENCY.formatValue(recibido)
                + "  \u00B7  Total " + Formats.CURRENCY.formatValue(ticket.getTotal());
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                abrir(textoCambio, detalle);
            }
        });
    }

    private static void abrir(String textoCambio, String detalle) {
        cerrar();
        Component padre = Dialogos.padre();
        Window ventana = padre instanceof Window ? (Window) padre : null;
        final JDialog d = new JDialog(ventana);
        d.setUndecorated(true);
        // Sin foco: la venta siguiente (teclado o lector de codigos) sigue funcionando debajo.
        d.setFocusableWindowState(false);

        boolean oscuro = com.formdev.flatlaf.FlatLaf.isLafDark();
        Color fondo = oscuro ? new Color(0x10231A) : Color.WHITE;
        Color acento = oscuro ? new Color(0xA8E6C1) : new Color(0x1B5E3F);
        Color texto = UIManager.getColor("Label.foreground");
        Color suave = UIManager.getColor("Label.disabledForeground");

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(fondo);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(acento, 3),
                BorderFactory.createEmptyBorder(18, 40, 16, 40)));

        JLabel titulo = new JLabel("CAMBIO", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        titulo.setForeground(texto);
        JLabel monto = new JLabel(textoCambio, SwingConstants.CENTER);
        monto.setFont(monto.getFont().deriveFont(Font.BOLD, 64f));
        monto.setForeground(acento);
        JPanel abajo = new JPanel(new GridLayout(2, 1, 0, 4));
        abajo.setOpaque(false);
        JLabel det = new JLabel(detalle, SwingConstants.CENTER);
        det.setFont(det.getFont().deriveFont(16f));
        det.setForeground(texto);
        JLabel ayuda = new JLabel("Toca para cerrar", SwingConstants.CENTER);
        ayuda.setFont(ayuda.getFont().deriveFont(12f));
        ayuda.setForeground(suave);
        abajo.add(det);
        abajo.add(ayuda);
        panel.add(titulo, BorderLayout.NORTH);
        panel.add(monto, BorderLayout.CENTER);
        panel.add(abajo, BorderLayout.SOUTH);
        d.setContentPane(panel);
        d.pack();
        d.setLocationRelativeTo(ventana);

        MouseAdapter clic = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cerrar();
            }
        };
        panel.addMouseListener(clic);
        Timer temporizador = new Timer(SEGUNDOS_VISIBLE * 1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (actual == d) {
                    cerrar();
                }
            }
        });
        temporizador.setRepeats(false);
        temporizador.start();
        actual = d;
        d.setVisible(true);
    }

    private static void cerrar() {
        if (actual != null) {
            actual.dispose();
            actual = null;
        }
    }
}
