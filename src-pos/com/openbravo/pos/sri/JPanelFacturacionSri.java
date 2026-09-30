package com.openbravo.pos.sri;

import com.openbravo.pos.forms.AppView;

import javax.swing.JComponent;

/** Configuracion > Facturacion electronica, integrada en la ventana de EcoPos. */
public class JPanelFacturacionSri extends JPanelSriIntegrado {

    public JPanelFacturacionSri(AppView app) {
        super(app);
    }

    @Override
    protected JComponent crear(EcoPosSriBridge puente) {
        return puente.crearPanelFacturacion();
    }
}
