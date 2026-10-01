package com.openbravo.pos.sri.correo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asunto, texto y nombres de archivo del correo con el que se envia un
 * comprobante autorizado al cliente. Los datos salen del mismo XML
 * autorizado que va adjunto, asi el correo siempre coincide con el PDF.
 */
public final class MensajeComprobante {

    /** Identificador del logo incrustado en el HTML ("cid:" + LOGO_CID). */
    public static final String LOGO_CID = "logo-negocio";

    public final String asunto;
    /** Texto plano: para los correos que no muestran HTML. */
    public final String cuerpo;
    /** El mismo mensaje con diseño (HTML con estilos en linea, como exigen los clientes de correo). */
    public final String html;
    public final String archivoXml;
    public final String archivoPdf;

    private MensajeComprobante(String asunto, String cuerpo, String html, String archivoXml, String archivoPdf) {
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.html = html;
        this.archivoXml = archivoXml;
        this.archivoPdf = archivoPdf;
    }

    /**
     * @param notaCredito true para una nota de credito, false para una factura
     * @param xml         XML autorizado del comprobante
     */
    public static MensajeComprobante armar(boolean notaCredito, String xml) {
        return armar(notaCredito, xml, false);
    }

    /**
     * @param conLogo true si el correo lleva el logo del negocio incrustado (cid:{@value #LOGO_CID})
     */
    public static MensajeComprobante armar(boolean notaCredito, String xml, boolean conLogo) {
        String tipo = notaCredito ? "Nota de crédito" : "Factura";
        String numero = campo(xml, "estab") + "-" + campo(xml, "ptoEmi") + "-" + campo(xml, "secuencial");
        String emisor = campo(xml, "nombreComercial").isEmpty() ? campo(xml, "razonSocial") : campo(xml, "nombreComercial");
        String cliente = campo(xml, "razonSocialComprador");
        // Misma coma decimal que el PDF.
        String total = (notaCredito ? campo(xml, "valorModificacion") : campo(xml, "importeTotal")).replace('.', ',');

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
        String html = html(notaCredito, tipo, numero, emisor, cliente, total, xml, conLogo);
        return new MensajeComprobante(asunto, c.toString(), html, base + ".xml", base + ".pdf");
    }

    private static String html(boolean notaCredito, String tipo, String numero, String emisor, String cliente,
                               String total, String xml, boolean conLogo) {
        String selva = "#1B5E3F";
        String tinta = "#10231A";
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>")
         .append("<body style=\"margin:0;padding:0;background:#F2F4F3;font-family:Arial,Helvetica,sans-serif;color:").append(tinta).append(";\">")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F2F4F3;padding:24px 12px;\"><tr><td align=\"center\">")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;background:#FFFFFF;border-radius:12px;overflow:hidden;border:1px solid #D9DDE3;\">");
        // Encabezado: logo (si hay) y nombre del negocio.
        h.append("<tr><td style=\"background:").append(selva).append(";padding:20px 24px;color:#FFFFFF;\">");
        if (conLogo) {
            h.append("<img src=\"cid:").append(LOGO_CID).append("\" alt=\"\" style=\"max-height:48px;max-width:200px;display:block;margin-bottom:10px;background:#FFFFFF;border-radius:6px;padding:4px;\">");
        }
        h.append("<div style=\"font-size:20px;font-weight:bold;\">").append(esc(emisor)).append("</div>")
         .append("<div style=\"font-size:13px;opacity:0.85;\">RUC ").append(esc(campo(xml, "ruc"))).append("</div></td></tr>");
        // Cuerpo.
        h.append("<tr><td style=\"padding:24px;\">")
         .append("<p style=\"margin:0 0 12px;font-size:15px;\">Estimado(a) ").append(esc(cliente.isEmpty() ? "cliente" : cliente)).append(":</p>")
         .append("<p style=\"margin:0 0 20px;font-size:15px;line-height:1.5;\">Adjuntamos su <b>").append(esc(tipo.toLowerCase()))
         .append(" electr&oacute;nica</b>, autorizada por el SRI.</p>")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F5FBF7;border:1px solid #D8F3E3;border-radius:8px;\">");
        fila(h, tipo + " No.", numero, true);
        fila(h, "Fecha de emisi&oacute;n", esc(campo(xml, "fechaEmision")), false);
        if (!total.isEmpty()) {
            fila(h, notaCredito ? "Valor acreditado" : "Valor total", "$" + esc(total), true);
        }
        h.append("<tr><td colspan=\"2\" style=\"padding:10px 16px 14px;font-size:12px;color:#55605A;\">N&uacute;mero de autorizaci&oacute;n<br>")
         .append("<span style=\"font-family:Consolas,Menlo,monospace;font-size:12px;color:").append(tinta).append(";word-break:break-all;\">")
         .append(esc(campo(xml, "claveAcceso"))).append("</span></td></tr></table>");
        h.append("<p style=\"margin:20px 0 8px;font-size:14px;line-height:1.5;\">Encontrar&aacute; adjunta la representaci&oacute;n impresa (PDF) y el archivo XML, que es el comprobante v&aacute;lido.</p>")
         .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:16px 0 4px;\"><tr><td style=\"background:").append(selva)
         .append(";border-radius:8px;\"><a href=\"https://srienlinea.sri.gob.ec\" style=\"display:inline-block;padding:12px 20px;color:#FFFFFF;text-decoration:none;font-weight:bold;font-size:14px;\">Consultar en el SRI</a></td></tr></table>")
         .append("<p style=\"margin:20px 0 0;font-size:15px;\">Gracias por su compra.</p></td></tr>");
        // Pie.
        h.append("<tr><td style=\"padding:14px 24px;border-top:1px solid #E6E9E7;font-size:11px;color:#7A857F;\">")
         .append("Este correo fue enviado autom&aacute;ticamente por ").append(esc(emisor)).append(". Enviado con Quinde POS.</td></tr>")
         .append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    private static void fila(StringBuilder h, String etiqueta, String valor, boolean destacado) {
        h.append("<tr><td style=\"padding:10px 16px 0;font-size:13px;color:#55605A;\">").append(etiqueta).append("</td>")
         .append("<td align=\"right\" style=\"padding:10px 16px 0;font-size:").append(destacado ? "16px;font-weight:bold" : "14px")
         .append(";\">").append(valor).append("</td></tr>");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
