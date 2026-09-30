package com.openbravo.pos.promociones;

import com.openbravo.pos.promociones.MotorPromociones.Regla;
import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class MotorPromocionesTest {

    private static Regla nxm(int n, int m) {
        return new Regla("promo", MotorPromociones.TIPO_NXM, "p1", null, n, m, 0, 0, 0, "");
    }

    @Test
    public void dosPorUno() {
        assertEquals(1.00, MotorPromociones.precioConPromocion(1.00, 1, nxm(2, 1)), 1e-9); // 1 unidad: sin promo
        assertEquals(0.50, MotorPromociones.precioConPromocion(1.00, 2, nxm(2, 1)), 1e-9); // paga 1 de 2
        assertEquals(2.0 / 3, MotorPromociones.precioConPromocion(1.00, 3, nxm(2, 1)), 1e-4); // paga 2 de 3
        assertEquals(0.50, MotorPromociones.precioConPromocion(1.00, 4, nxm(2, 1)), 1e-9);
    }

    @Test
    public void tresPorDosYCantidadFraccionaria() {
        assertEquals(2.0 / 3, MotorPromociones.precioConPromocion(1.00, 3, nxm(3, 2)), 1e-4);
        assertEquals(1.00, MotorPromociones.precioConPromocion(1.00, 2.5, nxm(2, 1)), 1e-9); // balanza: no aplica
    }

    @Test
    public void porcentaje() {
        Regla diez = new Regla("10%", MotorPromociones.TIPO_PORCENTAJE, null, "cat", 0, 0, 10, 0, 0, "");
        assertEquals(2.70, MotorPromociones.precioConPromocion(3.00, 1, diez), 1e-9);
        assertEquals(3.00, MotorPromociones.precioConPromocion(3.00, 1, null), 1e-9);
    }

    @Test
    public void horarioYDias() {
        Regla happyHour = new Regla("HH", MotorPromociones.TIPO_PORCENTAJE, "p1", null, 0, 0, 20, 17, 19, "5");
        Calendar viernes18 = Calendar.getInstance();
        viernes18.set(2026, Calendar.OCTOBER, 2, 18, 0); // 2-oct-2026 es viernes
        Calendar viernes20 = (Calendar) viernes18.clone();
        viernes20.set(Calendar.HOUR_OF_DAY, 20);
        Calendar sabado18 = (Calendar) viernes18.clone();
        sabado18.add(Calendar.DAY_OF_MONTH, 1);
        assertEquals(true, happyHour.vigente(viernes18));
        assertEquals(false, happyHour.vigente(viernes20));
        assertEquals(false, happyHour.vigente(sabado18));
    }
}
