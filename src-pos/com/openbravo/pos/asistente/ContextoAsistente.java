package com.openbravo.pos.asistente;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.forms.AppConfig;
import com.openbravo.pos.forms.AppProperties;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.DataLogicSystem;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Lo que comparten los pasos del asistente: la app, su configuracion y la base. */
final class ContextoAsistente {

    private static final Logger LOG = Logger.getLogger(ContextoAsistente.class.getName());

    final AppView app;
    final AppProperties props;
    final Session session;
    final DataLogicSystem dls;
    final EstadoAsistente estado;
    /** Carpeta del modulo de facturacion (sri-conector/), donde estan los datos del negocio y el logo. */
    final File carpetaConector;

    ContextoAsistente(AppView app) {
        this.app = app;
        this.props = app.getProperties();
        this.session = app.getSession();
        this.dls = (DataLogicSystem) app.getBean("com.openbravo.pos.forms.DataLogicSystem");
        this.estado = new EstadoAsistente(dls);
        this.carpetaConector = new File(new File(System.getProperty("dirname.path", "./")), "sri-conector");
    }

    /** Cambia una opcion de esta computadora (ecopos.properties) y la guarda. */
    void guardarOpcion(String clave, String valor) {
        if (props instanceof AppConfig) {
            AppConfig config = (AppConfig) props;
            config.setProperty(clave, valor);
            try {
                config.save();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "No se pudo guardar la opcion " + clave, e);
            }
        }
    }
}
