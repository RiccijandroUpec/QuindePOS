package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.FormaPago;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import com.openbravo.pos.sri.dominio.Pago;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.xml.generado.Factura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComprobanteXmlMapperTest {

    private static Comprobante comprobanteDeEjemplo() {
        return comprobanteDeEjemplo(Cliente.consumidorFinal());
    }

    private static Comprobante comprobanteDeEjemplo(Cliente cliente) {
        DatosEmisor emisor = new DatosEmisor(
                "1790012345001", "ALMACENES DE PRUEBA S.A.", "ALMACENES PRUEBA",
                "Av. Amazonas y Naciones Unidas", "Av. Amazonas y Naciones Unidas",
                null, true, "001", "001", Ambiente.PRUEBAS, null, null);

        BigDecimal precioUnitario = new BigDecimal("10.00");
        BigDecimal baseImponible = new BigDecimal("10.00");
        BigDecimal valorIva = new BigDecimal("1.50");

        DetalleFactura detalle = new DetalleFactura(
                "PROD001", "PRODUCTO DE PRUEBA", BigDecimal.ONE, precioUnitario,
                BigDecimal.ZERO, baseImponible,
                List.of(ImpuestoDetalle.iva(new BigDecimal("0.15"), baseImponible, valorIva)));

        List<ImpuestoDetalle> totalesPorImpuesto = List.of(
                ImpuestoDetalle.iva(new BigDecimal("0.15"), baseImponible, valorIva));

        List<Pago> pagos = List.of(new Pago(FormaPago.SIN_SISTEMA_FINANCIERO, new BigDecimal("11.50")));

        Comprobante comprobante = new Comprobante(
                "ticket-123", TipoComprobante.FACTURA, Ambiente.PRUEBAS,
                LocalDateTime.of(2026, 7, 6, 10, 30), emisor, cliente,
                Collections.singletonList(detalle), totalesPorImpuesto, pagos,
                baseImponible, BigDecimal.ZERO, new BigDecimal("11.50"), "000000001");

        comprobante.setClaveAcceso("0607202601179001234500110010010000000011234567811");
        return comprobante;
    }

    @Test
    void mapeaCamposDeInfoTributaria() {
        Comprobante comprobante = comprobanteDeEjemplo();

        Factura factura = ComprobanteXmlMapper.map(comprobante);

        assertEquals("comprobante", factura.getId());
        assertEquals("2.1.0", factura.getVersion());
        assertEquals("1", factura.getInfoTributaria().getAmbiente());
        assertEquals("1", factura.getInfoTributaria().getTipoEmision());
        assertEquals("1790012345001", factura.getInfoTributaria().getRuc());
        assertEquals("01", factura.getInfoTributaria().getCodDoc());
        assertEquals("000000001", factura.getInfoTributaria().getSecuencial());
        assertEquals(comprobante.getClaveAcceso(), factura.getInfoTributaria().getClaveAcceso());
    }

    @Test
    void mapeaClienteConsumidorFinal() {
        Factura factura = ComprobanteXmlMapper.map(comprobanteDeEjemplo());

        assertEquals("07", factura.getInfoFactura().getTipoIdentificacionComprador());
        assertEquals(Cliente.IDENTIFICACION_CONSUMIDOR_FINAL, factura.getInfoFactura().getIdentificacionComprador());
        assertEquals("CONSUMIDOR FINAL", factura.getInfoFactura().getRazonSocialComprador());
    }

    @Test
    void mapeaTotalesConDosDecimales() {
        Factura factura = ComprobanteXmlMapper.map(comprobanteDeEjemplo());

        assertEquals(new BigDecimal("10.00"), factura.getInfoFactura().getTotalSinImpuestos());
        assertEquals(new BigDecimal("11.50"), factura.getInfoFactura().getImporteTotal());
        assertEquals(1, factura.getInfoFactura().getTotalConImpuestos().getTotalImpuesto().size());
        assertEquals("4", factura.getInfoFactura().getTotalConImpuestos().getTotalImpuesto().get(0).getCodigoPorcentaje());
    }

    @Test
    void mapeaUnaLineaDeDetalleConSuImpuesto() {
        Factura factura = ComprobanteXmlMapper.map(comprobanteDeEjemplo());

        assertEquals(1, factura.getDetalles().getDetalle().size());
        Factura.Detalles.Detalle detalle = factura.getDetalles().getDetalle().get(0);
        assertEquals("PROD001", detalle.getCodigoPrincipal());
        assertEquals("PRODUCTO DE PRUEBA", detalle.getDescripcion());
        assertEquals(1, detalle.getImpuestos().getImpuesto().size());
        assertEquals("4", detalle.getImpuestos().getImpuesto().get(0).getCodigoPorcentaje());
        assertEquals(new BigDecimal("15.00"), detalle.getImpuestos().getImpuesto().get(0).getTarifa());
    }

    @Test
    void rechazaComprobanteSinClaveAcceso() {
        Comprobante sinClave = comprobanteDeEjemplo();
        // Se crea uno nuevo identico pero sin setClaveAcceso, reutilizando el
        // mismo constructor no es trivial aqui, asi que se verifica sobre un
        // Comprobante recien construido (claveAcceso null por defecto).
        DatosEmisor emisor = new DatosEmisor(
                "1790012345001", "ALMACENES DE PRUEBA S.A.", "ALMACENES PRUEBA",
                "Av. Amazonas y Naciones Unidas", "Av. Amazonas y Naciones Unidas",
                null, true, "001", "001", Ambiente.PRUEBAS, null, null);
        Comprobante nuevo = new Comprobante(
                "ticket-456", TipoComprobante.FACTURA, Ambiente.PRUEBAS,
                LocalDateTime.now(), emisor, Cliente.consumidorFinal(),
                sinClave.getDetalles(), sinClave.getTotalesPorImpuesto(), sinClave.getPagos(),
                sinClave.getTotalSinImpuestos(), sinClave.getTotalDescuento(), sinClave.getImporteTotal(),
                "000000002");

        assertThrows(IllegalStateException.class, () -> ComprobanteXmlMapper.map(nuevo));
    }

    @Test
    void generaXmlBienFormadoConLosDatosClave() {
        Comprobante comprobante = comprobanteDeEjemplo();
        Factura factura = ComprobanteXmlMapper.map(comprobante);

        String xml = FacturaXmlWriter.toXml(factura);

        assertTrue(xml.contains("<factura"));
        assertTrue(xml.contains(comprobante.getClaveAcceso()));
        assertTrue(xml.contains("<razonSocial>ALMACENES DE PRUEBA S.A.</razonSocial>"));
        assertTrue(xml.contains("<codDoc>01</codDoc>"));
        assertTrue(xml.contains("PRODUCTO DE PRUEBA"));
    }

    @Test
    void consumidorFinalSinDatosDeContactoNoLlevaInfoAdicional() {
        Factura factura = ComprobanteXmlMapper.map(comprobanteDeEjemplo());
        assertNull(factura.getInfoAdicional());
    }

    @Test
    void datosDeContactoDelClienteVanEnInfoAdicional() throws Exception {
        Cliente cliente = new Cliente("05", "1710034065", "MARIA PEREZ", "Centro Ibarra", "maria@correo.ec", " ");
        Factura factura = ComprobanteXmlMapper.map(comprobanteDeEjemplo(cliente));

        var campos = factura.getInfoAdicional().getCampoAdicional();
        assertEquals(2, campos.size(), "el telefono vacio no se incluye");
        assertEquals("Dirección", campos.get(0).getNombre());
        assertEquals("Centro Ibarra", campos.get(0).getValue());
        assertEquals("Email", campos.get(1).getNombre());
        assertEquals("maria@correo.ec", campos.get(1).getValue());

        // El XML resultante sigue cumpliendo el XSD oficial del SRI (factura 2.1.0).
        String xml = FacturaXmlWriter.toXml(factura);
        javax.xml.validation.SchemaFactory fabrica =
                javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        javax.xml.validation.Schema esquema = fabrica.newSchema(getClass().getResource("/xsd/factura_V2.1.0.xsd"));
        esquema.newValidator().validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml)));
    }
}
