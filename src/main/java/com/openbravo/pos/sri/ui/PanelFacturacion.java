package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.config.ConfiguracionCorreoLoader;
import com.openbravo.pos.sri.config.ConfiguracionLoader;
import com.openbravo.pos.sri.config.RutasConector;
import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.ConfiguracionCorreo;
import com.openbravo.pos.sri.dominio.DatosEmisor;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Pantalla "Facturacion electronica" dentro de la configuracion de EcoPos:
 * todo en un solo lugar (emitir si/no, datos del negocio, punto de emision,
 * ambiente, firma con su titular y vencimiento, correo de envio) y una lista
 * de verificacion con prueba de conexion al SRI. Guarda con los mismos
 * cargadores de siempre (ConfiguracionLoader, ConfiguracionCorreoLoader), asi
 * que las claves siguen cifradas en disco.
 */
public class PanelFacturacion extends JPanel {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String URL_PRUEBAS = "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl";
    private static final String URL_PRODUCCION = "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl";

    private final Path archivoEmisor = RutasConector.resolver("config/datos-emisor.properties");
    private final Path archivoCorreo = RutasConector.resolver("config/correo.properties");
    private final Path archivoEstado = RutasConector.resolver("facturacion-global.properties");

    private final JCheckBox emitir = new JCheckBox("Emitir factura electrónica en cada venta");
    private final JTextField ruc = new JTextField(14);
    private final JTextField razonSocial = new JTextField(32);
    private final JTextField nombreComercial = new JTextField(32);
    private final JTextField dirMatriz = new JTextField(32);
    private final JTextField dirEstablecimiento = new JTextField(32);
    private final JTextField contribuyenteEspecial = new JTextField(10);
    private final JCheckBox obligadoContabilidad = new JCheckBox("Obligado a llevar contabilidad");
    private final JTextField establecimiento = new JTextField(4);
    private final JTextField puntoEmision = new JTextField(4);
    private final JComboBox<String> ambiente = new JComboBox<>(new String[]{"Pruebas", "Producción"});
    private final JLabel avisoAmbiente = new JLabel();
    private final JTextField rutaFirma = new JTextField(26);
    private final JPasswordField claveFirma = new JPasswordField(16);
    private final JLabel estadoFirma = new JLabel(" ");
    private final JTextField smtpHost = new JTextField(22);
    private final JTextField smtpPuerto = new JTextField(5);
    private final JTextField smtpUsuario = new JTextField(22);
    private final JPasswordField smtpClave = new JPasswordField(16);
    private final JTextField smtpRemitente = new JTextField(22);
    private final JCheckBox smtpTls = new JCheckBox("Usar STARTTLS", true);
    private final JLabel chequeoDatos = new JLabel();
    private final JLabel chequeoFirma = new JLabel();
    private final JLabel chequeoCorreo = new JLabel();
    private final JLabel chequeoSri = new JLabel("○  Conexión con el SRI: sin probar");
    private final JLabel estadoGuardado = new JLabel(" ");

    private char[] claveFirmaGuardada;
    private char[] claveCorreoGuardada;

    public PanelFacturacion() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titulo = new JLabel("Facturación electrónica");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        avisoAmbiente.setFont(avisoAmbiente.getFont().deriveFont(Font.BOLD));
        JPanel norte = new JPanel(new BorderLayout());
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        norte.add(titulo, BorderLayout.CENTER);
        norte.add(avisoAmbiente, BorderLayout.EAST);
        add(norte, BorderLayout.NORTH);

