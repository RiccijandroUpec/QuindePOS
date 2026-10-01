package com.openbravo.pos.sri.dominio;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;

/**
 * Genera la clave de acceso de 49 digitos que exige el SRI para cada
 * comprobante, segun la estructura documentada en la ficha tecnica:
 *
 * <pre>
 *   Posiciones  Longitud  Campo
 *   1-8         8         Fecha de emision (ddMMyyyy)
 *   9-10        2         Tipo de comprobante (ej "01" factura)
 *   11-23       13        RUC del emisor
 *   24          1         Ambiente (1=pruebas, 2=produccion)
 *   25-30       6         Serie (3 establecimiento + 3 punto de emision)
 *   31-39       9         Numero secuencial (relleno de ceros a la izquierda)
 *   40-47       8         Codigo numerico (aleatorio, identifica el comprobante)
 *   48          1         Tipo de emision (1=normal)
 *   49          1         Digito verificador (modulo 11)
 * </pre>
 *
 * ⚠️ El digito verificador usa el algoritmo "modulo 11" estandar, pero el
 * manejo de los casos borde (cuando el resultado da 10 u 11) varia segun la
 * fuente consultada. Antes de emitir comprobantes reales, valida esta
 * implementacion contra un generador de referencia (el propio SRI, o un
 * ejemplo conocido de tu ficha tecnica) - ver
 * {@code ClaveAccesoGeneratorTest} para donde agregar ese caso una vez que
 * tengas un ejemplo verificado.
 */
public final class ClaveAccesoGenerator {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ClaveAccesoGenerator() {
    }

    public static String generar(LocalDate fechaEmision, TipoComprobante tipo, String ruc,
                                  Ambiente ambiente, String establecimiento, String puntoEmision,
                                  String secuencial, String tipoEmision) {

        if (ruc == null || ruc.length() != 13) {
            throw new IllegalArgumentException("El RUC debe tener 13 digitos, recibido: " + ruc);
        }
        if (establecimiento == null || establecimiento.length() != 3
                || puntoEmision == null || puntoEmision.length() != 3) {
            throw new IllegalArgumentException("Establecimiento y punto de emision deben tener 3 digitos cada uno");
        }
        if (secuencial == null || secuencial.length() != 9) {
            throw new IllegalArgumentException("El secuencial debe tener 9 digitos (con ceros a la izquierda), recibido: " + secuencial);
        }

        String codigoNumerico = generarCodigoNumerico();

        StringBuilder sb = new StringBuilder(48);
        sb.append(fechaEmision.format(FORMATO_FECHA));
        sb.append(tipo.getCodigo());
        sb.append(ruc);
        sb.append(ambiente.getCodigo());
        sb.append(establecimiento).append(puntoEmision);
        sb.append(secuencial);
        sb.append(codigoNumerico);
        sb.append(tipoEmision);

        String cuerpo = sb.toString();
        if (cuerpo.length() != 48) {
            // salvaguarda interna; no deberia poder pasar dado las validaciones de arriba
            throw new IllegalStateException("Cuerpo de clave de acceso con longitud invalida: " + cuerpo.length());
        }

        char digitoVerificador = calcularDigitoVerificadorModulo11(cuerpo);
        return cuerpo + digitoVerificador;
    }

    private static String generarCodigoNumerico() {
        int valor = RANDOM.nextInt(100_000_000); // 8 digitos: 0..99999999
        return String.format("%08d", valor);
    }

    /**
     * Modulo 11 con pesos ciclicos 2..7, calculado de derecha a izquierda.
     * result = 11 - (suma mod 11); si result == 11 -> 0; si result == 10 -> 1.
     */
    static char calcularDigitoVerificadorModulo11(String cuerpo) {
        int suma = 0;
        int peso = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            int digito = Character.digit(cuerpo.charAt(i), 10);
            suma += digito * peso;
            peso++;
            if (peso > 7) {
                peso = 2;
            }
        }
        int resultado = 11 - (suma % 11);
        if (resultado == 11) {
            resultado = 0;
        } else if (resultado == 10) {
            resultado = 1;
        }
        return Character.forDigit(resultado, 10);
    }
}
