package com.openbravo.pos.sri.dominio;

/**
 * Configuracion SMTP para enviar el XML/RIDE al comprador (Resolucion
 * NAC-DGERCGC12-00105: el emisor debe entregarselo si lo pide). Separada de
 * {@link DatosEmisor} a proposito - son dos responsabilidades distintas
 * (identidad tributaria del emisor vs. como se entrega el comprobante) que
 * pueden cambiar de forma independiente.
 */
public class ConfiguracionCorreo {

    private final String host;
    private final int puerto;
    private final String usuario;
    private final char[] clave;
    private final String remitente;
    private final boolean usarTls;

    public ConfiguracionCorreo(String host, int puerto, String usuario, char[] clave, String remitente, boolean usarTls) {
        this.host = host;
        this.puerto = puerto;
        this.usuario = usuario;
        this.clave = clave;
        this.remitente = remitente;
        this.usarTls = usarTls;
    }

    public String getHost() { return host; }
    public int getPuerto() { return puerto; }
    public String getUsuario() { return usuario; }
    public char[] getClave() { return clave; }
    public String getRemitente() { return remitente; }
    public boolean isUsarTls() { return usarTls; }
}
