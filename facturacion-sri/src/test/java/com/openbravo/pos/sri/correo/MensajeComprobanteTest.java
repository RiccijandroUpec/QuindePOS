package com.openbravo.pos.sri.correo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MensajeComprobanteTest {

    private static final String XML = "<factura><infoTributaria><razonSocial>COMERCIAL DE PRUEBA CIA. LTDA.</razonSocial>"
            + "<nombreComercial>HELADERIA &amp; CIA</nombreComercial><ruc>1790012345001</ruc>"
            + "<claveAcceso>1908202601179001234500120010030001426450014264512</claveAcceso>"
            + "<estab>001</estab><ptoEmi>003</ptoEmi><secuencial>000142645</secuencial></infoTributaria>"
            + "<infoFactura><fechaEmision>19/08/2026</fechaEmision><razonSocialComprador>JUAN PEREZ</razonSocialComprador>"
            + "<importeTotal>7.50</importeTotal></infoFactura></factura>";

    @Test
    void armaAsuntoCuerpoYArchivosConLosDatosDelXml() {
        MensajeComprobante m = MensajeComprobante.armar(false, XML);

        assertEquals("Factura 001-003-000142645 - HELADERIA & CIA", m.asunto);
        assertEquals("factura-001-003-000142645.pdf", m.archivoPdf);
        assertEquals("factura-001-003-000142645.xml", m.archivoXml);
        assertTrue(m.cuerpo.startsWith("Estimado(a) JUAN PEREZ:"));
        assertTrue(m.cuerpo.contains("RUC 1790012345001"));
        assertTrue(m.cuerpo.contains("Valor total: $7.50"));
        assertTrue(m.cuerpo.contains("1908202601179001234500120010030001426450014264512"));
    }

    @Test
    void notaDeCreditoUsaSuPropioNombreYValor() {
        String nc = XML.replace("<importeTotal>7.50</importeTotal>", "<valorModificacion>3.75</valorModificacion>");
        MensajeComprobante m = MensajeComprobante.armar(true, nc);

        assertTrue(m.asunto.startsWith("Nota de crédito 001-003-000142645"));
        assertEquals("nota-credito-001-003-000142645.pdf", m.archivoPdf);
        assertTrue(m.cuerpo.contains("Valor acreditado: $3.75"));
    }
}