        JPanel contenido = new JPanel();
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        emitir.setFont(emitir.getFont().deriveFont(Font.BOLD, 14f));
        contenido.add(seccion("Emisión", emitir,
                nota("Si la venta no tiene cliente, la factura sale a Consumidor final. Para facturar con datos, "
                        + "elige \"Factura con datos\" al cobrar.")));
        contenido.add(seccion("Datos del negocio", formulario(
                "RUC", ruc, "Razón social", razonSocial, "Nombre comercial", nombreComercial,
                "Dirección matriz", dirMatriz, "Dirección del establecimiento", dirEstablecimiento,
                "Contribuyente especial (N° resolución)", contribuyenteEspecial, "", obligadoContabilidad)));
        contenido.add(seccion("Punto de emisión y ambiente", formulario(
                "Establecimiento (3 dígitos)", establecimiento, "Punto de emisión (3 dígitos)", puntoEmision,
                "Ambiente", ambiente)));
        JButton examinar = new JButton("Examinar…");
        examinar.addActionListener(e -> elegirFirma());
        JPanel filaFirma = new JPanel(new BorderLayout(6, 0));
        filaFirma.add(rutaFirma, BorderLayout.CENTER);
        filaFirma.add(examinar, BorderLayout.EAST);
        contenido.add(seccion("Firma electrónica (.p12)", formulario(
                "Archivo", filaFirma, "Clave", claveFirma, "", estadoFirma),
                nota("La clave se guarda cifrada. Déjala en blanco para mantener la que ya está guardada.")));
        contenido.add(seccion("Correo para enviar los comprobantes (opcional)", formulario(
                "Servidor SMTP", smtpHost, "Puerto", smtpPuerto, "Usuario", smtpUsuario, "Clave", smtpClave,
                "Correo remitente", smtpRemitente, "", smtpTls)));
        JButton probar = new JButton("Probar conexión con el SRI");
        probar.addActionListener(e -> probarConexion());
        JPanel checklist = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        checklist.add(chequeoDatos);
        checklist.add(chequeoFirma);
        checklist.add(chequeoCorreo);
        checklist.add(chequeoSri);
        JPanel filaProbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        filaProbar.add(probar);
        contenido.add(seccion("Verificación", checklist, filaProbar));

        JScrollPane scroll = new JScrollPane(contenido);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        JButton guardar = new JButton("Guardar cambios");
        guardar.setFont(guardar.getFont().deriveFont(Font.BOLD));
        guardar.addActionListener(e -> guardar());
        JPanel sur = new JPanel(new BorderLayout());
        sur.add(estadoGuardado, BorderLayout.WEST);
        sur.add(guardar, BorderLayout.EAST);
        add(sur, BorderLayout.SOUTH);

