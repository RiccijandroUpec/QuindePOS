package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.xml.generado.Factura;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

/**
 * Serializa un {@link Factura} (ya poblado por {@link ComprobanteXmlMapper})
 * al XML que el SRI espera. Este XML es el que luego firma el modulo de
 * XAdES-BES - {@link Factura#getSignature()} se deja sin usar aqui a
 * proposito, la firma se inserta como paso posterior sobre el XML ya
 * generado.
 */
public final class FacturaXmlWriter {

    private static final String ENCODING = "UTF-8";

    private FacturaXmlWriter() {
    }

    public static String toXml(Factura factura) {
        try {
            JAXBContext contexto = JAXBContext.newInstance(Factura.class);
            Marshaller marshaller = contexto.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, ENCODING);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);

            StringWriter writer = new StringWriter();
            marshaller.marshal(factura, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo generar el XML de la factura", e);
        }
    }
}
