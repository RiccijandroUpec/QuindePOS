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
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron aplicar las actualizaciones de EcoPos", e);
        }
    }

    /** Fase D: "Panel del negocio" como primera opcion del menu, para Administrador y Gerente. */
    private static void agregarPanelNegocio(Connection con) throws SQLException {
        String menu = leerRecurso(con, "Menu.Root");
        if (menu != null && !menu.contains(CLASE_PANEL_NEGOCIO) && menu.contains(ANCLA_MENU_VENTAS)) {
            String linea = "group.addPanel(\"/com/openbravo/images/chart.png\", \"Menu.Dashboard\", \"" + CLASE_PANEL_NEGOCIO + "\");";
            int i = menu.indexOf(ANCLA_MENU_VENTAS);
            String sangria = sangriaDeLinea(menu, i);
            menu = menu.substring(0, i) + linea + "\n" + sangria + menu.substring(i);
            guardarRecurso(con, "Menu.Root", menu);
            LOG.info("Menu.Root: agregado el Panel del negocio");
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
