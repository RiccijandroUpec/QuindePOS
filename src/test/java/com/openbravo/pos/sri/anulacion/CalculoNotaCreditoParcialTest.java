package com.openbravo.pos.sri.anulacion;

import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalculoNotaCreditoParcialTest {

    private static final BigDecimal IVA15 = new BigDecimal("0.15");

    private static DetalleFactura linea(String codigo, String cant, String precio, BigDecimal tarifa) {
        BigDecimal base = new BigDecimal(precio).multiply(new BigDecimal(cant));
        return new DetalleFactura(codigo, "Producto " + codigo, new BigDecimal(cant), new BigDecimal(precio), BigDecimal.ZERO,
                base, List.of(new ImpuestoDetalle("2", tarifa, base, base.multiply(tarifa))));
    }

    @Test
    void devuelveSoloParteDeUnaLineaYOtraCompleta() {
        List<DetalleFactura> factura = List.of(
                linea("A", "3", "2.00", IVA15),       // 3 x $2 con IVA 15%
                linea("B", "1", "5.00", BigDecimal.ZERO)); // 1 x $5 con IVA 0%

        CalculoNotaCreditoParcial.Resultado r = CalculoNotaCreditoParcial.calcular(factura,
                List.of(new BigDecimal("2"), new BigDecimal("1")));

        assertEquals(2, r.detalles.size());
        assertEquals(new BigDecimal("4.00"), r.detalles.get(0).getPrecioTotalSinImpuesto());
        assertEquals(new BigDecimal("9.00"), r.totalSinImpuestos);
        assertEquals(new BigDecimal("9.60"), r.importeTotal); // 4.00 + 0.60 IVA + 5.00
        assertEquals(2, r.totalesPorImpuesto.size());
    }

    @Test
    void omiteLineasEnCeroYRechazaDevolverDeMas() {
        List<DetalleFactura> factura = List.of(linea("A", "3", "2.00", IVA15), linea("B", "1", "5.00", IVA15));
        CalculoNotaCreditoParcial.Resultado r = CalculoNotaCreditoParcial.calcular(factura,
                List.of(BigDecimal.ZERO, BigDecimal.ONE));
        assertEquals(1, r.detalles.size());
        assertEquals(new BigDecimal("5.75"), r.importeTotal);

        assertThrows(IllegalArgumentException.class, () -> CalculoNotaCreditoParcial.calcular(factura,
                List.of(new BigDecimal("4"), BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> CalculoNotaCreditoParcial.calcular(factura,
                List.of(BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test
    void descuentaLoYaAcreditadoEnNotasAnteriores() {
        String ncAnterior = "<notaCredito><detalles><detalle><codigoInterno>A</codigoInterno>"
                + "<descripcion>x</descripcion><cantidad>2.000000</cantidad></detalle></detalles></notaCredito>";
        Map<String, BigDecimal> ya = CalculoNotaCreditoParcial.yaAcreditadoPorCodigo(List.of(ncAnterior));
        List<DetalleFactura> factura = List.of(linea("A", "3", "2.00", IVA15), linea("B", "1", "5.00", IVA15));

        List<BigDecimal> disponible = CalculoNotaCreditoParcial.disponiblePorLinea(factura, ya);

        assertEquals(0, new BigDecimal("1").compareTo(disponible.get(0)));
        assertEquals(0, BigDecimal.ONE.compareTo(disponible.get(1)));
    }
}