        ambiente.addActionListener(e -> actualizarAvisoAmbiente());
        DocumentListener revisarFirma = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                revisarFirma();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                revisarFirma();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                revisarFirma();
            }
        };
        rutaFirma.getDocument().addDocumentListener(revisarFirma);
        claveFirma.getDocument().addDocumentListener(revisarFirma);

        cargar();
    }

    // --- carga / guardado ------------------------------------------------------

    public void cargar() {
        emitir.setSelected(leerEmitir());
        if (Files.exists(archivoEmisor)) {
            try {
                DatosEmisor d = ConfiguracionLoader.cargar(archivoEmisor);
                ruc.setText(nulo(d.getRuc()));
                razonSocial.setText(nulo(d.getRazonSocial()));
                nombreComercial.setText(nulo(d.getNombreComercial()));
                dirMatriz.setText(nulo(d.getDirMatriz()));
                dirEstablecimiento.setText(nulo(d.getDirEstablecimiento()));
                contribuyenteEspecial.setText(nulo(d.getContribuyenteEspecial()));
                obligadoContabilidad.setSelected(d.isObligadoContabilidad());
                establecimiento.setText(nulo(d.getEstablecimiento()));
                puntoEmision.setText(nulo(d.getPuntoEmision()));
                ambiente.setSelectedIndex(d.getAmbiente() == Ambiente.PRODUCCION ? 1 : 0);
                rutaFirma.setText(nulo(d.getRutaCertificadoP12()));
                claveFirmaGuardada = d.getClaveCertificado();
            } catch (Exception e) {
                estadoGuardado.setText("No se pudo leer la configuración: " + e.getMessage());
            }
        } else {
            establecimiento.setText("001");
            puntoEmision.setText("001");
        }
        if (Files.exists(archivoCorreo)) {
            try {
                ConfiguracionCorreo c = ConfiguracionCorreoLoader.cargar(archivoCorreo);
                smtpHost.setText(nulo(c.getHost()));
                smtpPuerto.setText(String.valueOf(c.getPuerto()));
                smtpUsuario.setText(nulo(c.getUsuario()));
                smtpRemitente.setText(nulo(c.getRemitente()));
                smtpTls.setSelected(c.isUsarTls());
                claveCorreoGuardada = c.getClave();
            } catch (Exception e) {
                estadoGuardado.setText("No se pudo leer la configuración de correo: " + e.getMessage());
            }
        } else {
            smtpPuerto.setText("587");
        }
        actualizarAvisoAmbiente();
        revisarFirma();
        actualizarChecklist();
    }

    private void guardar() {
        String problema = validar();
        if (problema != null) {
            JOptionPane.showMessageDialog(this, problema, "Facturación electrónica", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            char[] clave = claveFirma.getPassword().length > 0 ? claveFirma.getPassword() : claveFirmaGuardada;
            DatosEmisor datos = new DatosEmisor(texto(ruc), texto(razonSocial), texto(nombreComercial), texto(dirMatriz),
                    texto(dirEstablecimiento), texto(contribuyenteEspecial), obligadoContabilidad.isSelected(),
                    texto(establecimiento), texto(puntoEmision),
                    ambiente.getSelectedIndex() == 1 ? Ambiente.PRODUCCION : Ambiente.PRUEBAS, texto(rutaFirma), clave);
            Files.createDirectories(archivoEmisor.getParent());
            ConfiguracionLoader.guardar(datos, archivoEmisor);
            claveFirmaGuardada = clave;
            claveFirma.setText("");

            if (!smtpHost.getText().trim().isEmpty()) {
                char[] claveCorreo = smtpClave.getPassword().length > 0 ? smtpClave.getPassword() : claveCorreoGuardada;
                ConfiguracionCorreoLoader.guardar(new ConfiguracionCorreo(smtpHost.getText().trim(),
                        Integer.parseInt(smtpPuerto.getText().trim()), smtpUsuario.getText().trim(), claveCorreo,
                        smtpRemitente.getText().trim(), smtpTls.isSelected()), archivoCorreo);
                claveCorreoGuardada = claveCorreo;
                smtpClave.setText("");
            }
            guardarEmitir(emitir.isSelected());
            estadoGuardado.setText("✔  Cambios guardados");
            estadoGuardado.setForeground(verde());
            actualizarChecklist();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "El puerto del correo debe ser un número.", "Correo", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String validar() {
        String r = ruc.getText().trim();
        if (!r.matches("\\d{13}")) {
            return "El RUC debe tener 13 dígitos.";
        }
        if (razonSocial.getText().trim().isEmpty() || dirMatriz.getText().trim().isEmpty()) {
            return "Faltan la razón social y la dirección matriz.";
        }
        if (!establecimiento.getText().trim().matches("\\d{3}") || !puntoEmision.getText().trim().matches("\\d{3}")) {
            return "El establecimiento y el punto de emisión son de 3 dígitos (por ejemplo 001).";
        }
        if (ambiente.getSelectedIndex() == 1) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "En PRODUCCIÓN cada venta genera una factura real con validez tributaria.\n¿Confirmas el cambio?",
                    "Ambiente de producción", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) {
                return "Se mantiene sin guardar. Vuelve a Pruebas si todavía estás probando.";
            }
        }
        return null;
    }

    // --- firma, ambiente, checklist, conexion ------------------------------------

    private void elegirFirma() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Elige tu firma electrónica (.p12)");
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Firma electrónica (*.p12, *.pfx)", "p12", "pfx"));
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rutaFirma.setText(selector.getSelectedFile().getAbsolutePath());
        }
    }

    private void revisarFirma() {
        String ruta = rutaFirma.getText().trim();
        char[] clave = claveFirma.getPassword().length > 0 ? claveFirma.getPassword() : claveFirmaGuardada;
        if (ruta.isEmpty()) {
            mostrar(estadoFirma, "○  Todavía no elegiste la firma", null);
            return;
        }
        if (clave == null || clave.length == 0) {
            mostrar(estadoFirma, "○  Escribe la clave de la firma", ambar());
            return;
        }
        try {
            InfoCertificado info = InfoCertificado.leer(Paths.get(ruta), clave);
            long dias = info.diasRestantes();
            if (dias < 0) {
                mostrar(estadoFirma, "✖  Firma de " + info.titular + " VENCIDA el " + info.validoHasta.format(FECHA), rojo());
            } else if (dias <= 30) {
                mostrar(estadoFirma, "⚠  Firma de " + info.titular + ": vence en " + dias + " días (" + info.validoHasta.format(FECHA)
                        + "). Renóvala pronto.", ambar());
            } else {
                mostrar(estadoFirma, "✔  Firma de " + info.titular + ", válida hasta " + info.validoHasta.format(FECHA), verde());
            }
        } catch (Exception e) {
            mostrar(estadoFirma, "✖  " + e.getMessage(), rojo());
        }
        actualizarChecklist();
    }

    private void actualizarAvisoAmbiente() {
        if (ambiente.getSelectedIndex() == 1) {
            mostrar(avisoAmbiente, "●  PRODUCCIÓN: las facturas tienen validez tributaria", verde());
        } else {
            mostrar(avisoAmbiente, "●  PRUEBAS: estas facturas no tienen validez tributaria", ambar());
        }
    }

    private void actualizarChecklist() {
        boolean datos = ruc.getText().trim().matches("\\d{13}") && !razonSocial.getText().trim().isEmpty()
                && !dirMatriz.getText().trim().isEmpty();
        mostrar(chequeoDatos, datos ? "✔  Datos del negocio completos" : "✖  Faltan datos del negocio", datos ? verde() : rojo());
        String firma = estadoFirma.getText();
        boolean firmaOk = firma.startsWith("✔");
        mostrar(chequeoFirma, firmaOk ? "✔  Firma electrónica válida"
                : firma.startsWith("⚠") ? "⚠  La firma vence pronto" : "✖  Firma electrónica pendiente o inválida",
                firmaOk ? verde() : firma.startsWith("⚠") ? ambar() : rojo());
        boolean correo = Files.exists(archivoCorreo);
        mostrar(chequeoCorreo, correo ? "✔  Correo de envío configurado" : "○  Correo de envío sin configurar (opcional)",
                correo ? verde() : null);
    }

    private void probarConexion() {
        final String url = ambiente.getSelectedIndex() == 1 ? URL_PRODUCCION : URL_PRUEBAS;
        mostrar(chequeoSri, "○  Probando conexión con el SRI…", null);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(10000);
                    int codigo = con.getResponseCode();
                    try (InputStream in = con.getInputStream()) {
                        in.read(new byte[256]);
                    }
                    return codigo == 200 ? null : "respondió con código " + codigo;
                } catch (Exception e) {
                    return e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String error = get();
                    if (error == null) {
                        mostrar(chequeoSri, "✔  El SRI responde (" + (ambiente.getSelectedIndex() == 1 ? "producción" : "pruebas") + ")", verde());
                    } else {
                        mostrar(chequeoSri, "✖  No se pudo conectar con el SRI: " + error, rojo());
                    }
                } catch (Exception e) {
                    mostrar(chequeoSri, "✖  " + e.getMessage(), rojo());
                }
            }
        }.execute();
    }

    // --- interruptor global ------------------------------------------------------

    private boolean leerEmitir() {
        if (!Files.exists(archivoEstado)) {
            return false;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(archivoEstado)) {
            p.load(in);
        } catch (Exception e) {
            return false;
        }
        return "true".equals(p.getProperty("activo", "false"));
    }

    private void guardarEmitir(boolean activo) throws Exception {
        Properties p = new Properties();
        p.setProperty("activo", String.valueOf(activo));
        try (OutputStream out = Files.newOutputStream(archivoEstado)) {
            p.store(out, "EcoPos - facturacion electronica en cada venta");
        }
    }

    // --- piezas de interfaz --------------------------------------------------------

    private static JPanel seccion(String titulo, JComponent... contenido) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 12, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                        BorderFactory.createEmptyBorder(10, 14, 12, 14))));
        JLabel t = new JLabel(titulo);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 15f));
        p.add(t, BorderLayout.NORTH);
        JPanel cuerpo = new JPanel();
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));
        for (JComponent c : contenido) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            cuerpo.add(c);
        }
        p.add(cuerpo, BorderLayout.CENTER);
        p.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 40));
        return p;
    }

    /** Etiqueta / campo en pares. */
    private static JPanel formulario(Object... pares) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 10);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < pares.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            p.add(new JLabel(String.valueOf(pares[i])), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add((Component) pares[i + 1], c);
        }
        return p;
    }

    private static JLabel nota(String texto) {
        JLabel l = new JLabel("<html>" + texto + "</html>");
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        return l;
    }

    private static void mostrar(JLabel etiqueta, String texto, Color color) {
        etiqueta.setText(texto);
        etiqueta.setForeground(color == null ? UIManager.getColor("Label.foreground") : color);
    }

    private static boolean oscuro() {
        Color fondo = UIManager.getColor("Panel.background");
        return fondo != null && (fondo.getRed() + fondo.getGreen() + fondo.getBlue()) / 3 < 128;
    }

    private static Color verde() {
        return oscuro() ? new Color(0x81C784) : new Color(0x2E7D32);
    }

    private static Color ambar() {
        return oscuro() ? new Color(0xFFD54F) : new Color(0xB26A00);
    }

    private static Color rojo() {
        return oscuro() ? new Color(0xEF9A9A) : new Color(0xC62828);
    }

    private static String texto(JTextField campo) {
        String t = campo.getText().trim();
        return t.isEmpty() ? null : t;
    }

    private static String nulo(String valor) {
        return valor == null ? "" : valor;
    }
}
