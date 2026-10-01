package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.xml.generado.notacredito.NotaCredito;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

/** Espejo de {@link FacturaXmlWriter} para {@link NotaCredito}. */
public final class NotaCreditoXmlWriter {

    private static final String ENCODING = "UTF-8";

    private NotaCreditoXmlWriter() {
    }

    public static String toXml(NotaCredito notaCredito) {
        try {
            JAXBContext contexto = JAXBContext.newInstance(NotaCredito.class);
            Marshaller marshaller = contexto.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, ENCODING);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);

            StringWriter writer = new StringWriter();
            marshaller.marshal(notaCredito, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo generar el XML de la nota de credito", e);
        }
    }
}
