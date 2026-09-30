package com.openbravo.pos.sri.repository;

import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD sobre la tabla propia {@code ecopos_sri_comprobantes} (ver
 * src/main/resources/sql/001_create_ecopos_sri_comprobantes.sql). Nunca
 * toca ninguna tabla original de ECOPos.
 *
 * La conexion se inyecta (no se abre/cierra por llamada): en el modo
 * fusionado (mismo proceso que ECOPos) su ciclo de vida es del puente
 * ({@code EcoPosSriBridgeImpl}), no de este DAO.
 */
public class ComprobanteRepository {

    private final Connection connection;

    public ComprobanteRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Estado actual del comprobante de un ticket, si ya se creo alguno.
     * No reconstruye el {@link Comprobante} completo (lineas/impuestos no
     * se persisten normalizados en esta tabla, solo el XML ya generado) -
     * esto es solo para el chequeo de idempotencia ("ya se proceso este
     * ticket, y en que estado quedo?").
     */
    public Optional<EstadoComprobante> buscarEstadoPorTicketId(String ticketId) throws SQLException {
        String sql = "SELECT estado FROM ecopos_sri_comprobantes WHERE ticket_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(EstadoComprobante.valueOf(rs.getString("estado")));
            }
        }
    }

    /**
     * Datos minimos de un comprobante ya existente, necesarios para
     * reintentar su procesamiento sin generar un secuencial/claveAcceso
     * distinto al ya usado (el SRI exige reenviar con la MISMA clave de
     * acceso cuando se corrige un rechazo - ver seccion 5.10 de la ficha
     * tecnica).
     */
    public static final class RegistroExistente {
        public final String id;
        public final String secuencial;
        public final String claveAcceso;
        public final EstadoComprobante estado;
        public final int intentos;

        RegistroExistente(String id, String secuencial, String claveAcceso, EstadoComprobante estado, int intentos) {
            this.id = id;
            this.secuencial = secuencial;
            this.claveAcceso = claveAcceso;
            this.estado = estado;
            this.intentos = intentos;
        }
    }

    public Optional<RegistroExistente> buscarPorTicketId(String ticketId) throws SQLException {
        String sql = "SELECT id, secuencial, clave_acceso, estado, intentos " +
            "FROM ecopos_sri_comprobantes WHERE ticket_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RegistroExistente(
                        rs.getString("id"),
                        rs.getString("secuencial"),
                        rs.getString("clave_acceso"),
                        EstadoComprobante.valueOf(rs.getString("estado")),
                        rs.getInt("intentos")));
            }
        }
    }

    /** Datos de la factura AUTORIZADA que se necesitan para construir su Nota de Credito (anulacion). */
    public static final class FacturaParaAnular {
        public final String id;
        public final String xmlAutorizado;

        FacturaParaAnular(String id, String xmlAutorizado) {
            this.id = id;
            this.xmlAutorizado = xmlAutorizado;
        }
    }

    /**
     * La factura (tipo_comprobante='01') AUTORIZADA identificada por su
     * ticket_id, con el XML que devolvio el SRI ya autorizado - lo que
     * necesita {@code AnulacionService} para construir la Nota de Credito
     * que la anula. Vacio si el ticket no existe, no es una factura, o no
     * esta AUTORIZADA (no tiene sentido anular algo que el SRI rechazo o
     * que aun no se autorizo).
     */
    public Optional<FacturaParaAnular> buscarFacturaAutorizadaParaAnular(String ticketId) throws SQLException {
        String sql = "SELECT id, xml_respuesta_sri FROM ecopos_sri_comprobantes " +
            "WHERE ticket_id = ? AND tipo_comprobante = '01' AND estado = 'AUTORIZADO'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FacturaParaAnular(rs.getString("id"), rs.getString("xml_respuesta_sri")));
            }
        }
    }

    /**
     * XML de las notas de credito ya emitidas contra una factura (todas menos
     * las RECHAZADAS: una en ERROR/ENVIADO todavia puede quedar autorizada en
     * un reintento, asi que cuenta como devuelta).
     */
    public List<String> xmlNotasCreditoVigentesDe(String facturaOriginalId) throws SQLException {
        String sql = "SELECT xml_generado FROM ecopos_sri_comprobantes " +
            "WHERE tipo_comprobante = '04' AND comprobante_original_id = ? AND estado <> 'RECHAZADO'";
        List<String> xmls = new java.util.ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, facturaOriginalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    xmls.add(rs.getString(1));
                }
            }
        }
        return xmls;
    }

    public boolean existePorTicketId(String ticketId) throws SQLException {
        String sql = "SELECT 1 FROM ecopos_sri_comprobantes WHERE ticket_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insertar(Comprobante c) throws SQLException {
        String sql = "INSERT INTO ecopos_sri_comprobantes " +
            "(id, ticket_id, tipo_comprobante, comprobante_original_id, motivo, secuencial, ambiente, estado, fecha_emision, intentos) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getTicketId());
            ps.setString(3, c.getTipo().getCodigo());
            ps.setString(4, c.getComprobanteOriginalId());
            ps.setString(5, c.getMotivo());
            ps.setString(6, c.getSecuencial());
            ps.setString(7, c.getAmbiente().name());
            ps.setString(8, c.getEstado().name());
            ps.setTimestamp(9, Timestamp.valueOf(c.getFechaEmision()));
            ps.setInt(10, c.getIntentos());
            ps.executeUpdate();
        }
    }

    /**
     * Siguiente secuencial de 9 digitos a asignar para un tipo de
     * comprobante (el mayor ya usado + 1, con ceros a la izquierda) -
     * cada codDoc lleva su propia numeracion consecutiva (ficha tecnica,
     * seccion 4): una factura y una nota de credito NO comparten secuencia,
     * aunque se emitan el mismo dia. Devuelve "000000001" si todavia no
     * existe ningun comprobante de ese tipo. Asume un unico establecimiento/
     * punto de emision por instalacion del conector (ver DatosEmisor) - si
     * en el futuro se soportan varios, este metodo debe filtrar tambien por
     * ellos.
     *
     * No es seguro ante llamadas concurrentes (no hay bloqueo/transaccion);
     * suficiente mientras el conector procese un comprobante a la vez.
     */
    public String siguienteSecuencial(TipoComprobante tipo) throws SQLException {
        String sql = "SELECT MAX(CAST(secuencial AS UNSIGNED)) FROM ecopos_sri_comprobantes " +
            "WHERE secuencial IS NOT NULL AND tipo_comprobante = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tipo.getCodigo());
            try (ResultSet rs = ps.executeQuery()) {
                long maximo = 0;
                if (rs.next()) {
                    maximo = rs.getLong(1);
                }
                return String.format("%09d", maximo + 1);
            }
        }
    }

    public void actualizarProgreso(Comprobante c) throws SQLException {
        String sql = "UPDATE ecopos_sri_comprobantes SET " +
            "clave_acceso = ?, numero_autorizacion = ?, estado = ?, " +
            "xml_generado = ?, xml_firmado = ?, xml_respuesta_sri = ?, " +
            "mensaje_error = ?, intentos = ?, fecha_autorizacion = ? " +
            "WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, c.getClaveAcceso());
            ps.setString(2, c.getNumeroAutorizacion());
            ps.setString(3, c.getEstado().name());
            ps.setString(4, c.getXmlGenerado());
            ps.setString(5, c.getXmlFirmado());
            ps.setString(6, c.getXmlRespuestaSri());
            ps.setString(7, c.getMensajeError());
            ps.setInt(8, c.getIntentos());
            ps.setTimestamp(9, c.getFechaAutorizacion() != null ? Timestamp.valueOf(c.getFechaAutorizacion()) : null);
            ps.setString(10, c.getId());
            ps.executeUpdate();
        }
    }

    /** IDs de ticket cuyo comprobante quedo en ERROR y aun no agota reintentos, para el planificador. */
    public List<String> listarTicketIdsParaReintentar(int maxIntentos) throws SQLException {
        String sql = "SELECT ticket_id FROM ecopos_sri_comprobantes " +
            "WHERE estado IN ('ERROR', 'ENVIADO') AND intentos < ? " +
            "ORDER BY fecha_creacion";
        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, maxIntentos);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("ticket_id"));
                }
            }
        }
        return ids;
    }

    /** Una fila del historial de facturacion, enriquecida con el numero de ticket de ECOPos (mas legible que el UUID interno). */
    public static final class RegistroHistorial {
        public final String id;
        public final String ticketId;
        /** Null para una Nota de Credito (no corresponde a ningun TICKETS.ID real - ver 002_agregar_nota_credito.sql). */
        public final Integer numeroTicket;
        public final LocalDateTime fechaEmision;
        public final String secuencial;
        public final String claveAcceso;
        public final EstadoComprobante estado;
        public final String numeroAutorizacion;
        public final String mensajeError;
        public final int intentos;
        public final TipoComprobante tipoComprobante;
        /** Solo para Nota de Credito: id (de esta misma tabla) de la factura que anula. */
        public final String comprobanteOriginalId;
        public final String motivo;

        RegistroHistorial(String id, String ticketId, Integer numeroTicket, LocalDateTime fechaEmision, String secuencial,
                           String claveAcceso, EstadoComprobante estado, String numeroAutorizacion,
                           String mensajeError, int intentos, TipoComprobante tipoComprobante,
                           String comprobanteOriginalId, String motivo) {
            this.id = id;
            this.ticketId = ticketId;
            this.numeroTicket = numeroTicket;
            this.fechaEmision = fechaEmision;
            this.secuencial = secuencial;
            this.claveAcceso = claveAcceso;
            this.estado = estado;
            this.numeroAutorizacion = numeroAutorizacion;
            this.mensajeError = mensajeError;
            this.intentos = intentos;
            this.tipoComprobante = tipoComprobante;
            this.comprobanteOriginalId = comprobanteOriginalId;
            this.motivo = motivo;
        }
    }

    /** Los tres XML guardados para un comprobante, para inspeccion/exportacion desde el historial. */
    public static final class XmlComprobante {
        public final String xmlGenerado;
        public final String xmlFirmado;
        public final String xmlRespuestaSri;
        /** Fecha/hora en que el SRI autorizo (columna propia, no viene en el XML del comprobante) - la necesita el RIDE. */
        public final LocalDateTime fechaAutorizacion;

        XmlComprobante(String xmlGenerado, String xmlFirmado, String xmlRespuestaSri, LocalDateTime fechaAutorizacion) {
            this.xmlGenerado = xmlGenerado;
            this.xmlFirmado = xmlFirmado;
            this.xmlRespuestaSri = xmlRespuestaSri;
            this.fechaAutorizacion = fechaAutorizacion;
        }

        /** El mas "final" disponible: la respuesta autorizada del SRI si existe, si no el firmado, si no el generado sin firmar. */
        public String masReciente() {
            if (xmlRespuestaSri != null && !xmlRespuestaSri.isBlank()) {
                return xmlRespuestaSri;
            }
            if (xmlFirmado != null && !xmlFirmado.isBlank()) {
                return xmlFirmado;
            }
            return xmlGenerado;
        }
    }

    /**
     * Trae los XML (MEDIUMTEXT, no se piden en {@link #listarHistorial()}
     * para no cargar contenido pesado innecesariamente al mostrar la lista).
     */
    public Optional<XmlComprobante> obtenerXml(String ticketId) throws SQLException {
        String sql = "SELECT xml_generado, xml_firmado, xml_respuesta_sri, fecha_autorizacion " +
            "FROM ecopos_sri_comprobantes WHERE ticket_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp fechaAutorizacion = rs.getTimestamp("fecha_autorizacion");
                return Optional.of(new XmlComprobante(
                        rs.getString("xml_generado"),
                        rs.getString("xml_firmado"),
                        rs.getString("xml_respuesta_sri"),
                        fechaAutorizacion != null ? fechaAutorizacion.toLocalDateTime() : null));
            }
        }
    }

    /**
     * Historial completo de comprobantes, mas reciente primero. Hace JOIN
     * de solo lectura con TICKETS (ECOPos) unicamente para mostrar el
     * numero de ticket humano en vez del UUID interno - no escribe nada
     * en tablas de ECOPos.
     */
    public List<RegistroHistorial> listarHistorial() throws SQLException {
        String sql = "SELECT c.id, c.ticket_id, t.TICKETID, c.fecha_emision, c.secuencial, c.clave_acceso, " +
            "c.estado, c.numero_autorizacion, c.mensaje_error, c.intentos, " +
            "c.tipo_comprobante, c.comprobante_original_id, c.motivo " +
            "FROM ecopos_sri_comprobantes c " +
            "LEFT JOIN TICKETS t ON t.ID = c.ticket_id " +
            "ORDER BY c.fecha_emision DESC";
        List<RegistroHistorial> historial = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp fechaEmision = rs.getTimestamp("fecha_emision");
                historial.add(new RegistroHistorial(
                        rs.getString("id"),
                        rs.getString("ticket_id"),
                        (Integer) rs.getObject("TICKETID"),
                        fechaEmision != null ? fechaEmision.toLocalDateTime() : null,
                        rs.getString("secuencial"),
                        rs.getString("clave_acceso"),
                        EstadoComprobante.valueOf(rs.getString("estado")),
                        rs.getString("numero_autorizacion"),
                        rs.getString("mensaje_error"),
                        rs.getInt("intentos"),
                        TipoComprobante.porCodigo(rs.getString("tipo_comprobante")),
                        rs.getString("comprobante_original_id"),
                        rs.getString("motivo")));
            }
        }
        return historial;
    }
}
