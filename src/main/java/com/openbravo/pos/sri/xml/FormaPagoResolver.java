package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.FormaPago;

import java.util.Locale;

/**
 * ECOPos (columna PAYMENTS.PAYMENT) no tiene un catalogo cerrado de formas
 * de pago - los valores tipicos son "cash", "magcard"/"cheque", o el nombre
 * que el usuario le puso a una tarjeta configurada. Esta resolucion es una
 * heuristica por palabras clave y debe revisarse contra los metodos de pago
 * realmente configurados en cada instalacion antes de emitir en produccion.
 */
final class FormaPagoResolver {

    private FormaPagoResolver() {
    }

    static FormaPago paraNombreEcoPos(String nombrePago) {
        if (nombrePago == null) {
            return FormaPago.SIN_SISTEMA_FINANCIERO;
        }
        String normalizado = nombrePago.toLowerCase(Locale.ROOT);
        if (normalizado.contains("credit")) {
            return FormaPago.TARJETA_CREDITO;
        }
        if (normalizado.contains("debit")) {
            return FormaPago.TARJETA_DEBITO;
        }
        if (normalizado.contains("card") || normalizado.contains("magcard")) {
            return FormaPago.TARJETA_CREDITO;
        }
        // Transferencia ("bank" en EcoPos), DeUna y cheque pasan por el sistema
        // financiero: codigo 20, no 01 (que es solo para efectivo/sin banco).
        if (normalizado.contains("bank") || normalizado.contains("transfer")
                || normalizado.contains("deuna") || normalizado.contains("cheque")) {
            return FormaPago.OTROS_SISTEMA_FINANCIERO;
        }
        return FormaPago.SIN_SISTEMA_FINANCIERO;
    }
}
