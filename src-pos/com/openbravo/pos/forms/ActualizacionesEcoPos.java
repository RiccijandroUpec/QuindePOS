package com.openbravo.pos.forms;

import com.openbravo.data.loader.Session;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Actualizaciones de datos que EcoPos aplica solo al arrancar sobre una base
 * YA existente, para que las mejoras nuevas (entradas de menu, permisos)
 * lleguen tambien a instalaciones creadas con versiones anteriores sin correr
 * SQL a mano. Cada paso es idempotente: revisa un marcador y, si ya esta, no
 * toca nada. Nunca impide que EcoPos arranque (los errores solo se registran).
 *
 * Las instalaciones nuevas ya traen todo esto en las plantillas
 * (templates/Menu.Root.txt, Role.*.xml), asi que aqui no cambia nada.
 */
public final class ActualizacionesEcoPos {

    private static final Logger LOG = Logger.getLogger(ActualizacionesEcoPos.class.getName());

    private static final String CLASE_PANEL_NEGOCIO = "com.openbravo.pos.panels.JPanelDashboard";
    private static final String ANCLA_MENU_VENTAS = "group.addPanel(\"/com/openbravo/images/sale.png\", \"Menu.Ticket\"";
    private static final String ANCLA_PERMISO_VENTAS = "<class name=\"com.openbravo.pos.sales.JPanelTicketSales\"/>";

    private ActualizacionesEcoPos() {
    }

    public static void aplicar(Session session) {
        try {
            Connection con = session.getConnection();
            agregarPanelNegocio(con);
            agregarPermisoSinAutorizacion(con);
            crearTablaAuditoria(con);
            crearTablaArqueos(con);
            agregarResumenTributario(con);
            agregarPromociones(con);
            agregarAsistente(con);
            integrarFacturacionElectronica(con);
            renombrarAQuinde(con);
            ticketQuinde(con);
            scriptsEnEspanol(con);
            billetesEnDolares(con);
            scriptsModernos(con);
            limpiarComentarioSri(con);
            // Siempre al final: las actualizaciones de arriba reconocen los recursos de fabrica
            // por su texto (o su huella) con el aviso de licencia original.
            avisoDeLicenciaQuinde(con);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron aplicar las actualizaciones de EcoPos", e);
        }
    }

    /**
     * Fase D: "Panel del negocio" justo DESPUES de Ventas (EcoPos abre la
     * primera opcion del menu al iniciar sesion, y esa debe seguir siendo la
     * venta), para Administrador y Gerente. Si una version anterior de esta
     * actualizacion lo dejo antes de Ventas, lo mueve.
     */
    private static void agregarPanelNegocio(Connection con) throws SQLException {
        String menu = leerRecurso(con, "Menu.Root");
        String linea = "group.addPanel(\"/com/openbravo/images/chart.png\", \"Menu.Dashboard\", \"" + CLASE_PANEL_NEGOCIO + "\");";
        if (menu != null && menu.contains(ANCLA_MENU_VENTAS)) {
            int posLinea = menu.indexOf(linea);
            int posVentas = menu.indexOf(ANCLA_MENU_VENTAS);
            if (posLinea >= 0 && posLinea < posVentas) {
                // Quitar la linea vieja completa (sangria + texto + salto) para reinsertarla despues de Ventas.
                int inicioLinea = menu.lastIndexOf('\n', posLinea) + 1;
                int finLinea = menu.indexOf('\n', posLinea) + 1;
                menu = menu.substring(0, inicioLinea) + menu.substring(finLinea);
                posLinea = -1;
            }
            if (posLinea < 0 && !menu.contains(CLASE_PANEL_NEGOCIO)) {
                int i = menu.indexOf(ANCLA_MENU_VENTAS);
                String sangria = sangriaDeLinea(menu, i);
                int finVentas = menu.indexOf('\n', i);
                menu = menu.substring(0, finVentas + 1) + sangria + linea + "\n" + menu.substring(finVentas + 1);
                guardarRecurso(con, "Menu.Root", menu);
                LOG.info("Menu.Root: Panel del negocio despues de Ventas");
            }
        }
        for (String rol : new String[]{"Administrador", "Gerente"}) {
            String permisos = leerPermisos(con, rol);
            if (permisos != null && !permisos.contains(CLASE_PANEL_NEGOCIO) && permisos.contains(ANCLA_PERMISO_VENTAS)) {
                permisos = permisos.replace(ANCLA_PERMISO_VENTAS,
                        ANCLA_PERMISO_VENTAS + "\n    <class name=\"" + CLASE_PANEL_NEGOCIO + "\"/>");
                guardarPermisos(con, rol, permisos);
                LOG.info("Rol " + rol + ": permiso para el Panel del negocio");
            }
        }
    }

