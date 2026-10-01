import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
/** Genera branding/paleta-selva.png: java GenerarPaleta branding/fuentes branding/paleta-selva.png */
public class GenerarPaleta {
    public static void main(String[] a) throws Exception {
        Font extra = Font.createFont(Font.TRUETYPE_FONT, new File(a[0], "Outfit-ExtraBold.ttf"));
        Font reg = Font.createFont(Font.TRUETYPE_FONT, new File(a[0], "Outfit-Regular.ttf"));
        Object[][] c = {
            {"Quinde", 0x2E9E6B, "Color principal", Color.WHITE},
            {"Selva", 0x1B5E3F, "Botones y títulos", Color.WHITE},
            {"Brote", 0xA8E6C1, "Fondos suaves y gráficos", new Color(0x10231A)},
            {"Coral", 0xF2705E, "Acento (pico del quinde)", new Color(0x10231A)},
            {"Tinta", 0x10231A, "Texto y fondo oscuro", Color.WHITE}};
        int k = 2, w = 180, h = 200, gap = 14, pad = 24;
        BufferedImage img = new BufferedImage((pad * 2 + c.length * w + (c.length - 1) * gap) * k, (pad * 2 + h) * k, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(k, k);
        g.setColor(Color.WHITE);
        g.fillRoundRect(0, 0, img.getWidth() / k, img.getHeight() / k, 28, 28);
        for (int i = 0; i < c.length; i++) {
            int x = pad + i * (w + gap), y = pad;
            g.setColor(new Color((Integer) c[i][1]));
            g.fillRoundRect(x, y, w, h, 22, 22);
            if ((Integer) c[i][1] == 0xA8E6C1) { g.setColor(new Color(0xD9DDE3)); g.drawRoundRect(x, y, w, h, 22, 22); }
            g.setColor((Color) c[i][3]);
            g.setFont(extra.deriveFont(26f));
            g.drawString((String) c[i][0], x + 16, y + h - 64);
            g.setFont(reg.deriveFont(16f));
            g.drawString(String.format("#%06X", (Integer) c[i][1]), x + 16, y + h - 40);
            g.setFont(reg.deriveFont(12.5f));
            g.drawString((String) c[i][2], x + 16, y + h - 18);
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new File(a[1]));
    }
}
