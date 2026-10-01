package com.openbravo.pos.sri.xml;

/**
 * ECOPos (tabla CUSTOMERS) guarda un unico campo TAXID de texto libre, sin
 * distinguir si es RUC, cedula o pasaporte. Esta clase infiere el tipo por
 * longitud (Tabla 6 de la ficha tecnica: RUC=13, cedula=10); es una
 * heuristica razonable para Ecuador, pero debe revisarse si se detectan
 * clientes con TAXID mal formados antes de emitir en produccion.
 */
final class TipoIdentificacionResolver {

    static final String RUC = "04";
    static final String CEDULA = "05";
    static final String PASAPORTE = "06";

    private TipoIdentificacionResolver() {
    }

    static String paraIdentificacion(String identificacion) {
        int longitud = identificacion.length();
        if (longitud == 13) {
            return RUC;
        }
        if (longitud == 10) {
            return CEDULA;
        }
        return PASAPORTE;
    }
}
