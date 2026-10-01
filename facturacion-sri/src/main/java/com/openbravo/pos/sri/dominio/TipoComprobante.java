package com.openbravo.pos.sri.dominio;

/**
 * Codigos de tipo de comprobante segun el catalogo del SRI (usado en la
 * clave de acceso). Solo se necesita FACTURA para el alcance actual de
 * este conector; se dejan los demas documentados para cuando se agregue
 * soporte a notas de credito/debito, retenciones, etc.
 */
public enum TipoComprobante {
    FACTURA("01"),
    NOTA_CREDITO("04"),
    NOTA_DEBITO("05"),
    GUIA_REMISION("06"),
    COMPROBANTE_RETENCION("07");

    private final String codigo;

    TipoComprobante(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public static TipoComprobante porCodigo(String codigo) {
        for (TipoComprobante tipo : values()) {
            if (tipo.codigo.equals(codigo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Codigo de tipo de comprobante desconocido: " + codigo);
    }
}
