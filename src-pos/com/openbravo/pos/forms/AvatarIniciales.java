package com.openbravo.pos.forms;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** Circulo de color con las iniciales del usuario - para las tarjetas del login moderno. */
public final class AvatarIniciales implements Icon {

    private static final Color[] PALETA = {
        new Color(0x2E7D32), new Color(0x1565C0), new Color(0x6A1B9A), new Color(0xAD1457),
        new Color(0xEF6C00), new Color(0x00838F), new Color(0x4E342E), new Color(0x37474F)
    };

    private final String iniciales;
    private final Color color;
    private final int tam;

    public AvatarIniciales(String nombre, int tam) {
        this.tam = tam;
        this.iniciales = iniciales(nombre);
        this.color = PALETA[Math.abs((nombre == null ? "" : nombre).hashCode()) % PALETA.length];
    }

    private static String iniciales(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return "?";
        }
        String[] partes = nombre.trim().split("\\s+");
        String resultado = partes[0].substring(0, 1);
        if (partes.length > 1) {
            resultado += partes[partes.length - 1].substring(0, 1);
        } else if (partes[0].length() > 1) {
            resultado += partes[0].substring(1, 2);
        }
        return resultado.toUpperCase();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(x, y, tam, tam);
        g2.setColor(Color.WHITE);
        g2.setFont(c.getFont().deriveFont(Font.BOLD, tam * 0.38f));
        FontMetrics fm = g2.getFontMetrics();
        int ancho = fm.stringWidth(iniciales);
        g2.drawString(iniciales, x + (tam - ancho) / 2, y + (tam - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return tam;
    }

    @Override
    public int getIconHeight() {
        return tam;
    }
}
