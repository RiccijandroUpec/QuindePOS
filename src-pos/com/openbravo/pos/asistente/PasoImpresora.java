package com.openbravo.pos.asistente;

import com.openbravo.pos.forms.AppProperties;
import com.openbravo.pos.printer.DeviceTicket;
import com.openbravo.pos.printer.TicketParser;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Impresora de tickets (y de cocina, si el negocio envia pedidos a cocina): elige entre las
 * impresoras de Windows, imprime un ticket de prueba y guarda la eleccion en esta computadora.
 */
final class PasoImpresora extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoImpresora.class.getName());
    private static final String SIN_IMPRESORA = "Sin impresora (ver el ticket en pantalla)";
    private static final String NINGUNA = "No usar impresora de cocina";

    private final JComboBox<String> tickets = new JComboBox<>();
    private final JComboBox<String> cocina = new JComboBox<>();
    private final JCheckBox cajon = new JCheckBox("Abrir el caj\u00F3n de dinero en la prueba (si est\u00E1 conectado a la impresora)");
    private JPanel filaCocina;

    PasoImpresora(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "impresora";
    }

    @Override
    String nombre() {
        return "Impresora";
    }

    @Override
    String titulo() {
        return "Tu impresora de tickets";
    }

    @Override
    String descripcion() {
        return "Elige la impresora t\u00E9rmica e imprime una prueba. Se guarda solo en esta computadora.";
    }

    @Override
    protected JComponent crearPanel() {
        tickets.addItem(SIN_IMPRESORA);
        cocina.addItem(NINGUNA);
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            tickets.addItem(s.getName());
            cocina.addItem(s.getName());
        }
        seleccionar(tickets, ctx.props.getProperty("machine.printer"), SIN_IMPRESORA);
        seleccionar(cocina, ctx.props.getProperty("machine.printer.2"), NINGUNA);

        JPanel col = Ui.columna();
        col.add(Ui.formulario("Impresora de tickets", tickets));
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        fila.setOpaque(false);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton prueba = new JButton("Imprimir ticket de prueba");
        prueba.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                imprimirPrueba((String) tickets.getSelectedItem(), cajon.isSelected());
            }
        });
        fila.add(prueba);
        col.add(fila);
        cajon.setOpaque(false);
        cajon.setFont(cajon.getFont().deriveFont(15f));
        cajon.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(cajon);
        col.add(Ui.nota("\u00BFNo aparece? Inst\u00E1lala primero en Windows con el programa de su fabricante (Configuraci\u00F3n \u2192 "
                + "Impresoras). Para tickets de 80 mm elige la impresora t\u00E9rmica, no la de oficina."));

        filaCocina = Ui.columna();
        filaCocina.add(Ui.subtitulo("Impresora de cocina"));
        filaCocina.add(Ui.formulario("Comandas", cocina));
        filaCocina.add(Ui.nota("Si cocina no tiene impresora propia, la comanda sale en la impresora de tickets."));
        col.add(filaCocina);
        return col;
    }

    @Override
    void alMostrar() {
        if (filaCocina != null) {
            filaCocina.setVisible(PasoTipo.usaCocina(ctx));
        }
    }

    /** Elige en el combo la impresora guardada ("printer:NOMBRE,receipt"), si existe. */
    private static void seleccionar(JComboBox<String> combo, String guardada, String porDefecto) {
        combo.setSelectedItem(porDefecto);
        if (guardada != null && guardada.startsWith("printer:")) {
            String nombre = guardada.substring("printer:".length());
            int coma = nombre.lastIndexOf(',');
            if (coma > 0) {
                nombre = nombre.substring(0, coma);
            }
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (combo.getItemAt(i).equals(nombre)) {
                    combo.setSelectedIndex(i);
                }
            }
        }
    }

    private static String valor(String elegida, String sinImpresora) {
        return sinImpresora.equals(elegida) ? null : "printer:" + elegida + ",receipt";
    }

    private void imprimirPrueba(String elegida, boolean abrirCajon) {
        final String config = SIN_IMPRESORA.equals(elegida) ? "screen" : valor(elegida, SIN_IMPRESORA);
        AppProperties prueba = new AppProperties() {
            @Override
            public File getConfigFile() {
                return ctx.props.getConfigFile();
            }

            @Override
            public String getHost() {
                return ctx.props.getHost();
            }

            @Override
            public String getProperty(String clave) {
                return "machine.printer".equals(clave) ? config : ctx.props.getProperty(clave);
            }
        };
        com.openbravo.pos.ticket.DatosNegocio datos = com.openbravo.pos.ticket.DatosNegocio.cargar();
        StringBuilder xml = new StringBuilder("<output><ticket>");
        xml.append("<image>Printer.Ticket.Logo</image><line></line>");
        if (datos.isConfigurado()) {
            xml.append("<line size=\"1\"><text align=\"center\" length=\"42\" bold=\"true\">").append(datos.getNombre()).append("</text></line>");
        }
        xml.append("<line></line><line><text align=\"center\" length=\"42\" bold=\"true\">PRUEBA DE IMPRESION</text></line>");
        xml.append("<line><text align=\"center\" length=\"42\">").append(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())).append("</text></line>");
        xml.append("<line></line><line><text align=\"left\" length=\"42\">------------------------------------------</text></line>");
        xml.append("<line><text align=\"left\" length=\"30\">Producto de prueba</text><text align=\"right\" length=\"12\">$1,00</text></line>");
        xml.append("<line size=\"1\"><text align=\"left\" length=\"20\" bold=\"true\">TOTAL</text><text align=\"right\" length=\"22\" bold=\"true\">$1,00</text></line>");
        xml.append("<line><text align=\"left\" length=\"42\">------------------------------------------</text></line>");
        xml.append("<line><text align=\"center\" length=\"42\">Si ves este ticket completo,</text></line>");
        xml.append("<line><text align=\"center\" length=\"42\">tu impresora esta lista.</text></line>");
        xml.append("<line></line><line><text align=\"center\" length=\"42\">Quinde POS</text></line>");
        xml.append("</ticket>");
        if (abrirCajon) {
            xml.append("<opendrawer/>");
        }
        xml.append("</output>");
        try {
            DeviceTicket dt = new DeviceTicket(Asistente.padreDialogos(tickets), prueba);
            new TicketParser(dt, ctx.dls).printTicket(xml.toString());
            if ("screen".equals(config)) {
                javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(tickets),
                        "Sin impresora, los tickets se ven en la pantalla de venta.", nombre(),
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
            } else {
                javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(tickets),
                        "Se envi\u00F3 la prueba a \u201C" + elegida + "\u201D.\n\u00BFNo sali\u00F3? Revisa que est\u00E9 encendida, con papel y conectada.",
                        nombre(), javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo imprimir la prueba", e);
            javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(tickets),
                    "No se pudo imprimir: " + e.getMessage(), nombre(), javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    String guardar() {
        String t = valor((String) tickets.getSelectedItem(), SIN_IMPRESORA);
        ctx.guardarOpcion("machine.printer", t == null ? "screen" : t);
        if (filaCocina != null && filaCocina.isVisible()) {
            String c = valor((String) cocina.getSelectedItem(), NINGUNA);
            // Sin impresora de cocina, la comanda sale por la de tickets.
            ctx.guardarOpcion("machine.printer.2", c != null ? c : (t != null ? t : "screen"));
        }
        return null;
    }
}
