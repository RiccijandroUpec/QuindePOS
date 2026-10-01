package com.openbravo.pos.sri.config;

import com.openbravo.pos.sri.dominio.ConfiguracionCorreo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lee/escribe {@link ConfiguracionCorreo} desde {@code correo.properties} -
 * mismo patron que {@link ConfiguracionLoader} (clave cifrada con
 * {@link ClaveCifrador}, nunca en texto plano).
 */
public final class ConfiguracionCorreoLoader {

    static final String CLAVE_HOST = "host";
    static final String CLAVE_PUERTO = "puerto";
    static final String CLAVE_USUARIO = "usuario";
    static final String CLAVE_CLAVE_CIFRADA = "claveCifrada";
    static final String CLAVE_REMITENTE = "remitente";
    static final String CLAVE_USAR_TLS = "usarTls";

    private ConfiguracionCorreoLoader() {
    }

    public static ConfiguracionCorreo cargar(Path archivo) throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        }

        String claveCifrada = propiedades.getProperty(CLAVE_CLAVE_CIFRADA);
        char[] clave = (claveCifrada == null || claveCifrada.isEmpty()) ? null : ClaveCifrador.descifrar(claveCifrada);

        return new ConfiguracionCorreo(
                requerido(propiedades, CLAVE_HOST, archivo),
                Integer.parseInt(requerido(propiedades, CLAVE_PUERTO, archivo)),
                requerido(propiedades, CLAVE_USUARIO, archivo),
                clave,
                requerido(propiedades, CLAVE_REMITENTE, archivo),
                "true".equalsIgnoreCase(propiedades.getProperty(CLAVE_USAR_TLS, "true")));
    }

    public static void guardar(ConfiguracionCorreo datos, Path archivo) throws IOException {
        Properties propiedades = new Properties();
        propiedades.setProperty(CLAVE_HOST, vacioSiNulo(datos.getHost()));
        propiedades.setProperty(CLAVE_PUERTO, String.valueOf(datos.getPuerto()));
        propiedades.setProperty(CLAVE_USUARIO, vacioSiNulo(datos.getUsuario()));
        propiedades.setProperty(CLAVE_REMITENTE, vacioSiNulo(datos.getRemitente()));
        propiedades.setProperty(CLAVE_USAR_TLS, String.valueOf(datos.isUsarTls()));
        if (datos.getClave() != null) {
            propiedades.setProperty(CLAVE_CLAVE_CIFRADA, ClaveCifrador.cifrar(datos.getClave()));
        }

        Path carpeta = archivo.getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        try (OutputStream salida = Files.newOutputStream(archivo)) {
            propiedades.store(salida, "Configuracion SMTP de ecopos-sri-connector - no editar claveCifrada a mano");
        }
    }

    private static String requerido(Properties propiedades, String clave, Path archivo) {
        String valor = propiedades.getProperty(clave);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalStateException(
                    "Falta el campo obligatorio '" + clave + "' en " + archivo + " - completa la configuracion de correo");
        }
        return valor;
    }

    private static String vacioSiNulo(String valor) {
        return valor == null ? "" : valor;
    }
}
