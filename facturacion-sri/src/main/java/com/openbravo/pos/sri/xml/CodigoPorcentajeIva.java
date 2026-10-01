package com.openbravo.pos.sri.xml;

import java.math.BigDecimal;

/**
 * Traduce la tarifa de IVA como fraccion ({@code ImpuestoDetalle.getTarifa()},
 * ej. 0.15, 0.00) al codigo de catalogo que exige el XML del SRI
 * (Tabla 17 de la ficha tecnica: 0%->0, 12%->2, 14%->3, 15%->4, 5%->5,
 * 13%->10). Deliberadamente separado del modelo de dominio para poder
 * actualizar este catalogo sin tocar {@code ImpuestoDetalle}.
 */
final class CodigoPorcentajeIva {

    private CodigoPorcentajeIva() {
    }

    static String paraTarifa(BigDecimal tarifaFraccion) {
        if (esIgual(tarifaFraccion, "0.00")) {
            return "0";
        }
        if (esIgual(tarifaFraccion, "0.12")) {
            return "2";
        }
        if (esIgual(tarifaFraccion, "0.14")) {
            return "3";
        }
        if (esIgual(tarifaFraccion, "0.15")) {
            return "4";
        }
        if (esIgual(tarifaFraccion, "0.05")) {
            return "5";
        }
        if (esIgual(tarifaFraccion, "0.13")) {
            return "10";
        }
        throw new IllegalArgumentException("Tarifa de IVA sin codigo de catalogo conocido: " + tarifaFraccion);
    }

    private static boolean esIgual(BigDecimal valor, String referencia) {
        return valor.compareTo(new BigDecimal(referencia)) == 0;
    }
}
