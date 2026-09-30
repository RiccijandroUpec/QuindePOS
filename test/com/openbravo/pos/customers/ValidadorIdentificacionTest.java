package com.openbravo.pos.customers;

import com.openbravo.pos.customers.ValidadorIdentificacion.Resultado;
import com.openbravo.pos.customers.ValidadorIdentificacion.Tipo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ValidadorIdentificacionTest {

    @Test
    public void cedulasValidas() {
        for (String cedula : new String[]{"1710034065", "0102030400", "0923456784", "0450022686"}) {
            Resultado r = ValidadorIdentificacion.validar(cedula);
            assertTrue(cedula, r.isValida());
            assertEquals(Tipo.CEDULA, r.getTipo());
        }
    }

    @Test
    public void cedulaConDigitoVerificadorEquivocado() {
        assertFalse(ValidadorIdentificacion.validar("1710034066").isValida());
    }

    @Test
    public void cedulaConProvinciaOTercerDigitoImposible() {
        assertFalse(ValidadorIdentificacion.validar("2510034065").isValida());
        assertFalse(ValidadorIdentificacion.validar("1770034065").isValida());
    }

    @Test
    public void rucPersonaNatural() {
        Resultado r = ValidadorIdentificacion.validar("0450022686001");
        assertTrue(r.isValida());
        assertEquals(Tipo.RUC_PERSONA_NATURAL, r.getTipo());
        assertFalse(ValidadorIdentificacion.validar("0450022686000").isValida());
        assertFalse(ValidadorIdentificacion.validar("0450022687001").isValida());
    }

    @Test
    public void rucSociedadYPublico() {
        assertEquals(Tipo.RUC_SOCIEDAD, ValidadorIdentificacion.validar("1790012344001").getTipo());
        assertEquals(Tipo.RUC_PUBLICO, ValidadorIdentificacion.validar("1760012320001").getTipo());
        // Digito atipico en sociedades: se acepta con advertencia (el SRI emitio algunos asi).
        Resultado atipico = ValidadorIdentificacion.validar("1790012345001");
        assertTrue(atipico.isValida());
        assertTrue(atipico.getMensaje().contains("at\u00EDpico"));
    }

    @Test
    public void consumidorFinalPasaporteYLongitudes() {
        assertEquals(Tipo.CONSUMIDOR_FINAL, ValidadorIdentificacion.validar("9999999999999").getTipo());
        assertEquals(Tipo.PASAPORTE, ValidadorIdentificacion.validar("AB123456").getTipo());
        assertFalse(ValidadorIdentificacion.validar("12345").isValida());
        assertFalse(ValidadorIdentificacion.validar("").isValida());
        assertFalse(ValidadorIdentificacion.validar(null).isValida());
    }
}
