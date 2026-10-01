package com.openbravo.pos.asistente;

import com.openbravo.pos.forms.DataLogicSystem;
import java.util.Properties;

/**
 * Progreso del asistente de configuracion, guardado en la base (recurso Quinde.Asistente)
 * para que se pueda retomar en cualquier caja: que pasos se hicieron, cuales quedaron
 * "para despues" y si el asistente ya se termino o se pospuso.
 */
final class EstadoAsistente {

    static final String RECURSO = "Quinde.Asistente";
    static final String HECHO = "hecho";
    static final String DESPUES = "despues";

    private final DataLogicSystem dls;
    private final Properties p;

    EstadoAsistente(DataLogicSystem dls) {
        this.dls = dls;
        Properties leidas;
        try {
            leidas = dls.getResourceAsProperties(RECURSO);
        } catch (Exception e) {
            leidas = new Properties();
        }
        this.p = leidas;
    }

    String paso(String id) {
        return p.getProperty("paso." + id);
    }

    boolean hecho(String id) {
        return HECHO.equals(paso(id));
    }

    void marcar(String id, String estado) {
        p.setProperty("paso." + id, estado);
        guardar();
    }

    boolean terminado() {
        return "si".equals(p.getProperty("terminado"));
    }

    /** El usuario cerro el asistente antes de terminar: no se vuelve a abrir solo. */
    boolean pospuesto() {
        return "si".equals(p.getProperty("pospuesto"));
    }

    void terminar() {
        p.setProperty("terminado", "si");
        guardar();
    }

    void posponer() {
        p.setProperty("pospuesto", "si");
        guardar();
    }

    private void guardar() {
        try {
            dls.setResourceAsProperties(RECURSO, p);
        } catch (Exception e) {
            // Sin guardar el progreso el asistente igual funciona; solo no se podra retomar.
        }
    }
}
