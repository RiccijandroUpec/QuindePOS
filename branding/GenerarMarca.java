import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Genera las imagenes de marca de Quinde POS a partir del icono SVG maestro y la fuente Outfit. */
public class GenerarMarca {

    static final Color TINTA = new Color(0x10231A);
    static final Color SELVA = new Color(0x1B5E3F);
    static final Color QUINDE = new Color(0x2E9E6B);
    static final Color BROTE = new Color(0xA8E6C1);
    static final Color BORDE = new Color(0xD9DDE3);

    static SVGDocument icono;
    static Font extraBold;
    static Font regular;

    public static void main(String[] a) throws Exception {
        File base = new File(a[0]);
        File salida = new File(a[1]);
        salida.mkdirs();
        icono = new SVGLoader().load(new File(base, "quinde-icono.svg").toURI().toURL());
        extraBold = Font.createFont(Font.TRUETYPE_FONT, new File(base, "Outfit-ExtraBold.ttf"));
        regular = Font.createFont(Font.TRUETYPE_FONT, new File(base, "Outfit-Regular.ttf"));

        guardar(iconoApp(64), new File(salida, "favicon.png"));
        guardar(iconoApp(512), new File(salida, "quinde-icono-512.png"));
        guardar(iconoApp(48), new File(salida, "quinde-48.png"));
        guardar(logoLogin(410, 289), new File(salida, "logo.png"));
        guardar(logoCabecera(), new File(salida, "logo-cabecera.png"));
        guardar(splash(500, 352), new File(salida, "quinde_splash.png"));
        guardar(logoLogin(820, 578), new File(salida, "quinde-logo-820.png"));
        escribirIco(new int[]{16, 24, 32, 48, 64, 128, 256}, new File(salida, "quinde.ico"));
        System.out.println("ok");
    }

    static Graphics2D preparar(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** Dibuja el quinde en un cuadrado de lado 'tam' con esquina superior izquierda en (x, y). */
    static void ave(Graphics2D g, double x, double y, double tam) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.scale(tam / 120.0, tam / 120.0);
        icono.render(null, g2);
        g2.dispose();
    }

