package com.openbravo.pos.forms;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * Logo que se ve nitido con cualquier escala de pantalla de Windows (125%, 150%...).
 * Un ImageIcon normal estira la imagen de 1x y se ve pixeleada; este icono carga
 * tambien las versiones nombre@2x.png y nombre@3x.png y dibuja la que corresponde.
 */
public final class LogoNitido implements Icon {

    private final BufferedImage[] imagenes;
    private final int ancho;
    private final int alto;

    private LogoNitido(BufferedImage[] imagenes) {
        this.imagenes = imagenes;
        this.ancho = imagenes[0].getWidth();
        this.alto = imagenes[0].getHeight();
    }

    /**
     * Carga "base.png" (tamano real en pantalla al 100%) y, si existen, "base@2x.png" y
     * "base@3x.png". Si no encuentra la imagen devuelve null.
     */
    public static Icon cargar(String base) {
        BufferedImage uno = leer(base + ".png");
        if (uno == null) {
            return null;
        }
        List<BufferedImage> lista = new ArrayList<>();
        lista.add(uno);
        for (String sufijo : new String[]{"@2x", "@3x"}) {
            BufferedImage img = leer(base + sufijo + ".png");
            if (img != null) {
                lista.add(img);
            }
        }
        return new LogoNitido(lista.toArray(new BufferedImage[lista.size()]));
    }

    /** Iconos de la ventana (barra de titulo y de tareas) en todos los tamanos disponibles. */
    public static List<Image> iconosVentana() {
        List<Image> iconos = new ArrayList<>();
        for (String t : new String[]{"16", "20", "24", "32", "40", "48", "128", "256"}) {
            BufferedImage img = leer("/com/openbravo/images/favicon-" + t + ".png");
            if (img != null) {
                iconos.add(img);
            }
        }
        BufferedImage normal = leer("/com/openbravo/images/favicon.png");
        if (normal != null) {
            iconos.add(normal);
        }
        return iconos;
    }

    private static BufferedImage leer(String ruta) {
        try (InputStream in = LogoNitido.class.getResourceAsStream(ruta)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            double escala = Math.max(Math.abs(g2.getTransform().getScaleX()), 1.0);
            // La mas chica que alcance para los pixeles reales; si ninguna alcanza, la mas grande.
            BufferedImage elegida = imagenes[imagenes.length - 1];
            for (BufferedImage img : imagenes) {
                if (img.getWidth() >= ancho * escala - 0.5) {
                    elegida = img;
                    break;
                }
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(elegida, x, y, ancho, alto, null);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return ancho;
    }

    @Override
    public int getIconHeight() {
        return alto;
    }
}
