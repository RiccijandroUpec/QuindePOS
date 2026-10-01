package com.openbravo.pos.sri.config;

import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.DatosEmisor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lee/escribe {@link DatosEmisor} desde un archivo {@code .properties}
 * local (ver {@code README.md} para por que se eligio un archivo y no una
 * tabla de base de datos). La clave del certificado .p12 se guarda cifrada
 * (ver {@link ClaveCifrador}), nunca en texto plano.
 *
 * Esta es la clase que tanto el arranque del conector como la pantalla
 * Swing de configuracion usan para leer/guardar los mismos datos - no debe
 * haber otra forma de tocar este archivo.
 */
public final class ConfiguracionLoader {

    static final String CLAVE_RUC = "ruc";
    static final String CLAVE_RAZON_SOCIAL = "razonSocial";
    static final String CLAVE_NOMBRE_COMERCIAL = "nombreComercial";
    static final String CLAVE_DIR_MATRIZ = "dirMatriz";
    static final String CLAVE_DIR_ESTABLECIMIENTO = "dirEstablecimiento";
    static final String CLAVE_CONTRIBUYENTE_ESPECIAL = "contribuyenteEspecial";
    static final String CLAVE_OBLIGADO_CONTABILIDAD = "obligadoContabilidad";
    static final String CLAVE_ESTABLECIMIENTO = "establecimiento";
    static final String CLAVE_PUNTO_EMISION = "puntoEmision";
    static final String CLAVE_AMBIENTE = "ambiente";
    static final String CLAVE_RUTA_CERTIFICADO = "rutaCertificadoP12";
    static final String CLAVE_CLAVE_CERTIFICADO_CIFRADA = "claveCertificadoCifrada";

    private ConfiguracionLoader() {
    }

    public static DatosEmisor cargar(Path archivo) throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        }

        String claveCifrada = propiedades.getProperty(CLAVE_CLAVE_CERTIFICADO_CIFRADA);
        char[] claveCertificado = (claveCifrada == null || claveCifrada.isEmpty())
                ? null
                : ClaveCifrador.descifrar(claveCifrada);

        return new DatosEmisor(
                requerido(propiedades, CLAVE_RUC, archivo),
                requerido(propiedades, CLAVE_RAZON_SOCIAL, archivo),
                propiedades.getProperty(CLAVE_NOMBRE_COMERCIAL),
                requerido(propiedades, CLAVE_DIR_MATRIZ, archivo),
                propiedades.getProperty(CLAVE_DIR_ESTABLECIMIENTO),
                propiedades.getProperty(CLAVE_CONTRIBUYENTE_ESPECIAL),
                "SI".equalsIgnoreCase(propiedades.getProperty(CLAVE_OBLIGADO_CONTABILIDAD)),
                requerido(propiedades, CLAVE_ESTABLECIMIENTO, archivo),
                requerido(propiedades, CLAVE_PUNTO_EMISION, archivo),
                Ambiente.valueOf(requerido(propiedades, CLAVE_AMBIENTE, archivo)),
                propiedades.getProperty(CLAVE_RUTA_CERTIFICADO),
                claveCertificado);
    }

    public static void guardar(DatosEmisor datos, Path archivo) throws IOException {
        Properties propiedades = new Properties();
        propiedades.setProperty(CLAVE_RUC, vacioSiNulo(datos.getRuc()));
        propiedades.setProperty(CLAVE_RAZON_SOCIAL, vacioSiNulo(datos.getRazonSocial()));
        propiedades.setProperty(CLAVE_NOMBRE_COMERCIAL, vacioSiNulo(datos.getNombreComercial()));
        propiedades.setProperty(CLAVE_DIR_MATRIZ, vacioSiNulo(datos.getDirMatriz()));
        propiedades.setProperty(CLAVE_DIR_ESTABLECIMIENTO, vacioSiNulo(datos.getDirEstablecimiento()));
        propiedades.setProperty(CLAVE_CONTRIBUYENTE_ESPECIAL, vacioSiNulo(datos.getContribuyenteEspecial()));
        propiedades.setProperty(CLAVE_OBLIGADO_CONTABILIDAD, datos.isObligadoContabilidad() ? "SI" : "NO");
        propiedades.setProperty(CLAVE_ESTABLECIMIENTO, vacioSiNulo(datos.getEstablecimiento()));
        propiedades.setProperty(CLAVE_PUNTO_EMISION, vacioSiNulo(datos.getPuntoEmision()));
        propiedades.setProperty(CLAVE_AMBIENTE, datos.getAmbiente().name());
        propiedades.setProperty(CLAVE_RUTA_CERTIFICADO, vacioSiNulo(datos.getRutaCertificadoP12()));
        if (datos.getClaveCertificado() != null) {
            propiedades.setProperty(CLAVE_CLAVE_CERTIFICADO_CIFRADA, ClaveCifrador.cifrar(datos.getClaveCertificado()));
        }

        Path carpeta = archivo.getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        try (OutputStream salida = Files.newOutputStream(archivo)) {
            propiedades.store(salida, "Configuracion de ecopos-sri-connector - no editar claveCertificadoCifrada a mano");
        }
    }

    private static String requerido(Properties propiedades, String clave, Path archivo) {
        String valor = propiedades.getProperty(clave);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalStateException(
                    "Falta el campo obligatorio '" + clave + "' en " + archivo + " - completa la configuracion del emisor");
        }
        return valor;
    }

    private static String vacioSiNulo(String valor) {
        return valor == null ? "" : valor;
    }
}
