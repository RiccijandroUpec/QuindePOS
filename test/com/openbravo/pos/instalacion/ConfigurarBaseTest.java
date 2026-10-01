package com.openbravo.pos.instalacion;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigurarBaseTest {

    @Test
    public void laClaveAleatoriaEsLargaYSoloLetrasYNumeros() {
        String a = ConfigurarBase.claveAleatoria();
        String b = ConfigurarBase.claveAleatoria();
        assertEquals(24, a.length());
        assertTrue(a.matches("[A-Za-z0-9]+"));
        assertFalse(a.equals(b));
    }

    @Test
    public void unaActualizacionConservaLaConfiguracionExistente() throws Exception {
        File config = File.createTempFile("quinde", ".properties");
        config.deleteOnExit();
        Files.write(config.toPath(), "db.user=cajero\n".getBytes(StandardCharsets.UTF_8));
        Map<String, String> a = new HashMap<>();
        a.put("config", config.getAbsolutePath());
        a.put("usuario", "root");
        String mensaje = ConfigurarBase.configurar(a);
        assertTrue(mensaje.startsWith("Se conserva"));
        assertEquals("db.user=cajero\n", new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaNombresDeBaseRaros() throws Exception {
        Map<String, String> a = new HashMap<>();
        a.put("config", new File(System.getProperty("java.io.tmpdir"), "no-existe-quinde.properties").getAbsolutePath());
        a.put("dir", ".");
        a.put("usuario", "root");
        a.put("base", "quinde`; DROP DATABASE x");
        ConfigurarBase.configurar(a);
    }
}
