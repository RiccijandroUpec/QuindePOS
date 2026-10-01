package com.openbravo.pos.asistente;

import com.openbravo.pos.sri.EcoPosSriBridge;
import com.openbravo.pos.sri.EcoPosSriGlue;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Facturacion electronica: muestra dentro del asistente la misma pantalla de
 * Sistema -> Facturacion electronica (firma, ambiente, correo, prueba con el SRI).
 */
final class PasoFacturacion extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoFacturacion.class.getName());

    PasoFacturacion(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "facturacion";
    }

    @Override
    String nombre() {
        return "Facturaci\u00F3n SRI";
    }

    @Override
    String titulo() {
        return "Facturaci\u00F3n electr\u00F3nica";
    }

    @Override
    String descripcion() {
        return "Necesitas tu firma electr\u00F3nica (.p12). Empieza en ambiente de PRUEBAS; "
                + "pasa a PRODUCCI\u00D3N cuando el SRI te autorice. Toca \u201CGuardar cambios\u201D abajo de la pantalla.";
    }

    @Override
    protected JComponent crearPanel() {
        JPanel col = Ui.columna();
        EcoPosSriBridge puente = null;
        String problema = null;
        try {
            puente = EcoPosSriGlue.getInstance(ctx.props);
            if (puente == null) {
                problema = EcoPosSriGlue.getProblema();
            }
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "No se pudo cargar la facturacion electronica", e);
        }
        if (puente == null) {
            col.add(Ui.texto("El m\u00F3dulo de facturaci\u00F3n electr\u00F3nica no est\u00E1 instalado en esta computadora"
                    + (problema != null ? " (" + problema + ")" : "") + "."));
            col.add(Ui.nota("Puedes vender igual. Para facturar, instala Quinde POS con la facturaci\u00F3n incluida "
                    + "y configura en Sistema \u2192 Facturaci\u00F3n electr\u00F3nica."));
            return col;
        }
        try {
            JComponent pantalla = puente.crearPanelFacturacion();
            JPanel marco = new JPanel(new BorderLayout());
            marco.setAlignmentX(Component.LEFT_ALIGNMENT);
            marco.add(pantalla, BorderLayout.CENTER);
            marco.setPreferredSize(new Dimension(640, 900));
            col.add(marco);
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "No se pudo mostrar la pantalla de facturacion", e);
            col.add(Ui.texto("No se pudo abrir la pantalla de facturaci\u00F3n: " + e.getMessage()));
        }
        return col;
    }

    @Override
    String guardar() {
        return null; // la pantalla de facturacion guarda con su propio boton
    }
}
