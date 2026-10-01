package com.openbravo.pos.asistente;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activa o desactiva opciones de la pantalla de venta en el recurso Ticket.Buttons,
 * comentando o descomentando la linea exacta (asi el negocio puede seguir editandolo a mano).
 */
final class BotonesVenta {

    static final String ENVIAR_A_COCINA = "<button key=\"button.sendorder\" image=\"img.kit_print\" code=\"script.SendOrder\"/>";
    static final String STOCK_AL_AGREGAR = "<event key=\"ticket.addline\" code=\"script.StockCurrentAdd\"/>";
    static final String STOCK_AL_CAMBIAR = "<event key=\"ticket.setline\" code=\"script.StockCurrentSet\"/>";
    static final String AVISO_COCINA_AL_COBRAR = "<event key=\"ticket.total\" code=\"script.Event.Total\"/>";

    private BotonesVenta() {
    }

    /** True si la linea esta activa (sin comentar). */
    static boolean activo(String recurso, String elemento) {
        if (recurso == null) {
            return false;
        }
        Matcher m = Pattern.compile("(?m)^[ \\t]*" + Pattern.quote(elemento)).matcher(recurso);
        return m.find();
    }

    /** Devuelve el recurso con la linea activada o comentada; igual si no la encuentra. */
    static String alternar(String recurso, String elemento, boolean activar) {
        if (recurso == null) {
            return null;
        }
        String e = Pattern.quote(elemento);
        if (activar) {
            return recurso.replaceAll("(?m)^([ \\t]*)<!--\\s*(" + e + ")\\s*-->", "$1$2");
        }
        return recurso.replaceAll("(?m)^([ \\t]*)(" + e + ")[ \\t]*$", "$1<!-- $2 -->");
    }
}