    /** Icono de la app: quinde sobre un cuadrado blanco redondeado (se ve igual en barras claras y oscuras). */
    static BufferedImage iconoApp(int tam) {
        BufferedImage img = new BufferedImage(tam, tam, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preparar(img);
        double r = tam * 0.23;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(0, 0, tam, tam, r * 2, r * 2));
        if (tam >= 48) {
            g.setColor(BORDE);
            g.setStroke(new BasicStroke(Math.max(1f, tam / 128f)));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, tam - 1, tam - 1, r * 2, r * 2));
        }
        double margen = tam * 0.06;
        ave(g, margen, margen, tam - margen * 2);
        g.dispose();
        return img;
    }

    /** Nombre "Quinde" + pastilla "POS". Devuelve el ancho dibujado. */
    static int nombre(Graphics2D g, int x, int yBase, float tamLetra, Color colorNombre, Color fondoPos, Color textoPos) {
        Font f = extraBold.deriveFont(tamLetra);
        g.setFont(f);
        g.setColor(colorNombre);
        g.drawString("Quinde", x, yBase);
        FontMetrics fm = g.getFontMetrics();
        int ancho = fm.stringWidth("Quinde");
        Font fp = extraBold.deriveFont(tamLetra * 0.34f);
        FontMetrics fmp = g.getFontMetrics(fp);
        int padX = Math.round(tamLetra * 0.16f);
        int altoPos = Math.round(tamLetra * 0.42f);
        int anchoPos = fmp.stringWidth("POS") + padX * 2 + Math.round(tamLetra * 0.05f);
        int xPos = x + ancho + Math.round(tamLetra * 0.14f);
        int yPos = yBase - altoPos;
        g.setColor(fondoPos);
        g.fill(new RoundRectangle2D.Double(xPos, yPos, anchoPos, altoPos, altoPos, altoPos));
        g.setFont(fp);
        g.setColor(textoPos);
        int yTexto = yPos + (altoPos - fmp.getHeight()) / 2 + fmp.getAscent();
        g.drawString("POS", xPos + padX + Math.round(tamLetra * 0.025f), yTexto);
        return xPos + anchoPos - x;
    }

    /** Logo de la pantalla de inicio (tarjeta blanca, se ve bien en tema claro y oscuro). */
    static BufferedImage logoLogin(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preparar(img);
        double k = w / 410.0;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, 56 * k, 56 * k));
        g.setColor(BORDE);
        g.setStroke(new BasicStroke((float) (1.5 * k)));
        g.draw(new RoundRectangle2D.Double(0.75 * k, 0.75 * k, w - 1.5 * k, h - 1.5 * k, 56 * k, 56 * k));
        // Vertical: el quinde arriba, "Quinde POS" y el lema centrados debajo.
        double tamAve = 128 * k;
        ave(g, (w - tamAve) / 2, 18 * k, tamAve);
        float tamLetra = (float) (50 * k);
        BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = preparar(tmp);
        int ancho = nombre(gt, 0, (int) (200 * k), tamLetra, TINTA, SELVA, Color.WHITE);
        gt.dispose();
        nombre(g, (w - ancho) / 2, (int) (200 * k), tamLetra, TINTA, SELVA, Color.WHITE);
        g.setFont(regular.deriveFont((float) (17 * k)));
        g.setColor(SELVA);
        String lema = "Punto de venta libre para Ecuador";
        g.drawString(lema, (w - g.getFontMetrics().stringWidth(lema)) / 2, (int) (240 * k));
        g.dispose();
        return img;
    }

    /** Logo pequeno de la barra superior (pastilla blanca con el quinde y el nombre). */
    static BufferedImage logoCabecera() {
        int h = 34;
        BufferedImage tmp = new BufferedImage(400, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = preparar(tmp);
        int anchoTexto = nombre(gt, 0, 24, 19f, TINTA, SELVA, Color.WHITE);
        gt.dispose();
        int w = 8 + 28 + 4 + anchoTexto + 12;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preparar(img);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
        g.setColor(BORDE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, h - 1, h - 1));
        ave(g, 7, 3, 28);
        nombre(g, 40, 24, 19f, TINTA, SELVA, Color.WHITE);
        g.dispose();
        return img;
    }

    /** Pantalla de carga (se muestra antes de que Java termine de abrir EcoPos). */
    static BufferedImage splash(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preparar(img);
        g.setColor(TINTA);
        g.fillRect(0, 0, w, h);
        int tile = 132;
        int x = (w - tile) / 2;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x, 48, tile, tile, 60, 60));
        ave(g, x + 8, 56, tile - 16);
        BufferedImage tmp = new BufferedImage(w, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = preparar(tmp);
        int ancho = nombre(gt, 0, 60, 50f, Color.WHITE, BROTE, TINTA);
        gt.dispose();
        nombre(g, (w - ancho) / 2, 248, 50f, Color.WHITE, BROTE, TINTA);
        g.setFont(regular.deriveFont(17f));
        g.setColor(BROTE);
        String lema = "Punto de venta libre para Ecuador";
        g.drawString(lema, (w - g.getFontMetrics().stringWidth(lema)) / 2, 286);
        g.setFont(regular.deriveFont(13f));
        g.setColor(new Color(0x8FB9A3));
        String cargando = "Cargando…";
        g.drawString(cargando, (w - g.getFontMetrics().stringWidth(cargando)) / 2, 326);
        g.dispose();
        return img;
    }

    static void guardar(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
    }

    /** .ico con varias resoluciones (PNG dentro del ICO, valido desde Windows Vista). */
    static void escribirIco(int[] tamanos, File f) throws Exception {
        List<byte[]> pngs = new ArrayList<>();
        for (int t : tamanos) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(iconoApp(t), "png", b);
            pngs.add(b.toByteArray());
        }
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            escribirLE16(out, 0);
            escribirLE16(out, 1);
            escribirLE16(out, tamanos.length);
            int offset = 6 + 16 * tamanos.length;
            for (int i = 0; i < tamanos.length; i++) {
                int t = tamanos[i];
                out.writeByte(t >= 256 ? 0 : t);
                out.writeByte(t >= 256 ? 0 : t);
                out.writeByte(0);
                out.writeByte(0);
                escribirLE16(out, 1);
                escribirLE16(out, 32);
                escribirLE32(out, pngs.get(i).length);
                escribirLE32(out, offset);
                offset += pngs.get(i).length;
            }
            for (byte[] p : pngs) {
                out.write(p);
            }
        }
    }

    static void escribirLE16(DataOutputStream o, int v) throws Exception {
        o.writeByte(v & 0xFF);
        o.writeByte((v >> 8) & 0xFF);
    }

    static void escribirLE32(DataOutputStream o, int v) throws Exception {
        escribirLE16(o, v & 0xFFFF);
        escribirLE16(o, (v >> 16) & 0xFFFF);
    }
}
