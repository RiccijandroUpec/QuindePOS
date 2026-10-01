package com.openbravo.pos.sales;

import com.openbravo.pos.promociones.MotorPromociones;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;

/**
 * Descuento manual en % para una linea o para toda la venta (botones de descuento,
 * scripts script.linediscount y script.totaldiscount). El descuento queda guardado en la
 * linea y se calcula siempre sobre su precio de lista, asi:
 * <ul>
 * <li>no se acumula si se aplica dos veces (el segundo reemplaza al primero),</li>
 * <li>convive con las promociones (se aplica encima de la promocion),</li>
 * <li>0% lo quita y la linea vuelve a su precio,</li>
 * <li>no cambia el nombre del producto (que es el que va en la factura del SRI)
 * ni pierde las notas ni demas datos de la linea.</li>
 * </ul>
 */
public final class DescuentoManual {

    private DescuentoManual() {
    }

    /** Aplica (o quita, con 0) el descuento a una linea. False si la linea no admite descuento. */
    public static boolean aplicarALinea(TicketLineInfo linea, double porcentaje) {
        if (linea == null || linea.getPrice() <= 0 && linea.getProperty(MotorPromociones.PRECIO_LISTA) == null) {
            return false;
        }
        double pct = Math.max(0, Math.min(100, porcentaje));
        String lista = linea.getProperty(MotorPromociones.PRECIO_LISTA);
        double precioLista = lista == null ? linea.getPrice() : Double.parseDouble(lista);
        if (lista == null) {
            linea.setProperty(MotorPromociones.PRECIO_LISTA, Double.toString(precioLista));
        }
        if (pct == 0) {
            linea.getProperties().remove(MotorPromociones.DESCUENTO_MANUAL);
        } else {
            linea.setProperty(MotorPromociones.DESCUENTO_MANUAL, Double.toString(pct));
        }
        // Precio provisional; el motor de promociones lo recalcula (promocion + descuento) al refrescar.
        linea.setPrice(Math.round(precioLista * (1 - pct / 100.0) * 10000.0) / 10000.0);
        return true;
    }

    /** Aplica el descuento a todas las lineas con precio. Devuelve cuantas lineas cambio. */
    public static int aplicarATodo(TicketInfo ticket, double porcentaje) {
        int n = 0;
        for (int i = 0; i < ticket.getLinesCount(); i++) {
            if (aplicarALinea(ticket.getLine(i), porcentaje)) {
                n++;
            }
        }
        return n;
    }
}
