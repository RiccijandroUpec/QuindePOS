package com.openbravo.pos.sri;

import com.openbravo.pos.forms.AppView;

import javax.swing.JComponent;

/** Ventas > Comprobantes electronicos, integrada en la ventana de EcoPos. */
public class JPanelComprobantesSri extends JPanelSriIntegrado {

    public JPanelComprobantesSri(AppView app) {
        super(app);
    }

    @Override
    protected JComponent crear(EcoPosSriBridge puente) {
        return puente.crearPanelComprobantes();
    }
}
