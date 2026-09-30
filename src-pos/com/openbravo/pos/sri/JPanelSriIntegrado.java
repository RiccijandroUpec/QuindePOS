package com.openbravo.pos.sri;

import com.openbravo.basic.BasicException;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.JPanelView;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Base de las pantallas de facturacion electronica que se muestran DENTRO de
 * EcoPos (no en ventanas aparte): el contenido lo arma el conector SRI a
 * traves del puente; si el conector no esta instalado se muestra un aviso.
 */
abstract class JPanelSriIntegrado extends JPanel implements JPanelView {

    protected final AppView app;
    private boolean armado;

    JPanelSriIntegrado(AppView app) {
        super(new BorderLayout());
        this.app = app;
    }

    /** Crea el contenido con el puente del conector. */
    protected abstract JComponent crear(EcoPosSriBridge puente);

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public void activate() throws BasicException {
        if (armado) {
            return;
        }
        armado = true;
        EcoPosSriBridge puente = EcoPosSriGlue.getInstance(app.getProperties());
        if (puente == null) {
            JLabel aviso = new JLabel("<html><center><b>La facturaci\u00F3n electr\u00F3nica no est\u00E1 instalada.</b><br>"
                    + "Copia ecopos-sri-connector.jar en la carpeta sri-conector de EcoPos y vuelve a abrir EcoPos.</center></html>",
                    SwingConstants.CENTER);
            aviso.setForeground(UIManager.getColor("Label.disabledForeground"));
            add(aviso, BorderLayout.CENTER);
        } else {
            add(crear(puente), BorderLayout.CENTER);
        }
        revalidate();
    }

    @Override
    public boolean deactivate() {
        return true;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }
}
