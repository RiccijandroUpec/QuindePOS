package com.openbravo.pos.sri.ui;

import java.util.Locale;

/**
 * Traduce los errores del SRI (y de la red) a una explicacion en palabras
 * simples con lo que hay que hacer, para mostrarla al lado de cada
 * comprobante en vez del mensaje tecnico crudo.
 */
public final class MensajesSri {

    private MensajesSri() {
    }

    /** Explicacion para el usuario; null si no hay error. */
    public static String explicar(String mensajeTecnico) {
        if (mensajeTecnico == null || mensajeTecnico.isBlank()) {
            return null;
        }
        String m = mensajeTecnico.toUpperCase(Locale.ROOT);
        if (m.contains("IDENTIFICACION DEL RECEPTOR") || m.contains("IDENTIFICACIÓN DEL RECEPTOR")) {
            return "El SRI no aceptó la identificación del cliente (cédula o RUC). Corrige los datos del cliente "
                    + "y presiona Reintentar. Recuerda: una factura a Consumidor final no se puede anular con nota de crédito.";
        }
        if (m.contains("CLAVE ACCESO REGISTRADA") || m.contains("CLAVE DE ACCESO REGISTRADA")) {
            return "El SRI ya tenía registrado este comprobante. Presiona Reintentar para consultar si quedó autorizado.";
        }
        if (m.contains("FIRMA") && (m.contains("INVALID") || m.contains("INVÁLID"))) {
            return "La firma electrónica no es válida: puede estar vencida o la clave es incorrecta. "
                    + "Revísala en Facturación electrónica → Firma.";
        }
        if (m.contains("SECUENCIAL REGISTRADO") || m.contains("SECUENCIAL")) {
            return "Ese número de comprobante ya se usó en el SRI. Revisa que el establecimiento y el punto de emisión "
                    + "configurados sean los de esta caja.";
        }
        if (m.contains("RUC") && (m.contains("NO ACTIVO") || m.contains("CLAUSURADO") || m.contains("NO EXISTE"))) {
            return "El SRI indica un problema con el RUC del emisor (no activo o no autorizado para facturar electrónicamente).";
        }
        if (m.contains("ESTRUCTURA") || m.contains("XSD") || m.contains("NO CUMPLE")) {
            return "Algún dato de la factura no tiene el formato que exige el SRI (por ejemplo una dirección muy larga "
                    + "o un campo vacío). Revisa los datos del cliente y del negocio y presiona Reintentar.";
        }
        if (m.contains("TIMED OUT") || m.contains("TIMEOUT") || m.contains("UNKNOWNHOST") || m.contains("CONNECT")
                || m.contains("SOCKET") || m.contains("UNREACHABLE") || m.contains("CONEXI")) {
            return "No hubo conexión con el SRI (sin internet o el SRI no respondió). EcoPos lo reintenta solo cada "
                    + "15 minutos; también puedes presionar Reintentar.";
        }
        if (m.contains("EN PROCESO") || m.contains("PPR")) {
            return "El SRI todavía está procesando el comprobante. EcoPos vuelve a consultar solo.";
        }
        if (m.contains("DATOS-EMISOR") || m.contains("CONFIGURA LOS DATOS")) {
            return "Falta configurar la facturación electrónica (datos del negocio y firma).";
        }
        return "El SRI respondió: " + mensajeTecnico.trim();
    }
}
