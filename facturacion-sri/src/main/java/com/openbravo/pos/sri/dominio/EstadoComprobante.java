package com.openbravo.pos.sri.dominio;

/**
 * Espejo del ENUM de la columna {@code estado} en la tabla
 * {@code ecopos_sri_comprobantes} (ver
 * src/main/resources/sql/001_create_ecopos_sri_comprobantes.sql).
 */
public enum EstadoComprobante {
    PENDIENTE,
    ENVIADO,
    AUTORIZADO,
    RECHAZADO,
    ERROR
}
