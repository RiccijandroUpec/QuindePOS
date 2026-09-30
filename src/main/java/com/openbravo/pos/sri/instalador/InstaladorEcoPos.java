package com.openbravo.pos.sri.instalador;

import com.openbravo.pos.sri.config.ConexionLoader;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Instala/actualiza ecopos-sri-connector en una base de datos de EcoPos ya
 * existente: crea la tabla propia del conector si falta, y sincroniza los
 * "hooks" data-only (Menu.Root, Ticket.Buttons, Ticket.Close, los scripts
 * de facturacion, sus iconos, y los permisos de rol) - todo idempotente,
 * se puede correr las veces que sea sin duplicar nada ni pisar
 * personalizaciones ajenas.
 *
 * Pensado para dos escenarios:
 * <ul>
 *   <li>Una instalacion de EcoPos NUEVA (sembrada desde
 *       {@code MySQL-create.sql}, que ya incluye estos mismos hooks en sus
 *       archivos plantilla) - aqui este instalador solo necesita crear la
 *       tabla propia del conector, el resto ya viene listo.</li>
 *   <li>Una instalacion YA EXISTENTE (como la que se uso para desarrollar
 *       este conector) - aqui {@code RESOURCES}/{@code ROLES} tienen el
 *       contenido con el que se sembro esa base en su momento, sin los
 *       hooks nuevos, y hay que agregarselos en caliente.</li>
 * </ul>
 *
 * No depende de tener el repo de EcoPos a mano: las plantillas necesarias
 * viven empaquetadas en este mismo jar ({@code src/main/resources/plantillas-ecopos/}).
 */
public final class InstaladorEcoPos {

    private static final String CARPETA_PLANTILLAS = "/plantillas-ecopos/";

    private static final String MARCADOR_MENU_ROOT = "Menu.SriConnectorHistorial";
    private static final String MARCADOR_TICKET_BUTTONS = "button.sriinvoiceon";
    /** Inicio, contenido distintivo y fin del bloque viejo (modo servicio separado) de Ticket.Close que dejaba un .flag en sri-conector/pendientes/. */
    private static final String MARCADOR_TICKET_CLOSE_VIEJO = "// --- ecopos-sri-connector hook";
    private static final String CONTENIDO_TICKET_CLOSE_VIEJO = "sri-conector/pendientes";
    private static final String FIN_TICKET_CLOSE_VIEJO = "ecopos_sri_comprobantes.";
    private static final String MARCADOR_ROLE_ADMINISTRADOR = "SriConnectorConfig.bs";
    private static final String MARCADOR_ROLE_GERENTE = "SriConnectorHistorial.bs";
    private static final String MARCADOR_ROLE_EMPLEADO = "button.sriinvoiceon";

    private InstaladorEcoPos() {
    }

    public static void main(String[] args) throws Exception {
        Path archivoConexion = args.length > 0 ? Path.of(args[0]) : Path.of("config/conexion.properties");
        DataSource dataSource = ConexionLoader.cargar(archivoConexion);

        try (Connection con = dataSource.getConnection()) {
            System.out.println("Conectado a la base de datos de EcoPos. Instalando/actualizando ecopos-sri-connector...\n");

            crearTablaPropiaSiFalta(con);
            agregarColumnasNotaCreditoSiFaltan(con);

            asegurarFragmentoAlFinal(con, "Menu.Root", MARCADOR_MENU_ROOT, "menu-root-fragmento.txt");
            asegurarFragmentoAntesDeCierre(con, "Ticket.Buttons", MARCADOR_TICKET_BUTTONS, "ticket-buttons-fragmento.txt", "</configuration>");
            quitarHookViejoTicketClose(con);

            asegurarRecursoCompleto(con, "script.SriInvoiceOn", 0, "script-sri-invoice-on.txt");
            asegurarRecursoCompleto(con, "script.SriInvoiceOff", 0, "script-sri-invoice-off.txt");
            asegurarImagen(con, "img.sriinvoiceon", "img-sriinvoiceon.png");
            asegurarImagen(con, "img.sriinvoiceoff", "img-sriinvoiceoff.png");

            asegurarPermisoRol(con, "Administrador", MARCADOR_ROLE_ADMINISTRADOR, "role-administrador-fragmento.txt");
            asegurarPermisoRol(con, "Gerente", MARCADOR_ROLE_GERENTE, "role-gerente-fragmento.txt");
            asegurarPermisoRol(con, "Empleado", MARCADOR_ROLE_EMPLEADO, "role-empleado-fragmento.txt");

            System.out.println("\nListo. ecopos-sri-connector esta instalado/actualizado en esta base de datos.");
        }

        ServicioWindowsViejo.retirarSiExiste(Path.of("."));
    }

