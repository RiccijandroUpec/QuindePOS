package com.openbravo.pos.sri.dominio;

import java.math.BigDecimal;

/**
 * Un impuesto aplicado (a una linea de detalle, o a un total del
 * comprobante). {@code tarifa} es la tasa como fraccion (0.15, 0.05, 0.0),
 * igual que {@code TaxInfo.getRate()} en ECOPos - la conversion al
 * "codigoPorcentaje" numerico que exige el XSD del SRI (que ha cambiado
 * con las reformas del IVA: 12%, 14%, 15%...) se hace en la capa de mapeo
 * XML (com.openbravo.pos.sri.xml), no aqui, precisamente para poder
 * actualizar ese catalogo sin tocar el modelo de dominio.
 */
public class ImpuestoDetalle {

    /** "2" = IVA en el catalogo del SRI (tabla 16); se deja parametrizable
     *  por si se necesita ICE u otro impuesto en el futuro. */
    private final String codigoImpuesto;
    private final BigDecimal tarifa;
    private final BigDecimal baseImponible;
    private final BigDecimal valor;

    public ImpuestoDetalle(String codigoImpuesto, BigDecimal tarifa, BigDecimal baseImponible, BigDecimal valor) {
        this.codigoImpuesto = codigoImpuesto;
        this.tarifa = tarifa;
        this.baseImponible = baseImponible;
        this.valor = valor;
    }

    public static ImpuestoDetalle iva(BigDecimal tarifa, BigDecimal baseImponible, BigDecimal valor) {
        return new ImpuestoDetalle("2", tarifa, baseImponible, valor);
    }

    public String getCodigoImpuesto() { return codigoImpuesto; }
    public BigDecimal getTarifa() { return tarifa; }
    public BigDecimal getBaseImponible() { return baseImponible; }
    public BigDecimal getValor() { return valor; }
}
