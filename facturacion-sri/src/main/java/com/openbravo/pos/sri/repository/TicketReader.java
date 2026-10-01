package com.openbravo.pos.sri.repository;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lee un ticket ya cerrado directamente de las tablas de ECOPos
 * (TICKETS/RECEIPTS/TICKETLINES/PAYMENTS/PRODUCTS/TAXES/CUSTOMERS), en
 * modo solo-lectura. Nunca escribe en estas tablas - este conector no
 * modifica el core de ECOPos ni sus datos.
 *
 * La conexion se inyecta (no se abre/cierra por llamada): en el modo
 * fusionado (mismo proceso que ECOPos) su ciclo de vida es del puente
 * ({@code EcoPosSriBridgeImpl}), no de este DAO.
 */
public class TicketReader {

    private static final Logger LOG = LoggerFactory.getLogger(TicketReader.class);
    /** Propiedad por-ticket que script.SriInvoiceOn.txt guarda (mismo mecanismo que "sri.invoice") con el correo que el cajero ingreso para este ticket. */
    private static final String PROPIEDAD_EMAIL = "sri.email";

    private final Connection connection;

    public TicketReader(Connection connection) {
        this.connection = connection;
    }

    public Optional<TicketCrudo> leer(String ticketId) throws SQLException {
        TicketCrudo t = leerCabecera(connection, ticketId);
        if (t == null) {
            return Optional.empty();
        }
        t.lineas = leerLineas(connection, ticketId);
        t.pagos = leerPagos(connection, ticketId);
        return Optional.of(t);
    }

    private TicketCrudo leerCabecera(Connection con, String ticketId) throws SQLException {
        String sql =
            "SELECT t.TICKETTYPE, t.TICKETID, r.DATENEW, r.ATTRIBUTES, t.CUSTOMER, " +
            "       c.TAXID AS CLIENTE_TAXID, c.NAME AS CLIENTE_NOMBRE, " +
            "       c.ADDRESS AS CLIENTE_DIRECCION, c.EMAIL AS CLIENTE_EMAIL, c.PHONE AS CLIENTE_TELEFONO " +
            "FROM TICKETS t " +
            "JOIN RECEIPTS r ON r.ID = t.ID " +
            "LEFT JOIN CUSTOMERS c ON c.ID = t.CUSTOMER " +
            "WHERE t.ID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                TicketCrudo t = new TicketCrudo();
                t.ticketId = ticketId;
                t.ticketType = rs.getInt("TICKETTYPE");
                t.ticketNumero = rs.getInt("TICKETID");
                Timestamp fecha = rs.getTimestamp("DATENEW");
                t.fecha = fecha != null ? fecha.toLocalDateTime() : null;
                t.clienteId = rs.getString("CUSTOMER");
                t.clienteTaxId = rs.getString("CLIENTE_TAXID");
                t.clienteNombre = rs.getString("CLIENTE_NOMBRE");
                t.clienteDireccion = rs.getString("CLIENTE_DIRECCION");
                t.clienteEmail = rs.getString("CLIENTE_EMAIL");
                t.clienteTelefono = rs.getString("CLIENTE_TELEFONO");

                // El correo que el cajero escribio para ESTE ticket (boton "Facturar SRI: SI",
                // script.SriInvoiceOn.txt) manda sobre el del perfil de cliente guardado -
                // es el dato mas reciente/especifico para esta venta en particular.
                String emailDelTicket = leerPropiedadEmail(rs.getBytes("ATTRIBUTES"), ticketId);
                if (emailDelTicket != null && !emailDelTicket.isBlank()) {
                    t.clienteEmail = emailDelTicket;
                }
                return t;
            }
        }
    }

    /**
     * RECEIPTS.ATTRIBUTES guarda las propiedades por-ticket de ECOPos
     * (TicketInfo.attributes) serializadas con {@code Properties.storeToXML}
     * (ver DataLogicSales.java) - se leen de vuelta igual, sin depender de
     * ninguna clase de ECOPos.
     */
    private static String leerPropiedadEmail(byte[] atributos, String ticketId) {
        if (atributos == null) {
            return null;
        }
        try {
            Properties propiedades = new Properties();
            propiedades.loadFromXML(new ByteArrayInputStream(atributos));
            return propiedades.getProperty(PROPIEDAD_EMAIL);
        } catch (Exception e) {
            LOG.warn("No se pudieron leer los atributos del ticket {} (RECEIPTS.ATTRIBUTES) - se ignora el correo manual", ticketId, e);
            return null;
        }
    }

    private List<TicketCrudo.LineaCruda> leerLineas(Connection con, String ticketId) throws SQLException {
        String sql =
            "SELECT tl.PRODUCT, p.REFERENCE, p.NAME, tl.UNITS, tl.PRICE, tl.TAXID, tx.RATE " +
            "FROM TICKETLINES tl " +
            "LEFT JOIN PRODUCTS p ON p.ID = tl.PRODUCT " +
            "LEFT JOIN TAXES tx ON tx.ID = tl.TAXID " +
            "WHERE tl.TICKET = ? " +
            "ORDER BY tl.LINE";
        List<TicketCrudo.LineaCruda> lineas = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TicketCrudo.LineaCruda l = new TicketCrudo.LineaCruda();
                    l.productoId = rs.getString("PRODUCT");
                    l.referencia = rs.getString("REFERENCE");
                    l.nombre = rs.getString("NAME");
                    l.unidades = rs.getBigDecimal("UNITS");
                    l.precio = rs.getBigDecimal("PRICE");
                    l.taxId = rs.getString("TAXID");
                    BigDecimal tasa = rs.getBigDecimal("RATE");
                    l.tasaImpuesto = tasa != null ? tasa : BigDecimal.ZERO;
                    lineas.add(l);
                }
            }
        }
        return lineas;
    }

    private List<TicketCrudo.PagoCrudo> leerPagos(Connection con, String ticketId) throws SQLException {
        String sql = "SELECT PAYMENT, TOTAL FROM PAYMENTS WHERE RECEIPT = ?";
        List<TicketCrudo.PagoCrudo> pagos = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TicketCrudo.PagoCrudo p = new TicketCrudo.PagoCrudo();
                    p.nombre = rs.getString("PAYMENT");
                    p.total = rs.getBigDecimal("TOTAL");
                    pagos.add(p);
                }
            }
        }
        return pagos;
    }
}
