package com.openbravo.pos.sales;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Ventanas de aviso y de pregunta para los scripts de la venta (BeanShell, en Recursos).
 * A diferencia de llamar a JOptionPane con null, la ventana sale sobre la app (no detras),
 * se crea en el hilo de Swing y usa el tema de Quinde POS. Uso desde un script:
 * <pre>
 *   com.openbravo.pos.sales.Dialogos.aviso("Cocina", "Pedido enviado a cocina");
 *   nota = com.openbravo.pos.sales.Dialogos.pedirTexto("Nota", "Nota para esta linea", "");
 * </pre>
 */
public final class Dialogos {

    private Dialogos() {
    }

    /** La ventana activa de la app, para que el dialogo salga encima de ella. */
    static Component padre() {
        Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return w != null ? w : null;
    }

    public static void aviso(String titulo, String mensaje) {
        mostrar(titulo, mensaje, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void advertencia(String titulo, String mensaje) {
        mostrar(titulo, mensaje, JOptionPane.WARNING_MESSAGE);
    }

    private static void mostrar(final String titulo, final String mensaje, final int tipo) {
        enHiloSwing(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(padre(), mensaje, titulo, tipo);
            }
        });
    }

    /** Pide un texto; devuelve null si el usuario cancela. */
    public static String pedirTexto(final String titulo, final String pregunta, final String inicial) {
        final Object[] r = new Object[1];
        enHiloSwing(new Runnable() {
            @Override
            public void run() {
                r[0] = JOptionPane.showInputDialog(padre(), pregunta, titulo, JOptionPane.QUESTION_MESSAGE,
                        null, null, inicial == null ? "" : inicial);
            }
        });
        return r[0] == null ? null : r[0].toString();
    }

    /**
     * Pide un porcentaje entre 0 y 100 (acepta "10", "10%" o "12,5"); null si cancela.
     * Si escribe algo invalido vuelve a preguntar.
     */
    public static Double pedirPorcentaje(String titulo, String pregunta) {
        String error = null;
        while (true) {
            String texto = pedirTexto(titulo, error == null ? pregunta : error + "\n\n" + pregunta, "");
            if (texto == null) {
                return null;
            }
            Double p = leerPorcentaje(texto);
            if (p != null) {
                return p;
            }
            error = "\"" + texto.trim() + "\" no es un porcentaje entre 0 y 100.";
        }
    }

    /** "10", "10%", " 12,5 " -> 10.0 / 12.5; null si no es un numero entre 0 y 100. */
    static Double leerPorcentaje(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim().replace("%", "").replace(',', '.').trim();
        try {
            double v = Double.parseDouble(t);
            return v >= 0 && v <= 100 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void enHiloSwing(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
