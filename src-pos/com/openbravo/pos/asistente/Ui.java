package com.openbravo.pos.asistente;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Piezas visuales comunes de los pasos del asistente (paleta Selva, letras grandes). */
final class Ui {

    static final Color SELVA = new Color(0x1B5E3F);
    static final Color QUINDE = new Color(0x2E9E6B);
    static final Color BROTE = new Color(0xA8E6C1);
    static final Color CORAL = new Color(0xF2705E);

    private Ui() {
    }

    static boolean oscuro() {
        return com.formdev.flatlaf.FlatLaf.isLafDark();
    }

    static Color acento() {
        return oscuro() ? BROTE : SELVA;
    }

    static Color suave() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }

    /** Columna vertical con separacion entre bloques. */
    static JPanel columna() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    /** Ancho de los textos largos (para que se partan en lineas y no se corten). */
    static final int ANCHO_TEXTO = 430;

    static JLabel texto(String html) {
        JLabel l = new JLabel("<html><body style='width:" + ANCHO_TEXTO + "px'>" + html + "</body></html>");
        l.setFont(l.getFont().deriveFont(15f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JLabel nota(String html) {
        JLabel l = new JLabel("<html><body style='width:" + ANCHO_TEXTO + "px'>" + html + "</body></html>");
        l.setFont(l.getFont().deriveFont(13f));
        l.setForeground(suave());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JLabel subtitulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 16f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(14, 0, 6, 0));
        return l;
    }

    /** Formulario de dos columnas: etiqueta a la izquierda, campo a la derecha. */
    static JPanel formulario(Object... pares) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 12);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < pares.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JLabel etiqueta = new JLabel(String.valueOf(pares[i]));
            etiqueta.setFont(etiqueta.getFont().deriveFont(14f));
            p.add(etiqueta, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            Component campo = (Component) pares[i + 1];
            if (campo instanceof JComponent) {
                ((JComponent) campo).setFont(((JComponent) campo).getFont().deriveFont(15f));
            }
            p.add(campo, c);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** Recuadro suave para agrupar contenido. */
    static JPanel recuadro(Component contenido) {
        JPanel p = new JPanel(new BorderLayout());
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.LIGHT_GRAY, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        p.add(contenido, BorderLayout.CENTER);
        return p;
    }
}
