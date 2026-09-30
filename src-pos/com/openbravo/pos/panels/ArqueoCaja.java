package com.openbravo.pos.panels;

import com.openbravo.format.Formats;
import com.openbravo.pos.forms.AppView;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Arqueo de caja por denominacion con cierre ciego: primero el cajero cuenta
 * billetes y monedas SIN ver cuanto deberia haber; recien despues se muestra
 * lo esperado en efectivo y la diferencia (sobrante / faltante). El resultado
 * se guarda en ecopos_arqueos junto al cierre.
 */
public final class ArqueoCaja {

    private static final Logger LOG = Logger.getLogger(ArqueoCaja.class.getName());

    /** Denominaciones de Ecuador (dolar estadounidense). */
    private static final double[] DENOMINACIONES = {100, 50, 20, 10, 5, 1, 0.50, 0.25, 0.10, 0.05, 0.01};

    /** Resultado del arqueo, o null si el cajero cancelo. */
    public static final class Resultado {
        public final double fondo;
        public final double contado;
        public final double esperado;
        public final String detalle;

        Resultado(double fondo, double contado, double esperado, String detalle) {
            this.fondo = fondo;
            this.contado = contado;
            this.esperado = esperado;
            this.detalle = detalle;
        }

        public double diferencia() {
            return Math.round((contado - fondo - esperado) * 100.0) / 100.0;
        }
    }

    private ArqueoCaja() {
    }

