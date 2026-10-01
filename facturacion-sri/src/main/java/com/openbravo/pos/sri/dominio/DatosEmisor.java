package com.openbravo.pos.sri.dominio;

/**
 * Datos del emisor (el negocio que usa ECOPos), configurados una sola vez
 * en la pantalla Swing de configuracion del conector (ver paquete
 * {@code com.openbravo.pos.sri.ui}) y reutilizados para todos los
 * comprobantes.
 */
public class DatosEmisor {

    private final String ruc;
    private final String razonSocial;
    private final String nombreComercial;
    private final String dirMatriz;
    private final String dirEstablecimiento;
    private final String contribuyenteEspecial; // numero de resolucion, o null si no aplica
    private final boolean obligadoContabilidad;
    private final String establecimiento; // 3 digitos, ej "001"
    private final String puntoEmision;    // 3 digitos, ej "001"
    private final Ambiente ambiente;
    private final String rutaCertificadoP12;
    private final char[] claveCertificado;

    public DatosEmisor(String ruc, String razonSocial, String nombreComercial,
                        String dirMatriz, String dirEstablecimiento,
                        String contribuyenteEspecial, boolean obligadoContabilidad,
                        String establecimiento, String puntoEmision, Ambiente ambiente,
                        String rutaCertificadoP12, char[] claveCertificado) {
        this.ruc = ruc;
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.dirMatriz = dirMatriz;
        this.dirEstablecimiento = dirEstablecimiento;
        this.contribuyenteEspecial = contribuyenteEspecial;
        this.obligadoContabilidad = obligadoContabilidad;
        this.establecimiento = establecimiento;
        this.puntoEmision = puntoEmision;
        this.ambiente = ambiente;
        this.rutaCertificadoP12 = rutaCertificadoP12;
        this.claveCertificado = claveCertificado;
    }

    public String getRuc() { return ruc; }
    public String getRazonSocial() { return razonSocial; }
    public String getNombreComercial() { return nombreComercial; }
    public String getDirMatriz() { return dirMatriz; }
    public String getDirEstablecimiento() { return dirEstablecimiento; }
    public String getContribuyenteEspecial() { return contribuyenteEspecial; }
    public boolean isObligadoContabilidad() { return obligadoContabilidad; }
    public String getEstablecimiento() { return establecimiento; }
    public String getPuntoEmision() { return puntoEmision; }
    public Ambiente getAmbiente() { return ambiente; }
    public String getRutaCertificadoP12() { return rutaCertificadoP12; }
    public char[] getClaveCertificado() { return claveCertificado; }
}
