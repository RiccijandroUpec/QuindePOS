package com.openbravo.pos.sri.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MensajesSriTest {

    @Test
    void erroresConocidosSeExplicanEnPalabrasSimples() {
        assertTrue(MensajesSri.explicar("Estado: NO AUTORIZADO | 69: ERROR EN LA IDENTIFICACION DEL RECEPTOR").contains("cliente"));
        assertTrue(MensajesSri.explicar("43: CLAVE ACCESO REGISTRADA").contains("ya tenía registrado"));
        assertTrue(MensajesSri.explicar("39: FIRMA INVALIDA").contains("firma electrónica"));
        assertTrue(MensajesSri.explicar("java.net.SocketTimeoutException: Read timed out").contains("conexión"));
    }

    @Test
    void sinErrorNoHayExplicacionYLoDesconocidoSeMuestraTalCual() {
        assertNull(MensajesSri.explicar(null));
        assertNull(MensajesSri.explicar("  "));
        assertEquals("El SRI respondió: 99: ALGO NUEVO", MensajesSri.explicar("99: ALGO NUEVO"));
    }
}
