package com.openbravo.pos.asistente;

import javax.swing.JComponent;

/** Un paso del asistente de configuracion. */
abstract class Paso {

    protected final ContextoAsistente ctx;
    private JComponent panel;

    Paso(ContextoAsistente ctx) {
        this.ctx = ctx;
    }

    /** Identificador estable (se guarda en el progreso). */
    abstract String id();

    /** Nombre corto para la lista de pasos. */
    abstract String nombre();

    abstract String titulo();

    abstract String descripcion();

    /** Crea el contenido del paso (se llama una sola vez). */
    protected abstract JComponent crearPanel();

    /**
     * Guarda lo que el usuario eligio. Devuelve un mensaje si algo esta mal (y no se avanza),
     * o null si todo quedo guardado.
     */
    abstract String guardar();

    /** False en los pasos que no tienen nada que guardar ni posponer (bienvenida y final). */
    boolean sePuedePosponer() {
        return true;
    }

    /** Se llama cada vez que el paso se muestra (por ejemplo, para refrescar un resumen). */
    void alMostrar() {
    }

    final JComponent panel() {
        if (panel == null) {
            panel = crearPanel();
        }
        return panel;
    }
}
