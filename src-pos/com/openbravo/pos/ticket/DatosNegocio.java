package com.openbravo.pos.ticket;

import com.openbravo.pos.util.StringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Datos del negocio para el encabezado del ticket impreso ($negocio en las plantillas).
 * Salen de la configuracion de Facturacion electronica (sri-conector/config/datos-emisor.properties),
 * asi el negocio los escribe una sola vez. Los textos ya vienen listos para el XML del ticket.
 */
public final class DatosNegocio {

    private final Properties emisor = new Properties();
    private final boolean facturacionElectronica;

    private DatosNegocio(Properties emisor, boolean facturacionElectronica) {
        this.emisor.putAll(emisor);
        this.facturacionElectronica = facturacionElectronica;
    }

    /** Lee los datos actuales (se llama en cada impresion, para tomar cambios sin reiniciar). */
    public static DatosNegocio cargar() {
        File carpeta = new File(System.getProperty("dirname.path", "./"), "sri-conector");
        Properties datos = leer(new File(carpeta, "config/datos-emisor.properties"));
        Properties global = leer(new File(carpeta, "facturacion-global.properties"));
        boolean activa = "true".equals(global.getProperty("activo", "false").trim());
        return new DatosNegocio(datos, activa);
    }

    private static Properties leer(File archivo) {
        Properties p = new Properties();
        if (archivo.isFile()) {
            try (InputStream in = new FileInputStream(archivo)) {
                p.load(in);
            } catch (Exception e) {
                // Sin datos: el ticket sale sin encabezado del negocio.
            }
        }
        return p;
    }

    private String valor(String clave) {
        String v = emisor.getProperty(clave);
        return v == null ? "" : v.trim();
    }

    private static String xml(String texto) {
        return StringUtils.encodeXML(texto);
    }

    /** True si hay al menos un nombre para mostrar. */
    public boolean isConfigurado() {
        return !valor("razonSocial").isEmpty() || !valor("nombreComercial").isEmpty();
    }

    /** Nombre comercial (o la razon social si no hay nombre comercial). */
    public String getNombre() {
        String n = valor("nombreComercial");
        return xml(n.isEmpty() ? valor("razonSocial") : n);
    }

    /** True si hay nombre comercial distinto de la razon social (entonces se imprimen los dos). */
    public boolean isMostrarRazonSocial() {
        String n = valor("nombreComercial");
        return !n.isEmpty() && !n.equalsIgnoreCase(valor("razonSocial")) && !valor("razonSocial").isEmpty();
    }

    public String getRazonSocial() {
        return xml(valor("razonSocial"));
    }

    public String getRuc() {
        return xml(valor("ruc"));
    }

    /** Direccion del local (o la matriz si el local no tiene una propia). */
    public String getDireccion() {
        String d = valor("dirEstablecimiento");
        return xml(d.isEmpty() ? valor("dirMatriz") : d);
    }

    public boolean isObligadoContabilidad() {
        return "SI".equalsIgnoreCase(valor("obligadoContabilidad"));
    }

    /** True si la facturacion electronica esta encendida (el ticket avisa que la factura llega aparte). */
    public boolean isFacturacionElectronica() {
        return facturacionElectronica;
    }
}