    /** Fase F: "Resumen tributario" despues de "Cerrar caja", para Administrador y Gerente. */
    private static void agregarResumenTributario(Connection con) throws SQLException {
        agregarPanelAdministrativo(con, "com.openbravo.pos.panels.JPanelResumenTributario",
                "/com/openbravo/images/reports.png", "Menu.ResumenTributario",
                "\"Menu.CloseTPV\", \"com.openbravo.pos.panels.JPanelCloseMoney\");");
    }

    /** Fase U: "Asistente de configuracion" en Sistema, despues de Facturacion electronica (solo Administrador). */
    private static void agregarAsistente(Connection con) throws SQLException {
        agregarOpcionMenu(con, "addExecution", "com.openbravo.pos.asistente.AccionAsistente",
                "/com/openbravo/images/configuration.png", "Menu.Asistente",
                "\"Menu.FacturacionElectronica\", \"com.openbravo.pos.sri.JPanelFacturacionSri\");",
                new String[]{"Administrador"});
    }

    /** Fase H: "Promociones" despues de "Resumen tributario", con su tabla de reglas. */
    private static void agregarPromociones(Connection con) throws SQLException {
        try (java.sql.Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ecopos_promociones ("
                    + "id VARCHAR(36) NOT NULL, activo INT NOT NULL, nombre VARCHAR(100) NOT NULL, tipo VARCHAR(20) NOT NULL, "
                    + "producto VARCHAR(255), categoria VARCHAR(255), n INT, m INT, porcentaje DOUBLE, "
                    + "hora_desde INT, hora_hasta INT, dias VARCHAR(7), PRIMARY KEY (id))");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo crear la tabla ecopos_promociones", e);
        }
        agregarPanelAdministrativo(con, "com.openbravo.pos.promociones.JPanelPromociones",
                "/com/openbravo/images/bookmark.png", "Menu.Promociones",
                "\"Menu.ResumenTributario\", \"com.openbravo.pos.panels.JPanelResumenTributario\");");
    }

