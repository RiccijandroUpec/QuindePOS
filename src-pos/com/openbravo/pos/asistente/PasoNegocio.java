package com.openbravo.pos.asistente;

import com.openbravo.pos.customers.ValidadorIdentificacion;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Datos del negocio y logo. Se guardan donde ya los leen el ticket, los reportes, el PDF y la
 * factura del SRI (sri-conector/config/datos-emisor.properties y config/logo.png), asi se
 * escriben una sola vez. Opcionalmente el logo tambien va al ticket impreso, en blanco y negro.
 */
final class PasoNegocio extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoNegocio.class.getName());

    private final JTextField nombreComercial = new JTextField(28);
    private final JTextField razonSocial = new JTextField(28);
    private final JTextField ruc = new JTextField(14);
    private final JTextField direccion = new JTextField(28);
    private final JCheckBox obligado = new JCheckBox("Obligado a llevar contabilidad");
    private final JLabel vistaLogo = new JLabel();
    private final JCheckBox logoEnTicket = new JCheckBox("Usar mi logo tambi\u00E9n en el ticket impreso (en blanco y negro)");
    private BufferedImage logoNuevo;

    PasoNegocio(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "negocio";
    }

    @Override
    String nombre() {
        return "Tu negocio";
    }

    @Override
    String titulo() {
        return "Los datos de tu negocio";
    }

    @Override
    String descripcion() {
        return "Salen en el ticket, en los reportes y en la factura electr\u00F3nica. Escr\u00EDbelos como figuran en el SRI.";
    }

    private File archivoEmisor() {
        return new File(ctx.carpetaConector, "config/datos-emisor.properties");
    }

    private File archivoLogo() {
        return new File(ctx.carpetaConector, "config/logo.png");
    }

    @Override
    protected JComponent crearPanel() {
        Properties p = leerEmisor();
        nombreComercial.setText(p.getProperty("nombreComercial", ""));
        razonSocial.setText(p.getProperty("razonSocial", ""));
        ruc.setText(p.getProperty("ruc", ""));
        direccion.setText(p.getProperty("dirEstablecimiento", p.getProperty("dirMatriz", "")));
        obligado.setSelected("SI".equalsIgnoreCase(p.getProperty("obligadoContabilidad")));
        obligado.setOpaque(false);

        JPanel col = Ui.columna();
        col.add(Ui.formulario(
                "Nombre del negocio", nombreComercial,
                "Raz\u00F3n social (due\u00F1o o empresa)", razonSocial,
                "RUC", ruc,
                "Direcci\u00F3n", direccion,
                "", obligado));
        col.add(Ui.nota("Ejemplo: nombre \u201CCafeter\u00EDa El Quinde\u201D, raz\u00F3n social \u201CMar\u00EDa P\u00E9rez L\u00F3pez\u201D."));

        col.add(Ui.subtitulo("Tu logo"));
        JPanel filaLogo = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filaLogo.setOpaque(false);
        filaLogo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton elegir = new JButton("Elegir logo\u2026");
        elegir.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                elegirLogo();
            }
        });
        filaLogo.add(vistaLogo);
        filaLogo.add(elegir);
        col.add(filaLogo);
        logoEnTicket.setOpaque(false);
        logoEnTicket.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoEnTicket.setSelected(true);
        col.add(logoEnTicket);
        col.add(Ui.nota("PNG o JPG. Sale en la factura en PDF, en los reportes y en el correo al cliente."));
        mostrarLogo(leerImagen(archivoLogo()));
        return col;
    }

    private void elegirLogo() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Elige el logo de tu negocio");
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Imagen (*.png, *.jpg)", "png", "jpg", "jpeg"));
        if (selector.showOpenDialog(Asistente.padreDialogos(vistaLogo)) == JFileChooser.APPROVE_OPTION) {
            BufferedImage img = leerImagen(selector.getSelectedFile());
            if (img == null) {
                javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(vistaLogo),
                        "Ese archivo no es una imagen PNG o JPG.", "Logo", javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            logoNuevo = img;
            mostrarLogo(img);
        }
    }

    private void mostrarLogo(BufferedImage img) {
        if (img == null) {
            vistaLogo.setIcon(null);
            vistaLogo.setText("(sin logo)");
            return;
        }
        double k = Math.min(180.0 / img.getWidth(), 70.0 / img.getHeight());
        vistaLogo.setText(null);
        vistaLogo.setIcon(new ImageIcon(img.getScaledInstance(Math.max(1, (int) (img.getWidth() * k)),
                Math.max(1, (int) (img.getHeight() * k)), Image.SCALE_SMOOTH)));
    }

    @Override
    String guardar() {
        String nombre = nombreComercial.getText().trim();
        String razon = razonSocial.getText().trim();
        String r = ruc.getText().trim();
        String dir = direccion.getText().trim();
        if (nombre.isEmpty() && razon.isEmpty()) {
            return "Escribe el nombre de tu negocio o la raz\u00F3n social.";
        }
        if (razon.isEmpty()) {
            razon = nombre;
        }
        if (!r.isEmpty()) {
            ValidadorIdentificacion.Resultado v = ValidadorIdentificacion.validar(r);
            if (r.length() != 13 || !v.isValida()) {
                return "El RUC no es v\u00E1lido: debe tener 13 d\u00EDgitos (" + v.getMensaje() + ").";
            }
        }
        if (dir.isEmpty()) {
            return "Escribe la direcci\u00F3n del negocio.";
        }
        Properties p = leerEmisor();
        p.setProperty("nombreComercial", nombre);
        p.setProperty("razonSocial", razon);
        p.setProperty("ruc", r);
        p.setProperty("dirMatriz", p.getProperty("dirMatriz", "").isEmpty() ? dir : p.getProperty("dirMatriz"));
        p.setProperty("dirEstablecimiento", dir);
        p.setProperty("obligadoContabilidad", obligado.isSelected() ? "SI" : "NO");
        // Lo minimo que pide la facturacion electronica; se ajusta despues en su pantalla.
        if (p.getProperty("establecimiento", "").isEmpty()) {
            p.setProperty("establecimiento", "001");
        }
        if (p.getProperty("puntoEmision", "").isEmpty()) {
            p.setProperty("puntoEmision", "001");
        }
        if (p.getProperty("ambiente", "").isEmpty()) {
            p.setProperty("ambiente", "PRUEBAS");
        }
        try {
            File archivo = archivoEmisor();
            archivo.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(archivo)) {
                p.store(out, "Quinde POS - datos del negocio");
            }
            if (logoNuevo != null) {
                ImageIO.write(reducir(logoNuevo, 800), "png", archivoLogo());
            }
            BufferedImage logo = logoNuevo != null ? logoNuevo : leerImagen(archivoLogo());
            if (logo != null && logoEnTicket.isSelected()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                ImageIO.write(logoParaTicket(logo), "png", b);
                ctx.dls.setResourceAsBinary("Printer.Ticket.Logo", b.toByteArray());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron guardar los datos del negocio", e);
            return "No se pudieron guardar los datos: " + e.getMessage();
        }
        return null;
    }

    private Properties leerEmisor() {
        Properties p = new Properties();
        File f = archivoEmisor();
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "No se pudo leer " + f, e);
            }
        }
        return p;
    }

    private static BufferedImage leerImagen(File f) {
        try {
            return f != null && f.isFile() ? ImageIO.read(f) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage reducir(BufferedImage img, int anchoMax) {
        if (img.getWidth() <= anchoMax) {
            return img;
        }
        int alto = Math.max(1, img.getHeight() * anchoMax / img.getWidth());
        BufferedImage r = new BufferedImage(anchoMax, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = r.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, anchoMax, alto, null);
        g.dispose();
        return r;
    }

    /**
     * Logo para impresora termica: cabe en 300x90, fondo blanco y solo blanco o negro
     * (los grises y colores claros desaparecerian al imprimir).
     */
    static BufferedImage logoParaTicket(BufferedImage img) {
        double k = Math.min(300.0 / img.getWidth(), 90.0 / img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * k));
        int h = Math.max(1, (int) Math.round(img.getHeight() * k));
        BufferedImage escalada = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = escalada.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        BufferedImage bn = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = escalada.getRGB(x, y);
                int alfa = argb >>> 24;
                int luz = (int) (0.3 * ((argb >> 16) & 0xFF) + 0.59 * ((argb >> 8) & 0xFF) + 0.11 * (argb & 0xFF));
                bn.setRGB(x, y, alfa > 100 && luz < 150 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return bn;
    }
}
