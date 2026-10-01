package com.openbravo.pos.sri.dominio;

import java.math.BigDecimal;

/** Una forma de pago aplicada al comprobante, mapeada desde PaymentInfo (ECOPos). */
public class Pago {

    private final FormaPago formaPago;
    private final BigDecimal total;

    public Pago(FormaPago formaPago, BigDecimal total) {
        this.formaPago = formaPago;
        this.total = total;
    }

    public FormaPago getFormaPago() { return formaPago; }
    public BigDecimal getTotal() { return total; }
}