    /**
     * Muestra el arqueo. {@code esperadoEfectivo} = cobros en efectivo +
     * entradas - salidas de caja del turno (sin el fondo inicial).
     */
    public static Resultado mostrar(Component padre, final double esperadoEfectivo) {
        Window ventana = padre instanceof Window ? (Window) padre : SwingUtilities.getWindowAncestor(padre);
        final JDialog dialogo = new JDialog(ventana, "Arqueo de caja", Dialog.ModalityType.APPLICATION_MODAL);
        final CardLayout pasos = new CardLayout();
        final JPanel contenido = new JPanel(pasos);
        contenido.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        // --- Paso 1: contar (ciego) ---
        final JSpinner[] cantidades = new JSpinner[DENOMINACIONES.length];
        final JSpinner fondo = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 1.0));
        final JLabel totalContado = new JLabel();
        totalContado.setFont(totalContado.getFont().deriveFont(Font.BOLD, 22f));

        JPanel grilla = new JPanel(new GridLayout(0, 4, 10, 6));
        ChangeListener recalcular = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                totalContado.setText("Contado: " + Formats.CURRENCY.formatValue(sumar(cantidades)));
            }
        };
        for (int i = 0; i < DENOMINACIONES.length; i++) {
            JLabel etiqueta = new JLabel(Formats.CURRENCY.formatValue(DENOMINACIONES[i]) + " \u00D7",
                    SwingConstants.RIGHT);
            etiqueta.setFont(etiqueta.getFont().deriveFont(DENOMINACIONES[i] >= 1 ? Font.BOLD : Font.PLAIN, 15f));
            cantidades[i] = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
            cantidades[i].setFont(cantidades[i].getFont().deriveFont(15f));
            cantidades[i].addChangeListener(recalcular);
            grilla.add(etiqueta);
            grilla.add(cantidades[i]);
        }
        JPanel filaFondo = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        filaFondo.add(new JLabel("Fondo de caja (base que se deja para ma\u00F1ana):"));
        fondo.setPreferredSize(new java.awt.Dimension(110, fondo.getPreferredSize().height));
        filaFondo.add(fondo);

        JLabel instruccion = new JLabel("<html><b>Cuenta el efectivo de la caja.</b> Escribe cu\u00E1ntos billetes y monedas "
                + "hay de cada valor. Lo esperado se muestra despu\u00E9s de contar.</html>");
        JPanel paso1 = new JPanel(new BorderLayout(0, 12));
        paso1.add(instruccion, BorderLayout.NORTH);
        paso1.add(grilla, BorderLayout.CENTER);
        JPanel sur1 = new JPanel(new BorderLayout(0, 8));
        sur1.add(filaFondo, BorderLayout.NORTH);
        JPanel botones1 = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        JButton cancelar = new JButton("Cancelar");
        JButton continuar = new JButton("Continuar");
        continuar.setFont(continuar.getFont().deriveFont(Font.BOLD));
        botones1.add(cancelar);
        botones1.add(continuar);
        JPanel pie1 = new JPanel(new BorderLayout());
        pie1.add(totalContado, BorderLayout.WEST);
        pie1.add(botones1, BorderLayout.EAST);
        sur1.add(pie1, BorderLayout.SOUTH);
        paso1.add(sur1, BorderLayout.SOUTH);

        // --- Paso 2: comparar ---
        final JLabel esperado = new JLabel();
        final JLabel contado = new JLabel();
        final JLabel diferencia = new JLabel();
        diferencia.setFont(diferencia.getFont().deriveFont(Font.BOLD, 26f));
        JPanel datos = new JPanel(new GridLayout(0, 1, 0, 14));
        datos.add(esperado);
        datos.add(contado);
        datos.add(diferencia);
        JPanel paso2 = new JPanel(new BorderLayout(0, 12));
        paso2.add(new JLabel("<html><b>Resultado del arqueo</b></html>"), BorderLayout.NORTH);
        esperado.setFont(esperado.getFont().deriveFont(15f));
        contado.setFont(contado.getFont().deriveFont(15f));
        JPanel arribaDatos = new JPanel(new BorderLayout());
        arribaDatos.add(datos, BorderLayout.NORTH);
        paso2.add(arribaDatos, BorderLayout.CENTER);
        JPanel botones2 = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        JButton recontar = new JButton("Volver a contar");
        JButton cerrar = new JButton("Cerrar caja");
        cerrar.setFont(cerrar.getFont().deriveFont(Font.BOLD));
        botones2.add(recontar);
        botones2.add(cerrar);
        paso2.add(botones2, BorderLayout.SOUTH);

        contenido.add(paso1, "contar");
        contenido.add(paso2, "comparar");
        dialogo.setContentPane(contenido);

        final Resultado[] resultado = new Resultado[1];
        cancelar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialogo.dispose();
            }
        });
        continuar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double base = ((Number) fondo.getValue()).doubleValue();
                double total = sumar(cantidades);
                Resultado r = new Resultado(base, total, esperadoEfectivo, detalle(cantidades));
                esperado.setText("Esperado en efectivo (ventas + entradas \u2212 salidas): " + Formats.CURRENCY.formatValue(esperadoEfectivo));
                contado.setText("Contado " + Formats.CURRENCY.formatValue(total) + " \u2212 fondo " + Formats.CURRENCY.formatValue(base)
                        + " = " + Formats.CURRENCY.formatValue(total - base));
                double d = r.diferencia();
                if (Math.abs(d) < 0.005) {
                    diferencia.setText("\u2714  Cuadra exacto");
                    diferencia.setForeground(new Color(0x1B5E3F));
                } else {
                    diferencia.setText((d > 0 ? "Sobrante: " : "Faltante: ") + Formats.CURRENCY.formatValue(Math.abs(d)));
                    diferencia.setForeground(d > 0 ? new Color(0xB26A00) : new Color(0xC62828));
                }
                resultado[0] = r;
                pasos.show(contenido, "comparar");
            }
        });
        recontar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resultado[0] = null;
                pasos.show(contenido, "contar");
            }
        });
        final boolean[] confirmado = new boolean[1];
        cerrar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmado[0] = true;
                dialogo.dispose();
            }
        });

        recalcular.stateChanged(null);
        dialogo.pack();
        dialogo.setLocationRelativeTo(ventana);
        dialogo.setVisible(true);
        return confirmado[0] ? resultado[0] : null;
    }

    /** Guarda el arqueo del turno que se esta cerrando. Nunca impide el cierre si falla. */
    public static void guardar(AppView app, String idCaja, Resultado r) {
        try {
            Connection con = app.getSession().getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ecopos_arqueos (id, caja, fecha, usuario, fondo, contado, esperado, diferencia, detalle) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, idCaja);
                ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                ps.setString(4, app.getAppUserView().getUser().getName());
                ps.setDouble(5, r.fondo);
                ps.setDouble(6, r.contado);
                ps.setDouble(7, r.esperado);
                ps.setDouble(8, r.diferencia());
                ps.setString(9, r.detalle);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo guardar el arqueo de caja", e);
        }
    }

    private static double sumar(JSpinner[] cantidades) {
        double total = 0;
        for (int i = 0; i < cantidades.length; i++) {
            total += ((Number) cantidades[i].getValue()).intValue() * DENOMINACIONES[i];
        }
        return Math.round(total * 100.0) / 100.0;
    }

    private static String detalle(JSpinner[] cantidades) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cantidades.length; i++) {
            int n = ((Number) cantidades[i].getValue()).intValue();
            if (n > 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(n).append(" x ").append(Formats.CURRENCY.formatValue(DENOMINACIONES[i]));
            }
        }
        return sb.toString();
    }

    static boolean esMovimientoDeEfectivo(String tipo) {
        return "cash".equals(tipo) || "cashin".equals(tipo) || "cashout".equals(tipo) || "cashrefund".equals(tipo);
    }

}
