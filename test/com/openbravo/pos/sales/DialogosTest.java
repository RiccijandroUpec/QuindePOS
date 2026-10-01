package com.openbravo.pos.sales;

import org.junit.Test;

import static org.junit.Assert.*;

public class DialogosTest {

    @Test
    public void leePorcentajes() {
        assertEquals(10.0, Dialogos.leerPorcentaje("10"), 1e-9);
        assertEquals(10.0, Dialogos.leerPorcentaje(" 10% "), 1e-9);
        assertEquals(12.5, Dialogos.leerPorcentaje("12,5"), 1e-9);
        assertEquals(0.0, Dialogos.leerPorcentaje("0"), 1e-9);
        assertNull(Dialogos.leerPorcentaje("150"));
        assertNull(Dialogos.leerPorcentaje("-5"));
        assertNull(Dialogos.leerPorcentaje("diez"));
    }
}
