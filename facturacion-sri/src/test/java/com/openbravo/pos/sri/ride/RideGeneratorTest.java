package com.openbravo.pos.sri.ride;

import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.FormaPago;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import com.openbravo.pos.sri.dominio.Pago;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.xml.ComprobanteXmlMapper;
import com.openbravo.pos.sri.xml.FacturaXmlWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RideGeneratorTest {

    private static String facturaAutorizada(int lineas) {
        return facturaAutorizada(lineas, Ambiente.PRODUCCION);
    }

    private static String facturaAutorizada(int lineas, Ambiente ambiente) {
        DatosEmisor emisor = new DatosEmisor("1790012345001", "COMERCIAL DE PRUEBA CIA. LTDA.", "HELADERIA LA ESQUINA",
                "AV. PRINCIPAL 1-23 Y CALLE B", "AV. PRINCIPAL 1-23 Y CALLE B", null, false, "001", "003",
                ambiente, null, null);
        Cliente cliente = new Cliente("05", "1710034065", "JUAN PEREZ", "CENTRO QUITO", "cliente@correo.ec", null);
        List<DetalleFactura> detalles = new ArrayList<>();
        BigDecimal precio = new BigDecimal("3.75");
        for (int i = 1; i <= lineas; i++) {
            detalles.add(new DetalleFactura(String.valueOf(i), "BANANA SPLIT " + i, BigDecimal.ONE, precio, BigDecimal.ZERO, precio,
                    List.of(ImpuestoDetalle.iva(BigDecimal.ZERO, precio, BigDecimal.ZERO))));
        }
        BigDecimal total = precio.multiply(BigDecimal.valueOf(lineas));
        Comprobante c = new Comprobante("t", TipoComprobante.FACTURA, ambiente, LocalDateTime.of(2026, 8, 19, 15, 55),
                emisor, cliente, detalles, List.of(ImpuestoDetalle.iva(BigDecimal.ZERO, total, BigDecimal.ZERO)),
                List.of(new Pago(FormaPago.SIN_SISTEMA_FINANCIERO, total)), total, BigDecimal.ZERO, total, "000142645");
        c.setClaveAcceso("1908202601179001234500120010030001426450014264512");
        return FacturaXmlWriter.toXml(ComprobanteXmlMapper.map(c));
    }

    private static String texto(byte[] pdf, int[] paginas) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            paginas[0] = doc.getNumberOfPages();
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void incluyeLosCamposDelFormatoDelSri() throws Exception {
        byte[] pdf = RideGenerator.generar(facturaAutorizada(2), LocalDateTime.of(2026, 8, 19, 15, 55, 12));
        int[] paginas = new int[1];
        String t = texto(pdf, paginas);

        assertEquals(1, paginas[0]);
        assertTrue(t.contains("1790012345001"));
        assertTrue(t.contains("001-003-000142645"));
        assertTrue(t.contains("1908202601179001234500120010030001426450014264512"));
        assertTrue(t.contains("19/08/2026 15:55:12"), "fecha y hora de autorizacion");
        assertTrue(t.contains("PRODUCCIÓN"));
        assertTrue(t.contains("JUAN PEREZ"));
        assertTrue(t.contains("SUBTOTAL 15%"));
        assertTrue(t.contains("SUBTOTAL 0%"));
        assertTrue(t.contains("SUBTOTAL NO OBJETO DE IVA"));
        assertTrue(t.contains("SIN UTILIZACIÓN DEL SISTEMA FINANCIERO"));
        assertTrue(t.contains("Información Adicional"));
        assertTrue(t.contains("cliente@correo.ec"));
        assertTrue(t.contains("7,50"), "valores con coma decimal");
        assertTrue(t.contains("Página 1 de 1"));
        assertTrue(t.contains("srienlinea.sri.gob.ec"));
        assertFalse(t.contains("SIN VALOR TRIBUTARIO"), "en produccion no lleva marca de agua");
    }

    @Test
    void enPruebasLlevaMarcaDeAguaSinValorTributario() throws Exception {
        byte[] pdf = RideGenerator.generar(facturaAutorizada(2, Ambiente.PRUEBAS), null);
        String t = texto(pdf, new int[1]);
        assertTrue(t.contains("SIN VALOR TRIBUTARIO"));
        assertTrue(t.contains("AMBIENTE DE PRUEBAS"));
    }

    @Test
    void facturaLargaPasaAOtraPaginaSinPerderLineas() throws Exception {
        byte[] pdf = RideGenerator.generar(facturaAutorizada(60), null);
        int[] paginas = new int[1];
        String t = texto(pdf, paginas);

        assertTrue(paginas[0] >= 2);
        assertTrue(t.contains("BANANA SPLIT 1\n") || t.contains("BANANA SPLIT 1 "));
        assertTrue(t.contains("BANANA SPLIT 60"));
        assertTrue(t.contains("VALOR TOTAL"));
        assertTrue(t.contains("Página 2 de " + paginas[0]));
        assertTrue(t.contains("(continuación)"), "encabezado corto en las paginas siguientes");
    }
}
