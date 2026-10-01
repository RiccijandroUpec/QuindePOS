package com.openbravo.pos.sri.dominio;

/**
 * Ambiente de emision ante el SRI. El codigo numerico es el que exige el
 * esquema del comprobante (campo {@code ambiente}) y forma parte de la
 * clave de acceso.
 */
public enum Ambiente {
    PRUEBAS(1),
    PRODUCCION(2);

    private final int codigo;

    Ambiente(int codigo) {
        this.codigo = codigo;
    }

    public int getCodigo() {
        return codigo;
    }
}
