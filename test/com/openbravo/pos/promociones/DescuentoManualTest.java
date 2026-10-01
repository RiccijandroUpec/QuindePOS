package com.openbravo.pos.promociones;

import com.openbravo.pos.promociones.MotorPromociones.Regla;
import com.openbravo.pos.sales.DescuentoManual;
import com.openbravo.pos.ticket.TaxInfo;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** Descuento manual (botones de descuento) junto con el motor de promociones. */
public class DescuentoManualTest {

    private static final TaxInfo IVA0 = new TaxInfo("iva0", "IVA 0%", "cat0", null, null, 0.0, false, 0);

    private static TicketInfo venta(String producto, double cantidad, double precio) {
        TicketInfo t = new TicketInfo();
        t.addLine(new TicketLineInfo(producto, "Producto " + producto, "cat0", cantidad, precio, IVA0));
        return t;
    }

    @Test
    public void noSeAcumulaYCeroLoQuita() {
        TicketInfo t = venta("p1", 1, 10.00);
        MotorPromociones motor = new MotorPromociones(Collections.<Regla>emptyList());
        TicketLineInfo l = t.getLine(0);

        DescuentoManual.aplicarALinea(l, 10);
        motor.aplicar(t);
        assertEquals(9.00, l.getPrice(), 1e-9);

        DescuentoManual.aplicarALinea(l, 20); // reemplaza al 10%, no queda en 7,20
        motor.aplicar(t);
        assertEquals(8.00, l.getPrice(), 1e-9);

        DescuentoManual.aplicarALinea(l, 0);
        motor.aplicar(t);
        assertEquals(10.00, l.getPrice(), 1e-9);
        assertNull(l.getProperty(MotorPromociones.DESCUENTO_MANUAL));
        assertEquals("Producto p1", l.getProductName()); // el nombre no cambia (va en la factura)
    }

    @Test
    public void seAplicaEncimaDeLaPromocionYSobreviveAlRecalculo() {
        Regla dosPorUno = new Regla("2x1", MotorPromociones.TIPO_NXM, "p1", null, 2, 1, 0, 0, 0, "");
        MotorPromociones motor = new MotorPromociones(Arrays.asList(dosPorUno));
        TicketInfo t = venta("p1", 2, 1.00);

        motor.aplicar(t);
        assertEquals(0.50, t.getLine(0).getPrice(), 1e-9); // 2x1

        DescuentoManual.aplicarATodo(t, 10);
        motor.aplicar(t);
        motor.aplicar(t); // recalcular de nuevo no lo pierde ni lo duplica
        assertEquals(0.45, t.getLine(0).getPrice(), 1e-9); // 2x1 y luego 10%
    }
}
