package com.openbravo.pos.sales;

import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.DataLogicSales;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Control de stock al agregar o cambiar una linea de la venta (scripts script.StockCurrentAdd
 * y script.StockCurrentSet). Usa la conexion de la app (antes cada producto abria una conexion
 * nueva que nunca se cerraba) y compara los productos por su id, no por referencia.
 */
public final class ControlStock {

    private static final Logger LOG = Logger.getLogger(ControlStock.class.getName());

    private ControlStock() {
    }

    /**
     * @param indiceReemplazado la linea que se esta cambiando (no se cuenta dos veces), o -1 al agregar
     * @return null si hay stock (o es un servicio), "Cancel" si no alcanza (y avisa al cajero)
     */
    public static String revisar(AppView app, TicketInfo ticket, TicketLineInfo linea, int indiceReemplazado) {
        if (linea == null || linea.getProductID() == null || linea.isProductService()) {
            return null; // lineas sin producto y servicios no llevan control de stock
        }
        try {
            DataLogicSales dls = (DataLogicSales) app.getBean("com.openbravo.pos.forms.DataLogicSales");
            String almacen = app.getInventoryLocation();
            double disponible = dls.findProductStock(almacen, linea.getProductID(), linea.getProductAttSetInstId());
            double enVenta = 0;
            for (int i = 0; i < ticket.getLinesCount(); i++) {
                if (i == indiceReemplazado) {
                    continue;
                }
                TicketLineInfo otra = ticket.getLine(i);
                if (linea.getProductID().equals(otra.getProductID())) {
                    enVenta += otra.getMultiply();
                }
            }
            double queda = disponible - enVenta - linea.getMultiply();
            if (queda < 0) {
                Dialogos.advertencia("Stock",
                        "No hay stock suficiente de \"" + linea.getProductName() + "\".\n"
                        + "Disponible: " + formato(disponible) + "  \u00B7  En esta venta: " + formato(enVenta + linea.getMultiply())
                        + "\n\nRegistra la entrada en Inventario.");
                return "Cancel";
            }
            return null;
        } catch (Exception e) {
            // Si no se puede consultar el stock, no se bloquea la venta.
            LOG.log(Level.WARNING, "No se pudo revisar el stock de " + linea.getProductID(), e);
            return null;
        }
    }

    private static String formato(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
