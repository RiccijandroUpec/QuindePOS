package com.openbravo.pos.sri.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lectura cruda (sin interpretar) de un ticket de ECOPos: TICKETS +
 * RECEIPTS + TICKETLINES + PAYMENTS + CUSTOMERS, tal como estan en las
 * tablas originales. La conversion a {@code Comprobante} (dominio SRI)
 * vive en com.openbravo.pos.sri.xml, para mantener esta clase como un
 * espejo fiel del esquema de ECOPos y no mezclar las dos capas.
 */
public class TicketCrudo {

    public String ticketId;         // TICKETS.ID = RECEIPTS.ID
    public int ticketType;          // TICKETS.TICKETTYPE (0 = normal, ver TicketInfo.RECEIPT_NORMAL en ECOPos)
    public int ticketNumero;        // TICKETS.TICKETID (numero de recibo correlativo interno de ECOPos)
    public LocalDateTime fecha;      // RECEIPTS.DATENEW
    public String clienteId;        // TICKETS.CUSTOMER (puede ser null = consumidor final)
    public String clienteTaxId;
    public String clienteNombre;
    public String clienteDireccion;
    public String clienteEmail;
    public String clienteTelefono;
    public List<LineaCruda> lineas;
    public List<PagoCrudo> pagos;

    public static class LineaCruda {
        public String productoId;
        public String referencia;   // PRODUCTS.REFERENCE - candidato a codigoPrincipal
        public String nombre;       // PRODUCTS.NAME - candidato a descripcion
        public BigDecimal unidades;  // TICKETLINES.UNITS
        public BigDecimal precio;   // TICKETLINES.PRICE (unitario, segun convencion de ECOPos)
        public String taxId;        // TICKETLINES.TAXID -> TAXES.ID
        public BigDecimal tasaImpuesto; // TAXES.RATE (0.15, 0.0, etc.)
    }

    public static class PagoCrudo {
        public String nombre;       // PAYMENTS.PAYMENT (ej "cash", "magcard", "cheque")
        public BigDecimal total;    // PAYMENTS.TOTAL
    }
}
