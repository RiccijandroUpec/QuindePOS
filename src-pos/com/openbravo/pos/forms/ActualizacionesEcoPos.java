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
     * Agrega una opcion de menu justo despues de la linea que termina en
     * {@code ancla} (con su misma sangria) y el permiso para Administrador y
     * Gerente. Idempotente.
     */
    private static void agregarPanelAdministrativo(Connection con, String clase, String icono, String clave, String ancla)
            throws SQLException {
        String menu = leerRecurso(con, "Menu.Root");
        if (menu != null && !menu.contains(clase) && menu.contains(ancla)) {
            int fin = menu.indexOf(ancla) + ancla.length();
            int inicioLinea = menu.lastIndexOf('\n', fin) + 1;
            String sangria = sangriaDeLinea(menu, menu.indexOf("group.", inicioLinea));
            menu = menu.substring(0, fin) + "\n" + sangria
                    + "group.addPanel(\"" + icono + "\", \"" + clave + "\", \"" + clase + "\");"
                    + menu.substring(fin);
            guardarRecurso(con, "Menu.Root", menu);
            LOG.info("Menu.Root: agregado " + clave);
        }
        String permiso = "<class name=\"" + clase + "\"/>";
        for (String rol : new String[]{"Administrador", "Gerente"}) {
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
