package com.openbravo.pos.asistente;

import com.openbravo.pos.forms.EcoPosTema;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/** Bienvenida: que hace el asistente y eleccion del tema (claro u oscuro). */
final class PasoBienvenida extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoBienvenida.class.getName());
    private String temaElegido;

    PasoBienvenida(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "bienvenida";
    }

    @Override
    String nombre() {
        return "Bienvenida";
    }

    @Override
    String titulo() {
        return "\u00A1Bienvenido a Quinde POS!";
    }

    @Override
    String descripcion() {
        return "En unos minutos dejamos tu caja lista para vender. Cada paso se puede dejar para despu\u00E9s.";
    }

    @Override
    boolean sePuedePosponer() {
        return false;
    }

    @Override
    protected JComponent crearPanel() {
        JPanel col = Ui.columna();
        javax.swing.Icon logo = com.openbravo.pos.forms.LogoNitido.cargar("/com/openbravo/images/quinde-cabecera");
        if (logo != null) {
            JLabel imagen = new JLabel(logo);
            imagen.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(imagen);
        }
        col.add(Ui.subtitulo("Vamos a configurar:"));
        col.add(Ui.texto("\u2022 Los datos de tu negocio y tu logo (para el ticket y la factura)<br>"
                + "\u2022 La clave del administrador y los usuarios de caja<br>"
                + "\u2022 Despu\u00E9s: tu tipo de negocio, impuestos, impresora, productos y facturaci\u00F3n electr\u00F3nica"));
        col.add(Ui.subtitulo("\u00BFC\u00F3mo prefieres ver Quinde POS?"));

        String actual = ctx.props.getProperty("swing.defaultlaf");
        temaElegido = EcoPosTema.OSCURO.equals(actual) ? EcoPosTema.OSCURO : EcoPosTema.CLARO;
        JPanel opciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        opciones.setOpaque(false);
        opciones.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup grupo = new ButtonGroup();
        opciones.add(opcionTema("Claro", "Fondo blanco, ideal con luz de d\u00EDa", EcoPosTema.CLARO, grupo));
        opciones.add(javax.swing.Box.createHorizontalStrut(12));
        opciones.add(opcionTema("Oscuro", "Descansa la vista de noche", EcoPosTema.OSCURO, grupo));
        col.add(opciones);
        col.add(javax.swing.Box.createVerticalStrut(8));
        col.add(Ui.nota("Se puede cambiar cuando quieras en Configuraci\u00F3n \u2192 General."));
        return col;
    }

    private JToggleButton opcionTema(String nombre, String detalle, final String clase, ButtonGroup grupo) {
        JToggleButton b = new JToggleButton("<html><b style='font-size:13px'>" + nombre + "</b><br>" + detalle + "</html>");
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
        b.setPreferredSize(new Dimension(230, 64));
        b.setHorizontalAlignment(JLabel.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        b.setSelected(clase.equals(temaElegido));
        grupo.add(b);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                temaElegido = clase;
                aplicarTema(clase);
            }
        });
        return b;
    }

    /** Cambia el tema de toda la app al instante. */
    private static void aplicarTema(String clase) {
        try {
            if (!clase.equals(UIManager.getLookAndFeel().getClass().getName())) {
                UIManager.setLookAndFeel(clase);
                com.formdev.flatlaf.FlatLaf.updateUI();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo cambiar el tema", e);
        }
    }

    @Override
    String guardar() {
        ctx.guardarOpcion("swing.defaultlaf", temaElegido);
        return null;
    }
}
