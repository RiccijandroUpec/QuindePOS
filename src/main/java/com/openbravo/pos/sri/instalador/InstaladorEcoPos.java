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
 * existente: crea la tabla propia del conector si falta y limpia lo que
 * dejaron versiones anteriores (bloque .flag de Ticket.Close, servicio de
 * Windows). Las pantallas, menus y permisos de facturacion los agrega EcoPos
 * solo al abrirse. Idempotente: se puede correr las veces que sea.
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

    /** Inicio, contenido distintivo y fin del bloque viejo (modo servicio separado) de Ticket.Close que dejaba un .flag en sri-conector/pendientes/. */
    private static final String MARCADOR_TICKET_CLOSE_VIEJO = "// --- ecopos-sri-connector hook";
    private static final String CONTENIDO_TICKET_CLOSE_VIEJO = "sri-conector/pendientes";
    private static final String FIN_TICKET_CLOSE_VIEJO = "ecopos_sri_comprobantes.";

    private InstaladorEcoPos() {
    }

    public static void main(String[] args) throws Exception {
        Path archivoConexion = args.length > 0 ? Path.of(args[0]) : Path.of("config/conexion.properties");
        DataSource dataSource = ConexionLoader.cargar(archivoConexion);

        try (Connection con = dataSource.getConnection()) {
            System.out.println("Conectado a la base de datos de EcoPos. Instalando/actualizando ecopos-sri-connector...\n");

            crearTablaPropiaSiFalta(con);
            agregarColumnasNotaCreditoSiFaltan(con);

            quitarHookViejoTicketClose(con);

            // Desde 2026-09 las pantallas "Facturacion electronica" y "Comprobantes
            // electronicos", sus permisos y el retiro de los botones SRI SI/NO los
            // aplica EcoPos solo al abrirse (ActualizacionesEcoPos): aqui ya no se
            // tocan Menu.Root, Ticket.Buttons ni ROLES.
            System.out.println("[=] Menus y permisos: EcoPos los agrega solo la proxima vez que se abra.");

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








    // --- RESOURCES: helpers genericos ---


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
