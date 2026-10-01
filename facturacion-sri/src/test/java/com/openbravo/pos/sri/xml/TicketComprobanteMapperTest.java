package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.repository.TicketCrudo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketComprobanteMapperTest {

    private static DatosEmisor emisorDePrueba() {
        return new DatosEmisor(
                "1790012345001", "ALMACENES DE PRUEBA S.A.", "ALMACENES PRUEBA",
                "Av. Amazonas y Naciones Unidas", "Av. Amazonas y Naciones Unidas",
                null, true, "001", "001", Ambiente.PRUEBAS, null, null);
    }

    private static TicketCrudo.LineaCruda linea(String referencia, String nombre,
                                                 BigDecimal unidades, BigDecimal precio, BigDecimal tasaImpuesto) {
        TicketCrudo.LineaCruda l = new TicketCrudo.LineaCruda();
        l.productoId = "prod-uuid-1";
        l.referencia = referencia;
        l.nombre = nombre;
        l.unidades = unidades;
        l.precio = precio;
        l.taxId = "iva15";
        l.tasaImpuesto = tasaImpuesto;
        return l;
    }

    private static TicketCrudo.PagoCrudo pago(String nombre, BigDecimal total) {
        TicketCrudo.PagoCrudo p = new TicketCrudo.PagoCrudo();
        p.nombre = nombre;
        p.total = total;
        return p;
    }

    private static TicketCrudo ticketConsumidorFinal() {
        TicketCrudo t = new TicketCrudo();
        t.ticketId = "ticket-abc";
        t.ticketType = 0;
        t.ticketNumero = 42;
        t.fecha = LocalDateTime.of(2026, 7, 6, 10, 30);
        t.clienteId = null;
        t.clienteTaxId = null;

        List<TicketCrudo.LineaCruda> lineas = new ArrayList<>();
        lineas.add(linea("PROD001", "PRODUCTO A", new BigDecimal("2"), new BigDecimal("5.00"), new BigDecimal("0.15")));
        lineas.add(linea("PROD002", "PRODUCTO B", BigDecimal.ONE, new BigDecimal("3.00"), new BigDecimal("0.00")));
        t.lineas = lineas;

        List<TicketCrudo.PagoCrudo> pagos = new ArrayList<>();
        pagos.add(pago("cash", new BigDecimal("14.50")));
        t.pagos = pagos;

        return t;
    }

    @Test
    void asignaConsumidorFinalCuandoElTicketNoTieneCliente() {
        Comprobante c = TicketComprobanteMapper.map(ticketConsumidorFinal(), emisorDePrueba(), "000000001");

        assertEquals(Cliente.IDENTIFICACION_CONSUMIDOR_FINAL, c.getCliente().getIdentificacion());
        assertEquals("07", c.getCliente().getTipoIdentificacion());
    }

    @Test
    void resuelveClienteConRucPorLongitudDeIdentificacion() {
        TicketCrudo t = ticketConsumidorFinal();
        t.clienteId = "cust-1";
        t.clienteTaxId = "1790012345001"; // 13 digitos -> RUC
        t.clienteNombre = "CLIENTE DE PRUEBA";

        Comprobante c = TicketComprobanteMapper.map(t, emisorDePrueba(), "000000001");

        assertEquals("04", c.getCliente().getTipoIdentificacion());
        assertEquals("CLIENTE DE PRUEBA", c.getCliente().getRazonSocial());
    }

    @Test
    void mapeaDosLineasConDistintaTarifaYSumaTotales() {
        Comprobante c = TicketComprobanteMapper.map(ticketConsumidorFinal(), emisorDePrueba(), "000000001");

        assertEquals(2, c.getDetalles().size());
        // linea 1: 2 x 5.00 = 10.00 sin impuestos, 15% -> 1.50 de IVA
        // linea 2: 1 x 3.00 = 3.00 sin impuestos, 0% -> 0.00 de IVA
        assertEquals(new BigDecimal("13.00"), c.getTotalSinImpuestos());
        assertEquals(2, c.getTotalesPorImpuesto().size(), "debe haber un total por cada codigoPorcentaje distinto (15% y 0%)");
        assertEquals(new BigDecimal("14.50"), c.getImporteTotal());
    }

    @Test
    void generaClaveDeAccesoDe49Digitos() {
        Comprobante c = TicketComprobanteMapper.map(ticketConsumidorFinal(), emisorDePrueba(), "000000001");

        assertNotNull(c.getClaveAcceso());
        assertEquals(49, c.getClaveAcceso().length());
        assertEquals("000000001", c.getSecuencial());
    }

    @Test
    void resuelveFormaDePagoEfectivoComoSinSistemaFinanciero() {
        Comprobante c = TicketComprobanteMapper.map(ticketConsumidorFinal(), emisorDePrueba(), "000000001");

        assertEquals(1, c.getPagos().size());
        assertEquals("01", c.getPagos().get(0).getFormaPago().getCodigo());
    }
}
