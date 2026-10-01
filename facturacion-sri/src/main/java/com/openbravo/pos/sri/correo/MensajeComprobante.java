package com.openbravo.pos.sri.correo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asunto, texto y nombres de archivo del correo con el que se envia un
 * comprobante autorizado al cliente. Los datos salen del mismo XML
 * autorizado que va adjunto, asi el correo siempre coincide con el PDF.
 */
public final class MensajeComprobante {

    public final String asunto;
    public final String cuerpo;
    public final String archivoXml;
    public final String archivoPdf;

    private MensajeComprobante(String asunto, String cuerpo, String archivoXml, String archivoPdf) {
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.archivoXml = archivoXml;
        this.archivoPdf = archivoPdf;
    }

    /**
     * @param notaCredito true para una nota de credito, false para una factura
     * @param xml         XML autorizado del comprobante
     */
    public static MensajeComprobante armar(boolean notaCredito, String xml) {
        String tipo = notaCredito ? "Nota de crédito" : "Factura";
        String numero = campo(xml, "estab") + "-" + campo(xml, "ptoEmi") + "-" + campo(xml, "secuencial");
        String emisor = campo(xml, "nombreComercial").isEmpty() ? campo(xml, "razonSocial") : campo(xml, "nombreComercial");
        String cliente = campo(xml, "razonSocialComprador");
        String total = notaCredito ? campo(xml, "valorModificacion") : campo(xml, "importeTotal");

        StringBuilder c = new StringBuilder();
        c.append("Estimado(a) ").append(cliente.isEmpty() ? "cliente" : cliente).append(":\n\n");
        c.append("Adjuntamos su ").append(tipo.toLowerCase()).append(" electrónica No. ").append(numero)
                .append(", autorizada por el SRI.\n\n");
        c.append("Emisor: ").append(campo(xml, "razonSocial")).append(" - RUC ").append(campo(xml, "ruc")).append('\n');
        c.append("Fecha de emisión: ").append(campo(xml, "fechaEmision")).append('\n');
        if (!total.isEmpty()) {
            c.append(notaCredito ? "Valor acreditado: $" : "Valor total: $").append(total).append('\n');
        }
        c.append("Número de autorización: ").append(campo(xml, "claveAcceso")).append("\n\n");
        c.append("Encontrará adjunta la representación impresa (PDF) y el archivo XML, que es el comprobante válido.\n");
        c.append("También puede consultarla en https://srienlinea.sri.gob.ec\n\n");
        c.append("Gracias por su compra.\n").append(emisor).append('\n');

        String base = (notaCredito ? "nota-credito-" : "factura-") + numero;
        String asunto = tipo + " " + numero + (emisor.isEmpty() ? "" : " - " + emisor);
        return new MensajeComprobante(asunto, c.toString(), base + ".xml", base + ".pdf");
    }

    private static String campo(String xml, String etiqueta) {
        if (xml == null) {
            return "";
        }
        Matcher m = Pattern.compile("<" + etiqueta + ">([^<]*)</" + etiqueta + ">").matcher(xml);
        return m.find() ? desescapar(m.group(1).trim()) : "";
    }

    private static String desescapar(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
