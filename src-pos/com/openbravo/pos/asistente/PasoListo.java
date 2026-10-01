package com.openbravo.pos.asistente;

import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Final: resumen de lo hecho y de lo que quedo para despues. */
final class PasoListo extends Paso {

    private final List<Paso> pasos;
    private final JPanel resumen = Ui.columna();

    PasoListo(ContextoAsistente ctx, List<Paso> pasos) {
        super(ctx);
        this.pasos = pasos;
    }

    @Override
    String id() {
        return "listo";
    }

    @Override
    String nombre() {
        return "\u00A1Listo!";
    }

    @Override
    String titulo() {
        return "\u00A1Tu caja est\u00E1 lista!";
    }

    @Override
    String descripcion() {
        return "Ya puedes empezar a vender. Esto es lo que configuraste:";
    }

    @Override
    boolean sePuedePosponer() {
        return false;
    }

    @Override
    protected JComponent crearPanel() {
        JPanel col = Ui.columna();
        col.add(resumen);
        col.add(javax.swing.Box.createVerticalStrut(18));
        col.add(Ui.nota("Lo que qued\u00F3 pendiente lo puedes terminar cuando quieras en:<br>"
                + "<b>Sistema \u2192 Asistente de configuraci\u00F3n</b>."));
        return col;
    }

    @Override
    void alMostrar() {
        resumen.removeAll();
        for (Paso p : pasos) {
            if (!p.sePuedePosponer()) {
                continue;
            }
            boolean hecho = ctx.estado.hecho(p.id());
            resumen.add(Ui.texto((hecho ? "<span style='color:#2E9E6B'>\u2713</span> " : "<span style='color:gray'>\u2026</span> ")
                    + "<b>" + p.nombre() + "</b>" + (hecho ? "" : " <span style='color:gray'>\u2014 pendiente</span>")));
            resumen.add(javax.swing.Box.createVerticalStrut(6));
        }
        resumen.revalidate();
    }

    @Override
    String guardar() {
        return null;
    }
}
