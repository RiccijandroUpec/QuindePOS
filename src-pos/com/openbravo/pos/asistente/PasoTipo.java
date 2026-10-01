package com.openbravo.pos.asistente;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * Tipo de negocio: segun lo que elija se proponen las funciones que le sirven
 * (mesas, enviar pedidos a cocina, control de stock). Todo se puede cambiar despues.
 */
final class PasoTipo extends Paso {

    static final String TIENDA = "tienda";
    static final String CAFETERIA = "cafeteria";
    static final String RESTAURANTE = "restaurante";
    static final String OTRO = "otro";

    private final JCheckBox mesas = new JCheckBox("Mesas: elegir la mesa en un plano del sal\u00F3n antes de vender");
    private final JCheckBox cocina = new JCheckBox("Bot\u00F3n \u201CEnviar a cocina\u201D: imprime la comanda con los platos nuevos");
    private final JCheckBox avisoCocina = new JCheckBox("Avisar al cobrar si hay pedidos sin enviar a cocina");
    private final JCheckBox stock = new JCheckBox("Controlar el stock: no dejar vender lo que no hay en el almac\u00E9n");
    private String tipo;

    PasoTipo(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "tipo";
    }

    @Override
    String nombre() {
        return "Tipo de negocio";
    }

    @Override
    String titulo() {
        return "\u00BFQu\u00E9 tipo de negocio tienes?";
    }

    @Override
    String descripcion() {
        return "As\u00ED activamos solo lo que te sirve. Puedes ajustar cada opci\u00F3n.";
    }

    @Override
    protected JComponent crearPanel() {
        tipo = ctx.estado.valor("tipo");
        String botones = ctx.dls.getResourceAsText("Ticket.Buttons");
        mesas.setSelected("restaurant".equals(ctx.props.getProperty("machine.ticketsbag")));
        cocina.setSelected(BotonesVenta.activo(botones, BotonesVenta.ENVIAR_A_COCINA));
        avisoCocina.setSelected(BotonesVenta.activo(botones, BotonesVenta.AVISO_COCINA_AL_COBRAR));
        stock.setSelected(BotonesVenta.activo(botones, BotonesVenta.STOCK_AL_AGREGAR));

        JPanel col = Ui.columna();
        JPanel tarjetas = new JPanel(new GridLayout(2, 2, 12, 12));
        tarjetas.setOpaque(false);
        tarjetas.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjetas.setMaximumSize(new Dimension(620, 170));
        ButtonGroup grupo = new ButtonGroup();
        tarjetas.add(opcion(TIENDA, "Tienda o minimarket", "Abarrotes, bazar, ferreter\u00EDa, farmacia\u2026", grupo));
        tarjetas.add(opcion(CAFETERIA, "Cafeter\u00EDa o panader\u00EDa", "Mostrador, pedidos que salen de cocina", grupo));
        tarjetas.add(opcion(RESTAURANTE, "Restaurante", "Mesas, meseros y comandas a cocina", grupo));
        tarjetas.add(opcion(OTRO, "Otro", "Servicios, ropa, papeler\u00EDa\u2026", grupo));
        col.add(tarjetas);

        col.add(Ui.subtitulo("Funciones"));
        for (JCheckBox cb : new JCheckBox[]{mesas, cocina, avisoCocina, stock}) {
            cb.setOpaque(false);
            cb.setFont(cb.getFont().deriveFont(15f));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(cb);
        }
        col.add(Ui.nota("El control de stock necesita que cargues las existencias en Inventario. "
                + "Las mesas se ven desde el pr\u00F3ximo inicio de sesi\u00F3n."));
        return col;
    }

    private JToggleButton opcion(final String valor, String titulo, String detalle, ButtonGroup grupo) {
        JToggleButton b = new JToggleButton("<html><b style='font-size:13px'>" + titulo + "</b><br>" + detalle + "</html>");
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
        b.setHorizontalAlignment(JLabel.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        b.setSelected(valor.equals(tipo));
        grupo.add(b);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tipo = valor;
                proponer(valor);
            }
        });
        return b;
    }

    /** Lo recomendado para cada tipo (el usuario puede cambiarlo antes de seguir). */
    private void proponer(String t) {
        mesas.setSelected(RESTAURANTE.equals(t));
        cocina.setSelected(RESTAURANTE.equals(t) || CAFETERIA.equals(t));
        avisoCocina.setSelected(RESTAURANTE.equals(t));
        stock.setSelected(false);
    }

    @Override
    String guardar() {
        if (tipo == null) {
            return "Elige el tipo de negocio que m\u00E1s se parece al tuyo.";
        }
        String botones = ctx.dls.getResourceAsText("Ticket.Buttons");
        if (botones != null) {
            String nuevo = BotonesVenta.alternar(botones, BotonesVenta.ENVIAR_A_COCINA, cocina.isSelected());
            nuevo = BotonesVenta.alternar(nuevo, BotonesVenta.AVISO_COCINA_AL_COBRAR, avisoCocina.isSelected());
            nuevo = BotonesVenta.alternar(nuevo, BotonesVenta.STOCK_AL_AGREGAR, stock.isSelected());
            nuevo = BotonesVenta.alternar(nuevo, BotonesVenta.STOCK_AL_CAMBIAR, stock.isSelected());
            if (!nuevo.equals(botones)) {
                ctx.dls.setResource("Ticket.Buttons", 0, nuevo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        ctx.guardarOpcion("machine.ticketsbag", mesas.isSelected() ? "restaurant" : "standard");
        ctx.estado.guardarValor("tipo", tipo);
        return null;
    }

    /** Para otros pasos (por ejemplo, la impresora de cocina y los productos de ejemplo). */
    static boolean usaCocina(ContextoAsistente ctx) {
        return BotonesVenta.activo(ctx.dls.getResourceAsText("Ticket.Buttons"), BotonesVenta.ENVIAR_A_COCINA);
    }
}
