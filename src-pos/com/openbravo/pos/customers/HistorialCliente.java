package com.openbravo.pos.customers;

import com.openbravo.data.loader.Session;
import com.openbravo.format.Formats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * Resumen corto de lo que un cliente ya compro ("12 compras \u00B7 $150,00 en
 * total \u00B7 ultima el 20-09-2026"), para verlo al atenderlo: en la pantalla de
 * venta cuando se le asigna la venta y en "Factura con datos" al cobrar.
 */
public final class HistorialCliente {

    private HistorialCliente() {
    }

    /** Resumen de compras del cliente, o null si no se pudo leer. */
    public static String resumen(Session session, String idCliente) {
        if (idCliente == null) {
            return null;
        }
        try {
            Connection con = session.getConnection();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(DISTINCT T.ID), SUM(P.TOTAL), MAX(R.DATENEW) FROM TICKETS T "
                    + "JOIN RECEIPTS R ON R.ID = T.ID JOIN PAYMENTS P ON P.RECEIPT = R.ID "
                    + "WHERE T.CUSTOMER = ? AND T.TICKETTYPE = 0")) {
                ps.setString(1, idCliente);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) == 0) {
                        return "primera compra";
                    }
                    int compras = rs.getInt(1);
                    double total = rs.getDouble(2);
                    Timestamp ultima = rs.getTimestamp(3);
                    return compras + (compras == 1 ? " compra" : " compras") + " \u00B7 "
                            + Formats.CURRENCY.formatValue(total) + " en total"
                            + (ultima == null ? "" : " \u00B7 \u00FAltima el " + Formats.DATE.formatValue(ultima));
                }
            }
        } catch (Exception e) {
            return null;
        }
    }
}
