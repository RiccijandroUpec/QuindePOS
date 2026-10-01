package com.openbravo.pos.sri.ui;

import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.dominio.TipoComprobante;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un comprobante electronico tal como se muestra en la pantalla
 * "Comprobantes electronicos": numero legible (001-001-000000123), cliente,
 * total y estado. Cliente y total salen del XML del comprobante (lo que
 * realmente se envio al SRI); si todavia no hay XML, del ticket de EcoPos.
 */
public final class FilaComprobante {

    private static final Pattern RAZON_SOCIAL = Pattern.compile("<razonSocialComprador>([^<]*)</razonSocialComprador>");
    private static final Pattern IDENTIFICACION = Pattern.compile("<identificacionComprador>([^<]*)</identificacionComprador>");
    private static final Pattern IMPORTE_TOTAL = Pattern.compile("<importeTotal>([^<]*)</importeTotal>");
    private static final Pattern VALOR_MODIFICACION = Pattern.compile("<valorModificacion>([^<]*)</valorModificacion>");

    public final String id;
    public final String ticketId;
    public final Integer numeroTicket;
    public final TipoComprobante tipo;
    public final LocalDateTime fechaEmision;
    public final String numero;
    public final String secuencial;
    public final String claveAcceso;
    public final String numeroAutorizacion;
    public final EstadoComprobante estado;
    public final String mensajeError;
    public final int intentos;
    public final String motivo;
    public final String cliente;
    public final String identificacion;
    public final String correoCliente;
    public final BigDecimal total;

    private FilaComprobante(ResultSet rs) throws SQLException {
        id = rs.getString("id");
        ticketId = rs.getString("ticket_id");
        numeroTicket = (Integer) rs.getObject("TICKETID");
        tipo = TipoComprobante.porCodigo(rs.getString("tipo_comprobante"));
        Timestamp fecha = rs.getTimestamp("fecha_emision");
        fechaEmision = fecha == null ? null : fecha.toLocalDateTime();
        secuencial = rs.getString("secuencial");
        claveAcceso = rs.getString("clave_acceso");
        numeroAutorizacion = rs.getString("numero_autorizacion");
        estado = EstadoComprobante.valueOf(rs.getString("estado"));
        mensajeError = rs.getString("mensaje_error");
        intentos = rs.getInt("intentos");
        motivo = rs.getString("motivo");
        String xml = rs.getString("xml_generado");
        String nombre = extraer(RAZON_SOCIAL, xml);
        cliente = nombre != null ? nombre : (rs.getString("NAME") != null ? rs.getString("NAME") : "Consumidor final");
        String ident = extraer(IDENTIFICACION, xml);
        identificacion = ident != null ? ident : rs.getString("TAXID");
        correoCliente = rs.getString("EMAIL");
        String valor = tipo == TipoComprobante.NOTA_CREDITO ? extraer(VALOR_MODIFICACION, xml) : extraer(IMPORTE_TOTAL, xml);
        total = valor != null ? new BigDecimal(valor.trim()) : null;
        numero = numeroLegible(claveAcceso, secuencial);
    }

    /** 001-001-000000123 a partir de la clave de acceso (serie = posiciones 25 a 30). */
    static String numeroLegible(String claveAcceso, String secuencial) {
        if (claveAcceso != null && claveAcceso.length() >= 39) {
            return claveAcceso.substring(24, 27) + "-" + claveAcceso.substring(27, 30) + "-" + claveAcceso.substring(30, 39);
        }
        return secuencial == null ? "(sin número)" : secuencial;
    }

    private static String extraer(Pattern patron, String xml) {
        if (xml == null) {
            return null;
        }
        Matcher m = patron.matcher(xml);
        return m.find() ? m.group(1).replace("&amp;", "&") : null;
    }

    /** Comprobantes con fecha de emision en [desde, hasta), mas recientes primero. Nulls = sin limite. */
    public static List<FilaComprobante> listar(Connection con, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return listar(con, desde, hasta, null);
    }

    private static List<FilaComprobante> listar(Connection con, LocalDateTime desde, LocalDateTime hasta, String ticketId)
            throws SQLException {
        String sql = "SELECT c.id, c.ticket_id, t.TICKETID, c.tipo_comprobante, c.fecha_emision, c.secuencial, "
                + "c.clave_acceso, c.numero_autorizacion, c.estado, c.mensaje_error, c.intentos, c.motivo, c.xml_generado, "
                + "cu.NAME, cu.TAXID, cu.EMAIL "
                + "FROM ecopos_sri_comprobantes c "
                + "LEFT JOIN TICKETS t ON t.ID = c.ticket_id "
                + "LEFT JOIN CUSTOMERS cu ON cu.ID = t.CUSTOMER "
                + "WHERE (? IS NULL OR c.fecha_emision >= ?) AND (? IS NULL OR c.fecha_emision < ?) "
                + "AND (? IS NULL OR c.ticket_id = ?) "
                + "ORDER BY c.fecha_emision DESC";
        List<FilaComprobante> filas = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            Timestamp d = desde == null ? null : Timestamp.valueOf(desde);
            Timestamp h = hasta == null ? null : Timestamp.valueOf(hasta);
            ps.setTimestamp(1, d);
            ps.setTimestamp(2, d);
            ps.setTimestamp(3, h);
            ps.setTimestamp(4, h);
            ps.setString(5, ticketId);
            ps.setString(6, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(new FilaComprobante(rs));
                }
            }
        }
        return filas;
    }

    /** El comprobante (factura) de un ticket de EcoPos, o null. */
    public static FilaComprobante deTicket(Connection con, String ticketId) throws SQLException {
        List<FilaComprobante> filas = listar(con, null, null, ticketId);
        return filas.isEmpty() ? null : filas.get(0);
    }
}
