package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.FormaPago;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormaPagoResolverTest {

    @Test
    void efectivoYCreditoDelNegocioVanSinSistemaFinanciero() {
        assertEquals(FormaPago.SIN_SISTEMA_FINANCIERO, FormaPagoResolver.paraNombreEcoPos("cash"));
        assertEquals(FormaPago.SIN_SISTEMA_FINANCIERO, FormaPagoResolver.paraNombreEcoPos("debt"));
        assertEquals(FormaPago.SIN_SISTEMA_FINANCIERO, FormaPagoResolver.paraNombreEcoPos(null));
    }

    @Test
    void transferenciaDeUnaYChequeUsanElSistemaFinanciero() {
        assertEquals(FormaPago.OTROS_SISTEMA_FINANCIERO, FormaPagoResolver.paraNombreEcoPos("bank"));
        assertEquals(FormaPago.OTROS_SISTEMA_FINANCIERO, FormaPagoResolver.paraNombreEcoPos("deuna"));
        assertEquals(FormaPago.OTROS_SISTEMA_FINANCIERO, FormaPagoResolver.paraNombreEcoPos("cheque"));
    }

    @Test
    void tarjetas() {
        assertEquals(FormaPago.TARJETA_CREDITO, FormaPagoResolver.paraNombreEcoPos("magcard"));
        assertEquals(FormaPago.TARJETA_DEBITO, FormaPagoResolver.paraNombreEcoPos("debitcard"));
    }
}
