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
