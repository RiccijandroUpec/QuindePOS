package com.openbravo.pos.ticket;

import com.openbravo.pos.util.StringUtils;

/**
 * Ayudante para las plantillas del ticket ($xml): escapa textos libres que
 * escribe el usuario (por ejemplo las notas de cocina), para que un "&" o un
 * "<" no rompa el XML de la impresion.
 */
public final class TextoTicket {

    public String esc(Object valor) {
        return valor == null ? "" : StringUtils.encodeXML(valor.toString());
    }
}
