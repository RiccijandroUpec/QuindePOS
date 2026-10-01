package com.openbravo.pos.sri.correo;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class NotificadorCorreoTest {

    private static final String XML = "<factura><infoTributaria><razonSocial>COMERCIAL DE PRUEBA CIA. LTDA.</razonSocial>"
            + "<nombreComercial>HELADERIA &amp; CIA</nombreComercial><ruc>1790012345001</ruc>"
            + "<claveAcceso>1908202601179001234500120010030001426451234567813</claveAcceso>"
            + "<estab>001</estab><ptoEmi>003</ptoEmi><secuencial>000142645</secuencial></infoTributaria>"
            + "<infoFactura><fechaEmision>19/08/2026</fechaEmision><razonSocialComprador>JUAN PEREZ</razonSocialComprador>"
            + "<importeTotal>7.50</importeTotal></infoFactura></factura>";

    @Test
    void correoConDisenoLogoYAdjuntos() throws Exception {
        MensajeComprobante m = MensajeComprobante.armar(false, XML, true);
        MimeMessage correo = NotificadorCorreo.armar(Session.getInstance(new Properties()), "ventas@negocio.ec",
                "cliente@correo.ec", m.asunto, m.cuerpo, m.html, new byte[]{1, 2, 3},
                m.archivoXml, "<xml/>".getBytes(), m.archivoPdf, new byte[]{4});

        Multipart mixto = (Multipart) correo.getContent();
        assertEquals(3, mixto.getCount(), "cuerpo + XML + PDF");
        assertEquals("factura-001-003-000142645.xml", mixto.getBodyPart(1).getFileName());
        assertEquals("factura-001-003-000142645.pdf", mixto.getBodyPart(2).getFileName());

        Multipart alternativas = (Multipart) mixto.getBodyPart(0).getContent();
        assertTrue(alternativas.getContentType().startsWith("multipart/alternative"));
        assertTrue(alternativas.getBodyPart(0).isMimeType("text/plain"));
        Multipart relacionado = (Multipart) alternativas.getBodyPart(1).getContent();
        assertTrue(relacionado.getContentType().startsWith("multipart/related"));
        BodyPart html = relacionado.getBodyPart(0);
        assertTrue(html.isMimeType("text/html"));
        String contenido = (String) html.getContent();
        assertTrue(contenido.contains("cid:" + MensajeComprobante.LOGO_CID));
        assertTrue(contenido.contains("HELADERIA &amp; CIA"), "el & del nombre va escapado en el HTML");
        assertTrue(contenido.contains("001-003-000142645"));
        assertTrue(contenido.contains("https://srienlinea.sri.gob.ec"));
        assertEquals("<" + MensajeComprobante.LOGO_CID + ">", ((jakarta.mail.internet.MimeBodyPart) relacionado.getBodyPart(1)).getContentID());
    }

    @Test
    void sinLogoNoReferenciaImagen() throws Exception {
        MensajeComprobante m = MensajeComprobante.armar(false, XML, false);
        assertFalse(m.html.contains("cid:"));
        MimeMessage correo = NotificadorCorreo.armar(Session.getInstance(new Properties()), "ventas@negocio.ec",
                "cliente@correo.ec", m.asunto, m.cuerpo, m.html, null, m.archivoXml, "<xml/>".getBytes(), m.archivoPdf, new byte[]{4});
        Multipart alternativas = (Multipart) ((Multipart) correo.getContent()).getBodyPart(0).getContent();
        assertTrue(alternativas.getBodyPart(1).isMimeType("text/html"));
    }
}
