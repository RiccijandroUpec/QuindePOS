package com.openbravo.pos.sri.dominio;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Estas pruebas verifican las PROPIEDADES estructurales del algoritmo
 * (longitud, estabilidad, validaciones) - NO verifican un digito
 * verificador contra un ejemplo real del SRI, porque no se dispone de uno
 * confirmado en este momento. Antes de emitir comprobantes reales, agrega
 * aqui un caso con una clave de acceso real conocida (de la ficha tecnica
 * o de un comprobante autorizado de prueba) y verifica que
 * {@link ClaveAccesoGenerator} reproduce exactamente su digito
 * verificador.
 */
class ClaveAccesoGeneratorTest {

    @Test
    void generaClaveDe49Digitos() {
        String clave = ClaveAccesoGenerator.generar(
                LocalDate.of(2026, 7, 6), TipoComprobante.FACTURA, "1790012345001",
                Ambiente.PRUEBAS, "001", "001", "000000001", "1");

        assertEquals(49, clave.length());
        assertTrue(clave.chars().allMatch(Character::isDigit), "la clave debe ser solo digitos");
    }

    @Test
    void primeros8DigitosSonLaFechaDdMmYyyy() {
        String clave = ClaveAccesoGenerator.generar(
                LocalDate.of(2026, 7, 6), TipoComprobante.FACTURA, "1790012345001",
                Ambiente.PRUEBAS, "001", "001", "000000001", "1");

        assertEquals("06072026", clave.substring(0, 8));
    }

    @Test
    void rechazaRucConLongitudInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaveAccesoGenerator.generar(LocalDate.now(), TipoComprobante.FACTURA, "123",
                        Ambiente.PRUEBAS, "001", "001", "000000001", "1"));
    }

    @Test
    void rechazaSecuencialConLongitudInvalida() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaveAccesoGenerator.generar(LocalDate.now(), TipoComprobante.FACTURA, "1790012345001",
                        Ambiente.PRUEBAS, "001", "001", "1", "1"));
    }

    @Test
    void digitoVerificadorEsDeterministaParaElMismoCuerpo() {
        String cuerpo = "060720260117900123450011001001000000001" + "00000000" + "1";
        char v1 = ClaveAccesoGenerator.calcularDigitoVerificadorModulo11(cuerpo);
        char v2 = ClaveAccesoGenerator.calcularDigitoVerificadorModulo11(cuerpo);
        assertEquals(v1, v2);
        assertTrue(Character.isDigit(v1));
    }
}
