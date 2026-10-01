package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.xml.generado.Factura;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Desmarshalla el XML ya AUTORIZADO de una factura (el que el SRI devuelve
 * dentro de {@code <autorizacion><comprobante>}, guardado en
 * {@code ecopos_sri_comprobantes.xml_respuesta_sri}) de vuelta a las mismas
 * clases JAXB generadas del XSD que usa {@link ComprobanteXmlMapper} - el
 * {@code ds:Signature} incrustado se ignora, solo interesan los datos del
 * comprobante. Usado tanto por el RIDE ({@code RideGenerator}) como por la
 * anulacion via Nota de Credito ({@code AnulacionService}, que necesita leer
 * los datos de la factura original para poder referenciarla).
 */
public final class FacturaXmlReader {

    private FacturaXmlReader() {
    }

    public static Factura leer(String xmlAutorizado) throws IOException {
        try {
            JAXBContext contexto = JAXBContext.newInstance(Factura.class);
            Unmarshaller unmarshaller = contexto.createUnmarshaller();
            return (Factura) unmarshaller.unmarshal(new ByteArrayInputStream(xmlAutorizado.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("No se pudo leer el XML autorizado de la factura", e);
        }
    }
}
