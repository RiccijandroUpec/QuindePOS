package com.openbravo.pos.ticket;

import com.openbravo.pos.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Datos de la factura electronica de una venta para imprimirlos en el ticket
 * ($factura en las plantillas). Vienen del conector SRI: numero, clave de
 * acceso (que tambien es el numero de autorizacion), ambiente, comprador y
 * forma de pago con el texto del SRI. Los textos ya vienen listos para el XML del ticket.
 */
public final class FacturaTicket {

    private final Map<String, String> datos;

    private FacturaTicket(Map<String, String> datos) {
        this.datos = datos;
    }

    /** null si no hay datos (sin factura, o el conector no respondio a tiempo). */
    public static FacturaTicket de(Map<String, String> datos) {
        return datos == null || datos.get("claveAcceso") == null ? null : new FacturaTicket(datos);
    }

    private String valor(String clave) {
        String v = datos.get(clave);
        return v == null ? "" : StringUtils.encodeXML(v.trim());
    }

    public String getNumero() {
        return valor("numero");
    }

    public String getClaveAcceso() {
        return valor("claveAcceso");
    }

    /** La clave de acceso (49 digitos) no cabe en una linea de 42: primera mitad. */
    public String getClaveLinea1() {
        String c = valor("claveAcceso");
        return c.length() > 25 ? c.substring(0, 25) : c;
    }

    /** Segunda mitad de la clave de acceso. */
    public String getClaveLinea2() {
        String c = valor("claveAcceso");
        return c.length() > 25 ? c.substring(25) : "";
    }

    public String getAmbiente() {
        return valor("ambiente");
    }

    public String getEmision() {
        return valor("emision");
    }

    public String getFechaEmision() {
        return valor("fechaEmision");
    }

    public boolean isAutorizada() {
        return "AUTORIZADO".equals(datos.get("estado"));
    }

    public String getCompradorRazonSocial() {
        return valor("compradorRazonSocial");
    }

    public String getCompradorIdentificacion() {
        return valor("compradorIdentificacion");
    }

    public String getCompradorDireccion() {
        return valor("compradorDireccion");
    }

    public String getCompradorEmail() {
        return valor("compradorEmail");
    }

    /** Formas de pago con el texto de la tabla del SRI (por ejemplo "SIN UTILIZACION DEL SISTEMA FINANCIERO"). */
    public List<String> getFormasPago() {
        List<String> lista = new ArrayList<>();
        String v = datos.get("formasPago");
        if (v != null) {
            for (String p : v.split("\\|")) {
                // Cada forma de pago en lineas de hasta 42 columnas (el ancho del ticket).
                StringBuilder linea = new StringBuilder();
                for (String palabra : sinTildes(p.trim()).split("\\s+")) {
                    if (linea.length() > 0 && linea.length() + 1 + palabra.length() > 42) {
                        lista.add(StringUtils.encodeXML(linea.toString()));
                        linea.setLength(0);
                    }
                    linea.append(linea.length() == 0 ? "" : " ").append(palabra);
                }
                if (linea.length() > 0) {
                    lista.add(StringUtils.encodeXML(linea.toString()));
                }
            }
        }
        return lista;
    }

    /** Las impresoras termicas no siempre imprimen tildes: el ticket va sin ellas. */
    private static String sinTildes(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