    /**
     * La facturacion electronica deja de ser un modulo aparte: "Facturacion
     * electronica" (configuracion) y "Comprobantes electronicos" se abren
     * DENTRO de EcoPos como cualquier otra pantalla (antes eran ventanas
     * aparte lanzadas por SriConnectorConfig.bs / SriConnectorHistorial.bs),
     * y los botones "SRI: SI / SRI: NO" salen de la pantalla de venta (el
     * interruptor ahora esta en la configuracion).
     */
    private static void integrarFacturacionElectronica(Connection con) throws SQLException {
        String claseConfig = "com.openbravo.pos.sri.JPanelFacturacionSri";
        String claseComprobantes = "com.openbravo.pos.sri.JPanelComprobantesSri";
        String menu = leerRecurso(con, "Menu.Root");
        if (menu != null) {
            String nuevo = reemplazarLinea(menu, "SriConnectorConfig.bs",
                    "group.addPanel(\"/com/openbravo/images/configuration.png\", \"Menu.FacturacionElectronica\", \"" + claseConfig + "\");");
            nuevo = reemplazarLinea(nuevo, "SriConnectorHistorial.bs", null);
            if (!nuevo.equals(menu)) {
                guardarRecurso(con, "Menu.Root", nuevo);
                LOG.info("Menu.Root: facturacion electronica integrada");
            }
        }
        agregarPanelAdministrativo(con, claseComprobantes, "/com/openbravo/images/reports.png", "Menu.Comprobantes",
                "\"Menu.TicketEdit\", \"com.openbravo.pos.sales.JPanelTicketEdits\");");
        for (String rol : new String[]{"Administrador", "Gerente"}) {
            String permisos = leerPermisos(con, rol);
            if (permisos == null) {
                continue;
            }
            String nuevos = permisos.replace("<class name=\"/com/openbravo/pos/templates/SriConnectorConfig.bs\"/>",
                    "<class name=\"" + claseConfig + "\"/>");
            nuevos = reemplazarLinea(nuevos, "SriConnectorHistorial.bs\"/>", null);
            if (!nuevos.equals(permisos)) {
                guardarPermisos(con, rol, nuevos);
            }
        }
        String botones = leerRecurso(con, "Ticket.Buttons");
        if (botones != null) {
            String sinSri = reemplazarLinea(reemplazarLinea(botones, "key=\"button.sriinvoiceon\"", null),
                    "key=\"button.sriinvoiceoff\"", null);
            if (!sinSri.equals(botones)) {
                guardarRecurso(con, "Ticket.Buttons", sinSri);
                LOG.info("Ticket.Buttons: quitados los botones SRI SI/NO");
            }
        }
    }

    /** La marca pasa de EcoPos a Quinde POS: titulo de la ventana (solo si sigue siendo el de fabrica). */
    private static void renombrarAQuinde(Connection con) throws SQLException {
        String titulo = leerRecurso(con, "Window.Title");
        if (titulo != null && titulo.trim().startsWith("EcoPos")) {
            guardarRecurso(con, "Window.Title", "Quinde POS");
            LOG.info("Window.Title: Quinde POS");
        }
    }

