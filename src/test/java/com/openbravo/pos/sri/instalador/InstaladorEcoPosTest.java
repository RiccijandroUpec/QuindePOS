package com.openbravo.pos.sri.instalador;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InstaladorEcoPosTest {

    private static final String SCRIPT_NEGOCIO = "// script propio del negocio\nprintln(\"hola\");\n";

    private static String hookViejo() throws IOException {
        try (InputStream entrada = InstaladorEcoPosTest.class.getResourceAsStream("/ticket-close-hook-viejo.txt")) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void quitaElHookViejoAlFinalSinTocarElRestoDelScript() throws IOException {
        String conHook = SCRIPT_NEGOCIO + "\n" + hookViejo();

        String resultado = InstaladorEcoPos.sinHookViejoTicketClose(conHook);

        assertEquals(SCRIPT_NEGOCIO, resultado);
    }

    @Test
    void conservaLoQueVieneDespuesDelHookViejo() throws IOException {
        String conHook = SCRIPT_NEGOCIO + "\n" + hookViejo() + "\n// agregado despues\nx = 1;\n";

        String resultado = InstaladorEcoPos.sinHookViejoTicketClose(conHook);

        assertEquals(SCRIPT_NEGOCIO + "// agregado despues\nx = 1;\n", resultado);
    }

    @Test
    void funcionaConFinesDeLineaWindows() throws IOException {
        String conHook = (SCRIPT_NEGOCIO + "\n" + hookViejo()).replace("\n", "\r\n");

        String resultado = InstaladorEcoPos.sinHookViejoTicketClose(conHook);

        assertFalse(resultado.contains("pendientes"));
        assertTrue(resultado.contains("println(\"hola\");"));
    }

    @Test
    void noTocaUnScriptSinHookViejo() {
        String scriptNuevo = SCRIPT_NEGOCIO + "// ecopos-sri-connector: since 2026-07-17 this runs merged in the same\n";

        assertSame(scriptNuevo, InstaladorEcoPos.sinHookViejoTicketClose(scriptNuevo));
    }
}