    // --- tabla propia del conector -----------------------------------------

    private static void crearTablaPropiaSiFalta(Connection con) throws SQLException, IOException {
        if (existeTabla(con, "ecopos_sri_comprobantes")) {
            System.out.println("[=] Tabla ecopos_sri_comprobantes ya existe.");
            return;
        }
        ejecutarScriptSql(con, "/sql/001_create_ecopos_sri_comprobantes.sql");
        System.out.println("[+] Tabla ecopos_sri_comprobantes creada.");
    }

    private static void agregarColumnasNotaCreditoSiFaltan(Connection con) throws SQLException, IOException {
        if (existeColumna(con, "ecopos_sri_comprobantes", "tipo_comprobante")) {
            System.out.println("[=] Columnas de Nota de Credito ya existen en ecopos_sri_comprobantes.");
            return;
        }
        ejecutarScriptSql(con, "/sql/002_agregar_nota_credito.sql");
        System.out.println("[+] Columnas de Nota de Credito agregadas a ecopos_sri_comprobantes.");
    }

    private static boolean existeTabla(Connection con, String nombreTabla) throws SQLException {
        try (ResultSet rs = con.getMetaData().getTables(con.getCatalog(), null, nombreTabla, null)) {
            return rs.next();
        }
    }

    private static boolean existeColumna(Connection con, String nombreTabla, String nombreColumna) throws SQLException {
        try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, nombreTabla, nombreColumna)) {
            return rs.next();
        }
    }

    /** Ejecuta un script .sql (empaquetado en este jar) statement por statement - separados por ";", ignorando lineas de comentario "--". */
    private static void ejecutarScriptSql(Connection con, String rutaClasspath) throws SQLException, IOException {
        String contenido = leerRecursoTexto(rutaClasspath);
        StringBuilder sinComentarios = new StringBuilder();
        for (String linea : contenido.split("\n")) {
            if (linea.trim().startsWith("--")) {
                continue;
            }
            sinComentarios.append(linea).append('\n');
        }
        try (Statement st = con.createStatement()) {
            for (String sentencia : sinComentarios.toString().split(";")) {
                String limpia = sentencia.trim();
                if (!limpia.isEmpty()) {
                    st.execute(limpia);
                }
            }
        }
    }

    // --- RESOURCES: agregar un fragmento si el marcador todavia no esta presente ---

    private static void asegurarFragmentoAlFinal(Connection con, String nombreRecurso, String marcador, String plantilla) throws SQLException, IOException {
        String actual = leerContenidoTexto(con, nombreRecurso);
        if (actual == null) {
            System.out.println("[!] No se encontro el recurso '" + nombreRecurso + "' en RESOURCES - se omite (¿EcoPos sin sembrar todavia?)");
            return;
        }
        if (actual.contains(marcador)) {
            System.out.println("[=] " + nombreRecurso + " ya tiene el hook de ecopos-sri-connector.");
            return;
        }
        String fragmento = leerRecursoTexto(CARPETA_PLANTILLAS + plantilla);
        actualizarContenidoTexto(con, nombreRecurso, actual + "\n" + fragmento);
        System.out.println("[+] " + nombreRecurso + " actualizado con el hook de ecopos-sri-connector.");
    }

    /**
     * Desde 2026-07 el conector corre dentro del mismo proceso que ECOPos y
     * la venta lo llama directamente (JPanelTicket.closeTicket) - el bloque
     * que las versiones anteriores de este instalador agregaban al final de
     * Ticket.Close (dejar un .flag para un servicio de Windows aparte) sobra.
     * Si se queda y el servicio viejo sigue corriendo, el mismo ticket se
     * procesaria dos veces. Se quita solo ese bloque, sin tocar el resto del
     * script (que puede tener personalizaciones del negocio).
     */
    private static void quitarHookViejoTicketClose(Connection con) throws SQLException {
        String actual = leerContenidoTexto(con, "Ticket.Close");
        if (actual == null) {
            System.out.println("[!] No se encontro el recurso 'Ticket.Close' en RESOURCES - se omite (¿EcoPos sin sembrar todavia?)");
            return;
        }
        String nuevoContenido = sinHookViejoTicketClose(actual);
        if (nuevoContenido.equals(actual)) {
            System.out.println("[=] Ticket.Close no tiene el hook viejo (archivo .flag) - nada que quitar.");
            return;
        }
        actualizarContenidoTexto(con, "Ticket.Close", nuevoContenido);
        System.out.println("[-] Ticket.Close: quitado el hook viejo (archivo .flag) - ahora EcoPos llama al conector directamente.");
    }

    /** Devuelve el script sin el bloque viejo del .flag, o el mismo texto si no lo tiene. Paquete-privado para las pruebas. */
    static String sinHookViejoTicketClose(String script) {
        int inicio = script.indexOf(MARCADOR_TICKET_CLOSE_VIEJO);
        int finComentario = inicio < 0 ? -1 : script.indexOf(FIN_TICKET_CLOSE_VIEJO, inicio);
        int llaveCierre = finComentario < 0 ? -1 : script.indexOf('}', finComentario);
        if (llaveCierre < 0 || !script.substring(inicio, llaveCierre).contains(CONTENIDO_TICKET_CLOSE_VIEJO)) {
            return script;
        }
        int fin = llaveCierre + 1;
        while (fin < script.length() && (script.charAt(fin) == '\r' || script.charAt(fin) == '\n')) {
            fin++;
        }
        String antes = script.substring(0, inicio).replaceAll("\\s+$", "");
        String despues = script.substring(fin);
        return despues.isEmpty() ? antes + "\n" : antes + "\n" + despues;
    }

    private static void asegurarFragmentoAntesDeCierre(Connection con, String nombreRecurso, String marcador, String plantilla, String etiquetaCierre) throws SQLException, IOException {
        String actual = leerContenidoTexto(con, nombreRecurso);
        if (actual == null) {
            System.out.println("[!] No se encontro el recurso '" + nombreRecurso + "' en RESOURCES - se omite (¿EcoPos sin sembrar todavia?)");
            return;
        }
        if (actual.contains(marcador)) {
            System.out.println("[=] " + nombreRecurso + " ya tiene el hook de ecopos-sri-connector.");
            return;
        }
        String fragmento = leerRecursoTexto(CARPETA_PLANTILLAS + plantilla);
        int posicion = actual.lastIndexOf(etiquetaCierre);
        String nuevoContenido = posicion < 0
                ? actual + "\n" + fragmento
                : actual.substring(0, posicion) + fragmento + actual.substring(posicion);
        actualizarContenidoTexto(con, nombreRecurso, nuevoContenido);
        System.out.println("[+] " + nombreRecurso + " actualizado con el hook de ecopos-sri-connector.");
    }

    /** Para recursos que son ENTERAMENTE nuestros (los scripts propios) - se crean si faltan, se reemplazan si ya existen (siempre a la version empaquetada en este jar). */
    private static void asegurarRecursoCompleto(Connection con, String nombreRecurso, int tipo, String plantilla) throws SQLException, IOException {
        byte[] contenido = leerRecursoBytes(CARPETA_PLANTILLAS + plantilla);
        boolean existia = existeRecurso(con, nombreRecurso);
        guardarRecurso(con, nombreRecurso, tipo, contenido, existia);
        System.out.println((existia ? "[=] " : "[+] ") + nombreRecurso + (existia ? " ya existia (contenido actualizado a la ultima version)." : " creado."));
    }

    private static void asegurarImagen(Connection con, String nombreRecurso, String archivoPng) throws SQLException, IOException {
        if (existeRecurso(con, nombreRecurso)) {
            System.out.println("[=] " + nombreRecurso + " ya existe.");
            return;
        }
        byte[] contenido = leerRecursoBytes(CARPETA_PLANTILLAS + archivoPng);
        guardarRecurso(con, nombreRecurso, 1, contenido, false);
        System.out.println("[+] " + nombreRecurso + " creado.");
    }

    // --- ROLES.PERMISSIONS ---

    private static void asegurarPermisoRol(Connection con, String nombreRol, String marcador, String plantilla) throws SQLException, IOException {
        String actual = leerPermisosRol(con, nombreRol);
        if (actual == null) {
            System.out.println("[!] No se encontro el rol '" + nombreRol + "' en ROLES - se omite.");
            return;
        }
        if (actual.contains(marcador)) {
            System.out.println("[=] Rol " + nombreRol + " ya tiene los permisos de ecopos-sri-connector.");
            return;
        }
        String fragmento = leerRecursoTexto(CARPETA_PLANTILLAS + plantilla);
        int posicionCierre = actual.lastIndexOf("</permissions>");
        String nuevoContenido = posicionCierre < 0
                ? actual + "\n" + fragmento
                : actual.substring(0, posicionCierre) + fragmento + actual.substring(posicionCierre);
        actualizarPermisosRol(con, nombreRol, nuevoContenido);
        System.out.println("[+] Rol " + nombreRol + " actualizado con los permisos de ecopos-sri-connector.");
    }

    private static String leerPermisosRol(Connection con, String nombreRol) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT PERMISSIONS FROM ROLES WHERE NAME = ?")) {
            ps.setString(1, nombreRol);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                byte[] datos = rs.getBytes("PERMISSIONS");
                return datos == null ? "" : new String(datos, StandardCharsets.UTF_8);
            }
        }
    }

    private static void actualizarPermisosRol(Connection con, String nombreRol, String nuevoContenido) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE ROLES SET PERMISSIONS = ? WHERE NAME = ?")) {
            ps.setBytes(1, nuevoContenido.getBytes(StandardCharsets.UTF_8));
            ps.setString(2, nombreRol);
            ps.executeUpdate();
        }
    }

    // --- RESOURCES: helpers genericos ---

    private static boolean existeRecurso(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM RESOURCES WHERE NAME = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String leerContenidoTexto(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT CONTENT FROM RESOURCES WHERE NAME = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                byte[] datos = rs.getBytes("CONTENT");
                return datos == null ? "" : new String(datos, StandardCharsets.UTF_8);
            }
        }
    }

    private static void actualizarContenidoTexto(Connection con, String nombre, String nuevoContenido) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE RESOURCES SET CONTENT = ? WHERE NAME = ?")) {
            ps.setBytes(1, nuevoContenido.getBytes(StandardCharsets.UTF_8));
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
    }

    private static void guardarRecurso(Connection con, String nombre, int tipo, byte[] contenido, boolean existia) throws SQLException {
        if (existia) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE RESOURCES SET CONTENT = ? WHERE NAME = ?")) {
                ps.setBytes(1, contenido);
                ps.setString(2, nombre);
                ps.executeUpdate();
            }
        } else {
            String id = java.util.UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO RESOURCES (ID, NAME, RESTYPE, CONTENT) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, nombre);
                ps.setInt(3, tipo);
                ps.setBytes(4, contenido);
                ps.executeUpdate();
            }
        }
    }

    // --- lectura de plantillas empaquetadas en este jar ---

    private static String leerRecursoTexto(String rutaClasspath) throws IOException {
        return new String(leerRecursoBytes(rutaClasspath), StandardCharsets.UTF_8);
    }

    private static byte[] leerRecursoBytes(String rutaClasspath) throws IOException {
        try (InputStream entrada = InstaladorEcoPos.class.getResourceAsStream(rutaClasspath)) {
            if (entrada == null) {
                throw new IOException("No se encontro la plantilla empaquetada: " + rutaClasspath);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            entrada.transferTo(salida);
            return salida.toByteArray();
        }
    }
}
