package com.openbravo.pos.sri.dominio;

/**
 * Catalogo "Formas de pago" del SRI (Tabla 24 de la ficha tecnica),
 * verificado campo por campo contra la ficha tecnica oficial v2.32.
 */
public enum FormaPago {
    SIN_SISTEMA_FINANCIERO("01", "SIN UTILIZACIÓN DEL SISTEMA FINANCIERO"),   // efectivo, cheque, etc. sin usar el sistema financiero
    COMPENSACION_DEUDAS("15", "COMPENSACIÓN DE DEUDAS"),
    TARJETA_DEBITO("16", "TARJETA DE DÉBITO"),
    DINERO_ELECTRONICO("17", "DINERO ELECTRÓNICO"),
    TARJETA_PREPAGO("18", "TARJETA PREPAGO"),
    TARJETA_CREDITO("19", "TARJETA DE CRÉDITO"),
    OTROS_SISTEMA_FINANCIERO("20", "OTROS CON UTILIZACIÓN DEL SISTEMA FINANCIERO"),
    ENDOSO_TITULOS("21", "ENDOSO DE TÍTULOS");

    private final String codigo;
    private final String descripcion;

    FormaPago(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    /** Texto de la tabla 24 del SRI, el que va impreso en el RIDE y en el ticket. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Descripcion para un codigo del SRI ("01", "20"...); el mismo codigo si no se conoce. */
    public static String descripcionDe(String codigo) {
        for (FormaPago f : values()) {
            if (f.codigo.equals(codigo)) {
                return f.descripcion;
            }
        }
        return codigo == null ? "" : codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
