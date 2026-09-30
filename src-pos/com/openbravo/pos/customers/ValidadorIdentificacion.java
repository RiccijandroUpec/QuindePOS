package com.openbravo.pos.customers;

/**
 * Valida cedulas y RUC ecuatorianos (algoritmos del Registro Civil y del SRI)
 * antes de facturar, para no enviar al SRI una identificacion mal escrita que
 * luego rechaza (error 69 "identificacion del receptor").
 *
 * <ul>
 *   <li>Cedula: 10 digitos, provincia 01-24 o 30, tercer digito &lt; 6, modulo 10.</li>
 *   <li>RUC persona natural: cedula valida + 3 digitos de establecimiento (no "000").</li>
 *   <li>RUC sociedad privada (tercer digito 9) y publica (6): modulo 11. El SRI ha
 *       emitido algunos RUC de sociedades que no cumplen el modulo 11, asi que en
 *       esos dos casos un digito verificador distinto es solo una advertencia.</li>
 *   <li>9999999999999: consumidor final.</li>
 *   <li>Cualquier otro texto alfanumerico se toma como pasaporte / identificacion del exterior.</li>
 * </ul>
 */
public final class ValidadorIdentificacion {

    public static final String CONSUMIDOR_FINAL = "9999999999999";

    public enum Tipo {
        CEDULA("C\u00E9dula"),
        RUC_PERSONA_NATURAL("RUC persona natural"),
        RUC_SOCIEDAD("RUC sociedad"),
        RUC_PUBLICO("RUC entidad p\u00FAblica"),
        CONSUMIDOR_FINAL("Consumidor final"),
        PASAPORTE("Pasaporte / exterior"),
        INVALIDA("Inv\u00E1lida");

        private final String descripcion;

        Tipo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    /** Resultado de validar: si se puede facturar con ella, su tipo y un mensaje para el cajero. */
    public static final class Resultado {
        private final boolean valida;
        private final Tipo tipo;
        private final String mensaje;

        Resultado(boolean valida, Tipo tipo, String mensaje) {
            this.valida = valida;
            this.tipo = tipo;
            this.mensaje = mensaje;
        }

        public boolean isValida() {
            return valida;
        }

        public Tipo getTipo() {
            return tipo;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    private ValidadorIdentificacion() {
    }

    public static Resultado validar(String identificacion) {
        String id = identificacion == null ? "" : identificacion.trim();
        if (id.isEmpty()) {
            return new Resultado(false, Tipo.INVALIDA, "Escribe la c\u00E9dula o el RUC");
        }
        if (CONSUMIDOR_FINAL.equals(id)) {
            return new Resultado(true, Tipo.CONSUMIDOR_FINAL, "Consumidor final");
        }
        if (!soloDigitos(id)) {
            if (id.matches("[A-Za-z0-9-]{5,20}")) {
                return new Resultado(true, Tipo.PASAPORTE, "Se enviar\u00E1 como pasaporte / identificaci\u00F3n del exterior");
            }
            return new Resultado(false, Tipo.INVALIDA, "Solo letras, n\u00FAmeros y guiones");
        }
        if (id.length() == 10) {
            return esCedula(id)
                    ? new Resultado(true, Tipo.CEDULA, "C\u00E9dula v\u00E1lida")
                    : new Resultado(false, Tipo.INVALIDA, "C\u00E9dula no v\u00E1lida: revisa los n\u00FAmeros");
        }
        if (id.length() == 13) {
            return validarRuc(id);
        }
        return new Resultado(false, Tipo.INVALIDA, "La c\u00E9dula tiene 10 d\u00EDgitos y el RUC 13");
    }

    private static Resultado validarRuc(String ruc) {
        int provincia = Integer.parseInt(ruc.substring(0, 2));
        if (!(provincia >= 1 && provincia <= 24) && provincia != 30) {
            return new Resultado(false, Tipo.INVALIDA, "RUC no v\u00E1lido: los dos primeros d\u00EDgitos (provincia) no existen");
        }
        int tercero = ruc.charAt(2) - '0';
        if (tercero < 6) {
            if (ruc.endsWith("000")) {
                return new Resultado(false, Tipo.INVALIDA, "RUC no v\u00E1lido: termina en 000");
            }
            return esCedula(ruc.substring(0, 10))
                    ? new Resultado(true, Tipo.RUC_PERSONA_NATURAL, "RUC v\u00E1lido (persona natural)")
                    : new Resultado(false, Tipo.INVALIDA, "RUC no v\u00E1lido: revisa los n\u00FAmeros");
        }
        if (tercero == 9) {
            if (ruc.endsWith("000")) {
                return new Resultado(false, Tipo.INVALIDA, "RUC no v\u00E1lido: termina en 000");
            }
            return modulo11(ruc, new int[]{4, 3, 2, 7, 6, 5, 4, 3, 2}, 9)
                    ? new Resultado(true, Tipo.RUC_SOCIEDAD, "RUC v\u00E1lido (sociedad)")
                    : new Resultado(true, Tipo.RUC_SOCIEDAD, "RUC de sociedad con d\u00EDgito verificador at\u00EDpico: conf\u00EDrmalo con el cliente");
        }
        if (tercero == 6) {
            if (ruc.endsWith("0000")) {
                return new Resultado(false, Tipo.INVALIDA, "RUC no v\u00E1lido: termina en 0000");
            }
            return modulo11(ruc, new int[]{3, 2, 7, 6, 5, 4, 3, 2}, 8)
                    ? new Resultado(true, Tipo.RUC_PUBLICO, "RUC v\u00E1lido (entidad p\u00FAblica)")
                    : new Resultado(true, Tipo.RUC_PUBLICO, "RUC p\u00FAblico con d\u00EDgito verificador at\u00EDpico: conf\u00EDrmalo con el cliente");
        }
        return new Resultado(false, Tipo.INVALIDA, "RUC no v\u00E1lido: el tercer d\u00EDgito no puede ser " + tercero);
    }

    /** Cedula ecuatoriana: provincia, tercer digito y modulo 10. */
    public static boolean esCedula(String cedula) {
        if (cedula == null || cedula.length() != 10 || !soloDigitos(cedula)) {
            return false;
        }
        int provincia = Integer.parseInt(cedula.substring(0, 2));
        if (!(provincia >= 1 && provincia <= 24) && provincia != 30) {
            return false;
        }
        if (cedula.charAt(2) - '0' >= 6) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 9; i++) {
            int valor = (cedula.charAt(i) - '0') * (i % 2 == 0 ? 2 : 1);
            suma += valor > 9 ? valor - 9 : valor;
        }
        int verificador = (10 - suma % 10) % 10;
        return verificador == cedula.charAt(9) - '0';
    }

    private static boolean modulo11(String ruc, int[] coeficientes, int posicionVerificador) {
        int suma = 0;
        for (int i = 0; i < coeficientes.length; i++) {
            suma += (ruc.charAt(i) - '0') * coeficientes[i];
        }
        int verificador = 11 - suma % 11;
        if (verificador == 11) {
            verificador = 0;
        }
        return verificador != 10 && verificador == ruc.charAt(posicionVerificador) - '0';
    }

    private static boolean soloDigitos(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            if (!Character.isDigit(texto.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
