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

    /**
     * "Nombre - RUC 1790012345001" sin escapar, para los reportes (JasperReports no usa XML de ticket).
     * Null si el negocio no esta configurado.
     */
    public String getEncabezadoReporte() {
        if (!isConfigurado()) {
            return null;
        }
        String n = valor("nombreComercial").isEmpty() ? valor("razonSocial") : valor("nombreComercial");
        return valor("ruc").isEmpty() ? n : n + "  -  RUC " + valor("ruc");
    }

    /** Logo del negocio elegido en Facturacion electronica (sri-conector/config/logo.png); null si no hay. */
    public static java.awt.Image logoDelNegocio() {
        File archivo = new File(new File(System.getProperty("dirname.path", "./"), "sri-conector"), "config/logo.png");
        if (!archivo.isFile()) {
            return null;
        }
        try {
            return javax.imageio.ImageIO.read(archivo);
        } catch (Exception e) {
            return null;
        }
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

    /** Direccion matriz (en la factura se imprimen matriz y sucursal por separado). */
    public String getDirMatriz() {
        return xml(valor("dirMatriz"));
    }

    /** Direccion del local si es distinta de la matriz; vacio si es la misma. */
    public String getDirSucursal() {
        String d = valor("dirEstablecimiento");
        return d.equalsIgnoreCase(valor("dirMatriz")) ? "" : xml(d);
    }

    public String getContribuyenteEspecial() {
        return xml(valor("contribuyenteEspecial"));
    }

    public boolean isObligadoContabilidad() {
        return "SI".equalsIgnoreCase(valor("obligadoContabilidad"));
    }

    /** True si la facturacion electronica esta encendida (el ticket avisa que la factura llega aparte). */
    public boolean isFacturacionElectronica() {
        return facturacionElectronica;
    }
}
