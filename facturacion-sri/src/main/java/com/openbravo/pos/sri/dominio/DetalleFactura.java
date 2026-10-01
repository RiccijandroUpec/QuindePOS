package com.openbravo.pos.sri.dominio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una linea de producto/servicio, mapeada desde TicketLineInfo (ECOPos).
 */
public class DetalleFactura {

    private final String codigoPrincipal;
    private final String descripcion;
    private final BigDecimal cantidad;
    private final BigDecimal precioUnitario;
    private final BigDecimal descuento;
    private final BigDecimal precioTotalSinImpuesto;
    private final List<ImpuestoDetalle> impuestos;

    public DetalleFactura(String codigoPrincipal, String descripcion, BigDecimal cantidad,
                           BigDecimal precioUnitario, BigDecimal descuento,
                           BigDecimal precioTotalSinImpuesto, List<ImpuestoDetalle> impuestos) {
        this.codigoPrincipal = codigoPrincipal;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.descuento = descuento;
        this.precioTotalSinImpuesto = precioTotalSinImpuesto;
        this.impuestos = impuestos;
    }

    public String getCodigoPrincipal() { return codigoPrincipal; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getDescuento() { return descuento; }
    public BigDecimal getPrecioTotalSinImpuesto() { return precioTotalSinImpuesto; }
    public List<ImpuestoDetalle> getImpuestos() { return impuestos; }
}
