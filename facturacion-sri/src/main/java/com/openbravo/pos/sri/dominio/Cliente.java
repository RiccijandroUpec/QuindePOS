package com.openbravo.pos.sri.dominio;

/**
 * Datos del comprador, mapeados desde CustomerInfoExt (ECOPos) o desde el
 * consumidor final generico si el ticket no tiene cliente asociado.
 */
public class Cliente {

    /** "04" = RUC, "05" = cedula, "06" = pasaporte, "07" = consumidor final. */
    private final String tipoIdentificacion;
    private final String identificacion;
    private final String razonSocial;
    private final String direccion;
    private final String email;
    private final String telefono;

    public static final String IDENTIFICACION_CONSUMIDOR_FINAL = "9999999999999";

    public Cliente(String tipoIdentificacion, String identificacion, String razonSocial,
                    String direccion, String email, String telefono) {
        this.tipoIdentificacion = tipoIdentificacion;
        this.identificacion = identificacion;
        this.razonSocial = razonSocial;
        this.direccion = direccion;
        this.email = email;
        this.telefono = telefono;
    }

    public static Cliente consumidorFinal() {
        return new Cliente("07", IDENTIFICACION_CONSUMIDOR_FINAL, "CONSUMIDOR FINAL", null, null, null);
    }

    public String getTipoIdentificacion() { return tipoIdentificacion; }
    public String getIdentificacion() { return identificacion; }
    public String getRazonSocial() { return razonSocial; }
    public String getDireccion() { return direccion; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
}
