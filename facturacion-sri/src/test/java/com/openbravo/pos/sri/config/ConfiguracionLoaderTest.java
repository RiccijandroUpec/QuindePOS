package com.openbravo.pos.sri.config;

import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionLoaderTest {

    private static DatosEmisor emisorDePrueba() {
        return new DatosEmisor(
                "1790012345001", "ALMACENES DE PRUEBA S.A.", "ALMACENES PRUEBA",
                "Av. Amazonas y Naciones Unidas", "Av. Amazonas y Naciones Unidas",
                "5368", true, "001", "001", Ambiente.PRUEBAS,
                "C:/certificados/prueba.p12", "clave-secreta-del-p12".toCharArray());
    }

    @Test
    void guardaYRecargaLosMismosDatos(@TempDir Path tempDir) throws IOException {
        Path archivo = tempDir.resolve("datos-emisor.properties");
        DatosEmisor original = emisorDePrueba();

        ConfiguracionLoader.guardar(original, archivo);
        DatosEmisor recargado = ConfiguracionLoader.cargar(archivo);

        assertEquals(original.getRuc(), recargado.getRuc());
        assertEquals(original.getRazonSocial(), recargado.getRazonSocial());
        assertEquals(original.getNombreComercial(), recargado.getNombreComercial());
        assertEquals(original.getDirMatriz(), recargado.getDirMatriz());
        assertEquals(original.getEstablecimiento(), recargado.getEstablecimiento());
        assertEquals(original.getPuntoEmision(), recargado.getPuntoEmision());
        assertEquals(original.getAmbiente(), recargado.getAmbiente());
        assertEquals(original.isObligadoContabilidad(), recargado.isObligadoContabilidad());
        assertEquals(original.getRutaCertificadoP12(), recargado.getRutaCertificadoP12());
        assertArrayEquals(original.getClaveCertificado(), recargado.getClaveCertificado());
    }

    @Test
    void laClaveDelCertificadoNoQuedaEnTextoPlanoEnElArchivo(@TempDir Path tempDir) throws IOException {
        Path archivo = tempDir.resolve("datos-emisor.properties");
        ConfiguracionLoader.guardar(emisorDePrueba(), archivo);

        String contenidoCrudo = Files.readString(archivo);

        assertFalse(contenidoCrudo.contains("clave-secreta-del-p12"),
                "la clave en claro no debe aparecer nunca en el archivo:\n" + contenidoCrudo);
        assertTrue(contenidoCrudo.contains("claveCertificadoCifrada"));
    }

    @Test
    void faltaUnCampoObligatorioLanzaErrorClaro(@TempDir Path tempDir) throws IOException {
        Path archivo = tempDir.resolve("incompleto.properties");
        Files.writeString(archivo, "razonSocial=SOLO ESTO\n");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ConfiguracionLoader.cargar(archivo));
        assertTrue(error.getMessage().contains("ruc"), "el mensaje debe indicar cual campo falta");
    }

    @Test
    void sinCertificadoConfiguradoAunSePuedeCargar(@TempDir Path tempDir) throws IOException {
        DatosEmisor sinCertificado = new DatosEmisor(
                "1790012345001", "ALMACENES DE PRUEBA S.A.", null,
                "Av. Amazonas y Naciones Unidas", null,
                null, false, "001", "001", Ambiente.PRUEBAS, null, null);
        Path archivo = tempDir.resolve("sin-certificado.properties");

        ConfiguracionLoader.guardar(sinCertificado, archivo);
        DatosEmisor recargado = ConfiguracionLoader.cargar(archivo);

        assertNull(recargado.getClaveCertificado());
        assertFalse(recargado.isObligadoContabilidad());
    }
}