    /**
     * Ticket impreso en espanol con los datos del negocio y el logo de Quinde POS. Solo reemplaza
     * las plantillas que siguen siendo las de fabrica (si el negocio edito la suya, no se toca).
     */
    private static void ticketQuinde(Connection con) throws SQLException {
        String[][] plantillas = {
            {"Printer.Ticket", "Touch Friendly Point Of Sale"},
            {"Printer.TicketPreview", "Touch Friendly Point Of Sale"},
            {"Printer.ReprintTicket", "Touch Friendly Point Of Sale"},
            {"Printer.Ticket2", "Thank You for your custom"},
            {"Printer.CloseCash", "Close Cash Report"},
            {"Printer.PartialCash", "Partial Cash Report"},
            {"Printer.TicketKitchen", "Kitchen Order"},
            {"Printer.CustomerPaid", "Account Balance"},
            {"Printer.CustomerPaid2", "Total Paid"},
            {"Printer.Inventory", "Inventory Record"},
            {"Printer.Start", "<text>Point Of Sale</text>"},
            {"Printer.TicketTotal", "Thank You"},
            {"Printer.TicketClose", "Tendered:"},
            {"Printer.Product", "Pts."},
            {"Printer.TicketNew", "Please Call Again"},
            {"Printer.FiscalTicket", "Mag card"},
        };
        for (String[] p : plantillas) {
            String actual = leerRecurso(con, p[0]);
            // Tambien la primera version del ticket de Quinde POS (sin los datos de la factura electronica).
            boolean primeraVersion = actual != null && actual.contains("Quinde POS - punto de venta libre")
                    && !actual.contains("$factura")
                    && (p[0].equals("Printer.Ticket") || p[0].equals("Printer.TicketPreview") || p[0].equals("Printer.ReprintTicket"));
            if (actual != null && (actual.contains(p[1]) || primeraVersion)) {
                byte[] nueva = leerClasspath("/com/openbravo/pos/templates/" + p[0] + ".xml");
                if (nueva != null) {
                    guardarRecursoBytes(con, p[0], nueva);
                    LOG.info(p[0] + ": plantilla de Quinde POS");
                }
            }
        }
        // El logo viejo media 168x48; si sigue ese, se cambia por el de Quinde (en blanco y negro).
        byte[] logo = leerRecursoBytes(con, "Printer.Ticket.Logo");
        if (logo != null) {
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(logo));
                if (img != null && img.getWidth() == 168 && img.getHeight() == 48) {
                    byte[] nuevo = leerClasspath("/com/openbravo/pos/templates/printer.ticket.logo.png");
                    if (nuevo != null) {
                        guardarRecursoBytes(con, "Printer.Ticket.Logo", nuevo);
                        LOG.info("Printer.Ticket.Logo: logo de Quinde POS");
                    }
                }
            } catch (java.io.IOException e) {
                LOG.log(Level.WARNING, "No se pudo revisar el logo del ticket", e);
            }
        }
    }

    /**
     * Mensajes de los scripts de la venta en espanol. Solo se cambian las frases
     * (no el script entero), asi se respeta lo que el negocio haya personalizado.
     */
    private static void scriptsEnEspanol(Connection con) throws SQLException {
        String[][] frases = {
            {"script.AddLineNote", "showInputDialog(\"Line notes\"", "showInputDialog(\"Nota para esta l\u00EDnea (por ejemplo: sin sal)\""},
            {"script.Event.Total", "\"Before closing ticket: Please Send Order to Remote Printer\", \"Send Check\"", "\"Antes de cobrar, env\u00EDa el pedido a cocina.\", \"Pedido sin enviar\""},
            {"script.SendOrder", "showMessageDialog(null, \"Order sent to Kitchen\")", "showMessageDialog(null, \"Pedido enviado a cocina\")"},
            {"script.SendOrder", "\"Nothing to Send\", \"Warning\"", "\"No hay nada nuevo para enviar a cocina\", \"Cocina\""},
            {"script.SetPerson", "showInputDialog(\"Enter Waiter\"", "showInputDialog(\"Mesero\""},
            {"script.StockCurrentAdd", "\"This is a Service and Stock Level is not checked\", \"Stock Check\"", "\"Es un servicio: no se controla el stock.\", \"Stock\""},
            {"script.StockCurrentAdd", "\"Not enough stock at this Location \" + loc + \" - Use Stock Diary to Add Stock to Inventory\", \"Stock Check\"", "\"No hay stock suficiente en el almac\u00E9n \" + loc + \". Registra la entrada en Inventario.\", \"Stock\""},
            {"script.StockCurrentSet", "showMessageDialog(null, \"This is a Service and Stock Level is not checked\")", "showMessageDialog(null, \"Es un servicio: no se controla el stock.\")"},
            {"script.StockCurrentSet", "\"Not enough stock at this Location \" + loc + \" - Please use Stock Diary to Add Stock to Inventory\", \"Stock\"", "\"No hay stock suficiente en el almac\u00E9n \" + loc + \". Registra la entrada en Inventario.\", \"Stock\""},
            {"script.linediscount", "\"Line Discount \" + sdiscount", "\"Descuento \" + sdiscount"},
            {"script.ServiceCharge", "\"Service @  \" + scval + \" of \" + taxline.printSubTotal()", "\"Servicio \" + scval + \" de \" + taxline.printSubTotal()"},
        };
        for (String[] f : frases) {
            String actual = leerRecurso(con, f[0]);
            if (actual != null && actual.contains(f[1])) {
                guardarRecurso(con, f[0], actual.replace(f[1], f[2]));
                LOG.info(f[0] + ": mensajes en espanol");
            }
        }
    }

    /**
     * Imagenes de billetes y monedas del cobro (temas clasicos): las originales eran libras
     * esterlinas. Se cambian por las de dolar solo si siguen siendo exactamente las originales.
     */
    private static void billetesEnDolares(Connection con) throws SQLException {
        String[][] originales = {
            {"note.50", "35c92c079deebe07c1a91aeb6e92bbdc5614cce1a2e58e6c0713e8b3e89799e3"},
            {"note.20", "afb5999be9104a9dbe852ae3f2408e827f6b78db34c0480f90f56229583c45ff"},
            {"note.10", "1c0aa7359c74b36d205291e7ce47f6586c93eb59c3ee74330ba599c98c34f8f2"},
            {"note.5", "e3a88224282c9ec75becc5d198bb3365cf34c49a19f06b082c9f1125cf4a3c14"},
            {"coin.1", "6202342d837bbfd38b65a446eb46fe68b4091b49a9538ca821b11fcb1afa107e"},
            {"coin.50", "2ec2e0eac328f70cd07d7e787637252a9cd07141b75e9c3ca2ed722eb1c3b931"},
            {"coin.20", "ecbc9700ec364b29db9b19310aac7989b0eb794a8d3ec7cd865dd76e5b529bdf"},
            {"coin.10", "3b906152da54dbda7997f944de31fe83bad8b21e5c2e29d76336328097543a7f"},
            {"coin.05", "fa29de2c474bb2f2ceb94c408ca1e76fa502219e979799056b6abef69ddd4105"},
            {"coin.01", "bf3e2058501cae81aaa00c14c812b1cd6cc1b44d2b1ac5986ff047c971a35e63"},
        };
        for (String[] o : originales) {
            byte[] actual = leerRecursoBytes(con, o[0]);
            if (actual != null && o[1].equals(sha256(actual))) {
                byte[] nuevo = leerClasspath("/com/openbravo/pos/templates/" + o[0] + ".png");
                if (nuevo != null) {
                    guardarRecursoBytes(con, o[0], nuevo);
                    LOG.info(o[0] + ": imagen en dolares");
                }
            }
        }
    }

    /**
     * Scripts de la venta reescritos (aviso de cambio, descuentos, notas, mesero, cocina, stock):
     * se reemplazan solo si el de la base es una version de fabrica conocida (comparando la huella
     * del texto sin importar los finales de linea), asi no se pisa nada personalizado.
     */
    private static void scriptsModernos(Connection con) throws SQLException {
        String[][] scripts = {
            {"Ticket.Close", "Ticket.Close.xml", "0a69099a45951406a5c35540283a1d85b89ad69508d633c46b6e62e7b2b322b7", "19a77fbf7b87e823a3dd3d0d213a2bcdab4ff0dfc52814d8e8f2f2ccd61b971c", "35996d4c1eb3ed8a4b769ce6ae72882280e6d0b0b763672a631262ad721f2195", "41dbcd9ac38bdaa7790f71cd0dc1edbc2214d47fd730febfc5812c91d5db39c0", "6fe5025f33382e5d34221bfc696d8e85882d49a8345808a9c1c0f918cf4c69b9", "9bf576f0157ef9ca8b363a8378e8c8fdc5f37e9d4f3d95ad9fc822587347088e", "f17ba88a98d1149eee290969fa9eb86978fbbae32e800fa647eef0bf74f51f1a"},
            {"script.totaldiscount", "script.totaldiscount.txt", "ee4a2ded0cfad63cc384d93dcc504cccbc663768512d439d4f0eaaca538e81d7"},
            {"script.linediscount", "script.linediscount.txt", "04c538064d73bacf62220d3dc1cb04440225bd795153125036f208553ca3a637", "55b5a63c1ad5ad175b20231ad0c8ff33341ca3f5d2b28cbf8f3c3d4a3b058cbc"},
            {"script.AddLineNote", "script.AddLineNote.txt", "4177a30828ce459e0d8358dab80baf8878451d2ceceebd7e4531874b7b21465d", "ab3cf7a34ebb0b392ce62f231c6b1407c2bae3b2c07e378774574ea09407e34d"},
            {"script.SetPerson", "script.SetPerson.txt", "6c89bfa1995e17d3a2f002fe5688574e4b4df15541120df0c9502d288653eed8", "a038e311394609f5733ff54a9bc54a570c89a5d954ab8d74f385e9b2b284b79c"},
            {"script.SendOrder", "script.SendOrder.txt", "2fffcdf5342a9cf451664c3d2c6d39c3b9728bc0cd1bf817dcdd8dbb8b9b1aba", "ec27c8899559569d5b6e5befabbd4cffca4d28c7924091c3d5d9abb454782733"},
            {"script.Event.Total", "script.Event.Total.txt", "5455dbfd17de9c20b1315c48aec50ec7e1f8039fe8bdbcd0382357118a4c189c", "a9882e11dad80917cb06ac659db080aa8d8bb06d35c5924f46f8048828ff2eff"},
            {"script.StockCurrentAdd", "script.StockCurrentAdd.txt", "3b9166743586ddb7ae64af2eac6eb2d8b6c7ad75739f075bb5746da7b7ee7865", "9037f82002ce34eb6828a11e6661700767a872038212a1543638e622b0714cf2"},
            {"script.StockCurrentSet", "script.StockCurrentSet.txt", "0b8f359febe454a52fdb999844feb4df506793cdfef99f0d5d9980f8fc98e7ef", "bf25943c7167007661e10efa0ffcceb08f835a95e56a107e78860ae407542ea1"},
        };
        for (String[] sc : scripts) {
            byte[] actual = leerRecursoBytes(con, sc[0]);
            if (actual == null) {
                continue;
            }
            String huella = sha256(new String(actual, StandardCharsets.UTF_8).replace("\r\n", "\n").trim()
                    .getBytes(StandardCharsets.UTF_8));
            for (int i = 2; i < sc.length; i++) {
                if (sc[i].equals(huella)) {
                    byte[] nuevo = leerClasspath("/com/openbravo/pos/templates/" + sc[1]);
                    if (nuevo != null) {
                        guardarRecursoBytes(con, sc[0], nuevo);
                        LOG.info(sc[0] + ": script actualizado");
                    }
                    break;
                }
            }
        }
    }

    /**
     * Aviso de licencia de Quinde POS (en espanol) en todos los recursos de texto y en los roles,
     * en lugar del original en ingles. Conserva el copyright y la licencia GPL; el resto no cambia.
     */
    private static void avisoDeLicenciaQuinde(Connection con) throws SQLException {
        int n = 0;
        java.util.List<String> nombres = new java.util.ArrayList<String>();
        try (PreparedStatement ps = con.prepareStatement("SELECT NAME FROM RESOURCES WHERE RESTYPE = 0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                nombres.add(rs.getString(1));
            }
        }
        for (String nombre : nombres) {
            String actual = leerRecurso(con, nombre);
            String nuevo = EncabezadoLicencia.reemplazar(actual);
            if (actual != null && !nuevo.equals(actual)) {
                guardarRecurso(con, nombre, nuevo);
                n++;
            }
        }
        java.util.List<String> roles = new java.util.ArrayList<String>();
        try (PreparedStatement ps = con.prepareStatement("SELECT NAME FROM ROLES");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roles.add(rs.getString(1));
            }
        }
        for (String rol : roles) {
            String actual = leerPermisos(con, rol);
            String nuevo = EncabezadoLicencia.reemplazar(actual);
            if (actual != null && !nuevo.equals(actual)) {
                guardarPermisos(con, rol, nuevo);
                n++;
            }
        }
        if (n > 0) {
            LOG.info("Aviso de licencia de Quinde POS en " + n + " recursos y roles");
        }
    }

    /** Ticket.Buttons: quita el comentario de los viejos botones "Facturar SRI SI/NO", que ya no existen. */
    private static void limpiarComentarioSri(Connection con) throws SQLException {
        String actual = leerRecurso(con, "Ticket.Buttons");
        if (actual != null && actual.contains("SRI e-invoicing GLOBAL toggle")) {
            String limpio = actual.replaceAll("(?s)[ \t]*<!-- SET SRI e-invoicing GLOBAL toggle.*?-->\r?\n?", "");
            if (!limpio.equals(actual)) {
                guardarRecurso(con, "Ticket.Buttons", limpio);
                LOG.info("Ticket.Buttons: comentario de los botones SRI viejos quitado");
            }
        }
    }

    private static String sha256(byte[] datos) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(datos)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static byte[] leerClasspath(String ruta) {
        try (java.io.InputStream in = ActualizacionesEcoPos.class.getResourceAsStream(ruta)) {
            if (in == null) {
                return null;
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Reemplaza (o quita, si nuevaLinea es null) cada linea que contiene el marcador, conservando su sangria. */
    static String reemplazarLinea(String texto, String marcador, String nuevaLinea) {
        StringBuilder sb = new StringBuilder();
        int inicio = 0;
        while (inicio < texto.length()) {
            int fin = texto.indexOf('\n', inicio);
            fin = fin < 0 ? texto.length() : fin + 1;
            String linea = texto.substring(inicio, fin);
            if (linea.contains(marcador)) {
                if (nuevaLinea != null) {
                    String salto = linea.endsWith("\r\n") ? "\r\n" : linea.endsWith("\n") ? "\n" : "";
                    int j = 0;
                    while (j < linea.length() && (linea.charAt(j) == ' ' || linea.charAt(j) == '\t')) {
                        j++;
                    }
                    sb.append(linea, 0, j).append(nuevaLinea).append(salto);
                }
            } else {
                sb.append(linea);
            }
            inicio = fin;
        }
        return sb.toString();
    }

    /**
     * Agrega una opcion de menu justo despues de la linea que termina en
     * {@code ancla} (con su misma sangria) y el permiso para Administrador y
     * Gerente. Idempotente.
     */
    private static void agregarPanelAdministrativo(Connection con, String clase, String icono, String clave, String ancla)
            throws SQLException {
        agregarOpcionMenu(con, "addPanel", clase, icono, clave, ancla, new String[]{"Administrador", "Gerente"});
    }

    /**
     * Agrega una opcion al Menu.Root despues de la linea 'ancla' (addPanel para una pantalla,
     * addExecution para una accion) y el permiso para los roles indicados. Idempotente.
     */
    private static void agregarOpcionMenu(Connection con, String metodo, String clase, String icono, String clave,
            String ancla, String[] roles) throws SQLException {
        String menu = leerRecurso(con, "Menu.Root");
        if (menu != null && !menu.contains(clase) && menu.contains(ancla)) {
            int fin = menu.indexOf(ancla) + ancla.length();
            int inicioLinea = menu.lastIndexOf('\n', fin) + 1;
            String sangria = sangriaDeLinea(menu, menu.indexOf("group.", inicioLinea));
            menu = menu.substring(0, fin) + "\n" + sangria
                    + "group." + metodo + "(\"" + icono + "\", \"" + clave + "\", \"" + clase + "\");"
                    + menu.substring(fin);
            guardarRecurso(con, "Menu.Root", menu);
            LOG.info("Menu.Root: agregado " + clave);
        }
        String permiso = "<class name=\"" + clase + "\"/>";
        for (String rol : roles) {
            String permisos = leerPermisos(con, rol);
            if (permisos != null && !permisos.contains(permiso) && permisos.contains(ANCLA_PERMISO_VENTAS)) {
                guardarPermisos(con, rol, permisos.replace(ANCLA_PERMISO_VENTAS, ANCLA_PERMISO_VENTAS + "\n    " + permiso));
            }
        }
    }

    /** Fase E: Administrador y Gerente no necesitan autorizacion de supervisor. */
    private static void agregarPermisoSinAutorizacion(Connection con) throws SQLException {
        String permiso = "<class name=\"" + AutorizacionSupervisor.PERMISO + "\"/>";
        for (String rol : new String[]{"Administrador", "Gerente"}) {
            String permisos = leerPermisos(con, rol);
            if (permisos != null && !permisos.contains(permiso) && permisos.contains(ANCLA_PERMISO_VENTAS)) {
                guardarPermisos(con, rol, permisos.replace(ANCLA_PERMISO_VENTAS, ANCLA_PERMISO_VENTAS + "\n    " + permiso));
                LOG.info("Rol " + rol + ": no necesita autorizacion de supervisor");
            }
        }
    }

    /** Fase E: registro de autorizaciones de supervisor. */
    private static void crearTablaAuditoria(Connection con) {
        try (java.sql.Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ecopos_auditoria ("
                    + "id VARCHAR(36) NOT NULL, fecha TIMESTAMP NOT NULL, usuario VARCHAR(255), "
                    + "supervisor VARCHAR(255), accion VARCHAR(100), detalle VARCHAR(500), PRIMARY KEY (id))");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo crear la tabla ecopos_auditoria", e);
        }
    }

    /** Fase E: arqueos de caja por denominacion. */
    private static void crearTablaArqueos(Connection con) {
        try (java.sql.Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ecopos_arqueos ("
                    + "id VARCHAR(36) NOT NULL, caja VARCHAR(255), fecha TIMESTAMP NOT NULL, usuario VARCHAR(255), "
                    + "fondo DOUBLE, contado DOUBLE, esperado DOUBLE, diferencia DOUBLE, detalle VARCHAR(1000), PRIMARY KEY (id))");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo crear la tabla ecopos_arqueos", e);
        }
    }

    private static String sangriaDeLinea(String texto, int posicion) {
        int inicio = texto.lastIndexOf('\n', posicion - 1) + 1;
        StringBuilder sangria = new StringBuilder();
        for (int j = inicio; j < posicion && Character.isWhitespace(texto.charAt(j)); j++) {
            sangria.append(texto.charAt(j));
        }
        return sangria.toString();
    }

    private static String leerRecurso(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT CONTENT FROM RESOURCES WHERE NAME = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getBytes(1) == null) {
                    return null;
                }
                return new String(rs.getBytes(1), StandardCharsets.UTF_8);
            }
        }
    }

    private static byte[] leerRecursoBytes(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT CONTENT FROM RESOURCES WHERE NAME = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    private static void guardarRecursoBytes(Connection con, String nombre, byte[] contenido) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE RESOURCES SET CONTENT = ? WHERE NAME = ?")) {
            ps.setBytes(1, contenido);
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
    }

    private static void guardarRecurso(Connection con, String nombre, String contenido) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE RESOURCES SET CONTENT = ? WHERE NAME = ?")) {
            ps.setBytes(1, contenido.getBytes(StandardCharsets.UTF_8));
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
    }

    private static String leerPermisos(Connection con, String rol) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT PERMISSIONS FROM ROLES WHERE NAME = ?")) {
            ps.setString(1, rol);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getBytes(1) == null) {
                    return null;
                }
                return new String(rs.getBytes(1), StandardCharsets.UTF_8);
            }
        }
    }

    private static void guardarPermisos(Connection con, String rol, String permisos) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE ROLES SET PERMISSIONS = ? WHERE NAME = ?")) {
            ps.setBytes(1, permisos.getBytes(StandardCharsets.UTF_8));
            ps.setString(2, rol);
            ps.executeUpdate();
        }
    }
}
