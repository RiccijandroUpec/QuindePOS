package com.openbravo.pos.asistente;

import com.openbravo.pos.forms.AppView;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Asistente de configuracion (onboarding) de Quinde POS. Se abre solo la primera vez en
 * un negocio nuevo y despues desde Sistema -> Asistente de configuracion. Cada paso se puede
 * dejar "para despues" y el progreso queda guardado en la base.
 */
public final class Asistente extends JDialog {

    private static final Logger LOG = Logger.getLogger(Asistente.class.getName());

    private final ContextoAsistente ctx;
    private final List<Paso> pasos = new ArrayList<>();
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final JPanel lista = new JPanel();
    private final JLabel titulo = new JLabel();
    private final JLabel descripcion = new JLabel();
    private final JProgressBar progreso = new JProgressBar();
    private final JButton atras = new JButton("Atr\u00E1s");
    private final JButton despues = new JButton("Lo hago despu\u00E9s");
    private final JButton siguiente = new JButton("Siguiente");
    private int actual;
    private boolean terminado;

    private Asistente(Window padre, ContextoAsistente ctx) {
        super(padre, "Configura Quinde POS", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        pasos.add(new PasoBienvenida(ctx));
        pasos.add(new PasoNegocio(ctx));
        pasos.add(new PasoUsuarios(ctx));
        pasos.add(new PasoListo(ctx, pasos));
        construir();
        mostrarPaso(primerPasoPendiente());
    }

    // ------------------------------------------------------------------ entrada

    /**
     * Al arrancar la app: abre el asistente si es un negocio nuevo (sin ventas) y nunca se
     * termino ni se pospuso. Con -Dquinde.asistente=si se abre siempre (para probarlo).
     */
    public static void alIniciar(final Component padre, final AppView app, final Runnable alTerminar) {
        try {
            ContextoAsistente ctx = new ContextoAsistente(app);
            boolean forzar = "si".equals(System.getProperty("quinde.asistente"));
            if (!forzar && (ctx.estado.terminado() || ctx.estado.pospuesto() || hayVentas(ctx))) {
                return;
            }
            abrir(padre, ctx, alTerminar);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo abrir el asistente de configuracion", e);
        }
    }

    /** Desde el menu: siempre abre el asistente. */
    public static void abrir(Component padre, AppView app, Runnable alTerminar) {
        try {
            abrir(padre, new ContextoAsistente(app), alTerminar);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo abrir el asistente de configuracion", e);
        }
    }

    private static void abrir(Component padre, ContextoAsistente ctx, Runnable alTerminar) {
        Window ventana = padre == null ? null : SwingUtilities.getWindowAncestor(padre);
        Asistente a = new Asistente(ventana, ctx);
        a.setVisible(true);
        if (alTerminar != null) {
            alTerminar.run();
        }
    }

    private static boolean hayVentas(ContextoAsistente ctx) {
        try (java.sql.Statement st = ctx.session.getConnection().createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM TICKETS")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            return true; // ante la duda, no interrumpir a un negocio que ya esta trabajando
        }
    }

    // ------------------------------------------------------------------ ventana

    private void construir() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salir();
            }
        });

        // Izquierda: marca y lista de pasos.
        JPanel izquierda = new JPanel(new BorderLayout());
        izquierda.setBackground(Ui.oscuro() ? new Color(0x10231A) : new Color(0xF4F8F5));
        izquierda.setBorder(BorderFactory.createEmptyBorder(24, 22, 24, 22));
        izquierda.setPreferredSize(new Dimension(230, 10));
        JLabel marca = new JLabel("Quinde POS");
        marca.setFont(marca.getFont().deriveFont(Font.BOLD, 20f));
        marca.setForeground(Ui.acento());
        java.net.URL icono = Asistente.class.getResource("/com/openbravo/images/favicon-48.png");
        if (icono != null) {
            marca.setIcon(new javax.swing.ImageIcon(icono));
            marca.setIconTextGap(10);
        }
        izquierda.add(marca, BorderLayout.NORTH);
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        lista.setOpaque(false);
        lista.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0));
        izquierda.add(lista, BorderLayout.CENTER);
        JLabel ayuda = new JLabel("<html>Puedes volver aqu\u00ED desde<br><b>Sistema \u2192 Asistente</b></html>");
        ayuda.setFont(ayuda.getFont().deriveFont(12f));
        ayuda.setForeground(Ui.suave());
        izquierda.add(ayuda, BorderLayout.SOUTH);

        // Derecha: titulo, contenido y botones.
        JPanel derecha = new JPanel(new BorderLayout());
        JPanel cabecera = new JPanel();
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        cabecera.setBorder(BorderFactory.createEmptyBorder(26, 32, 8, 32));
        progreso.setAlignmentX(Component.LEFT_ALIGNMENT);
        progreso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progreso.setPreferredSize(new Dimension(10, 6));
        progreso.setForeground(Ui.QUINDE);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 26f));
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        titulo.setBorder(BorderFactory.createEmptyBorder(16, 0, 4, 0));
        descripcion.setFont(descripcion.getFont().deriveFont(15f));
        descripcion.setForeground(Ui.suave());
        descripcion.setAlignmentX(Component.LEFT_ALIGNMENT);
        cabecera.add(progreso);
        cabecera.add(titulo);
        cabecera.add(descripcion);
        derecha.add(cabecera, BorderLayout.NORTH);

        for (Paso p : pasos) {
            JPanel envoltura = new JPanel(new BorderLayout());
            envoltura.setBorder(BorderFactory.createEmptyBorder(8, 32, 8, 32));
            envoltura.add(p.panel(), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(envoltura);
            scroll.setBorder(null);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            contenido.add(scroll, p.id());
        }
        derecha.add(contenido, BorderLayout.CENTER);

        JPanel botones = new JPanel(new BorderLayout());
        botones.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.oscuro() ? new Color(0x23402F) : new Color(0xDCE6E0)),
                BorderFactory.createEmptyBorder(14, 32, 14, 32)));
        JPanel izquierdaBotones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        izquierdaBotones.add(atras);
        JPanel derechaBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        derechaBotones.add(despues);
        derechaBotones.add(siguiente);
        botones.add(izquierdaBotones, BorderLayout.WEST);
        botones.add(derechaBotones, BorderLayout.EAST);
        for (JButton b : new JButton[]{atras, despues, siguiente}) {
            b.setFont(b.getFont().deriveFont(15f));
            b.setPreferredSize(new Dimension(b.getPreferredSize().width + 24, 40));
        }
        // El boton principal cambia de texto: ancho fijo para el mas largo ("Empezar a vender").
        siguiente.setText("Empezar a vender");
        siguiente.setPreferredSize(new Dimension(siguiente.getPreferredSize().width + 40, 40));
        siguiente.putClientProperty("JButton.buttonType", "default");
        siguiente.setBackground(Ui.SELVA);
        siguiente.setForeground(Color.WHITE);
        getRootPane().setDefaultButton(siguiente);
        derecha.add(botones, BorderLayout.SOUTH);

        atras.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mostrarPaso(actual - 1);
            }
        });
        despues.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ctx.estado.marcar(pasos.get(actual).id(), EstadoAsistente.DESPUES);
                avanzar();
            }
        });
        siguiente.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Paso p = pasos.get(actual);
                String problema;
                try {
                    problema = p.guardar();
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Error guardando el paso " + p.id(), ex);
                    problema = "No se pudo guardar: " + ex.getMessage();
                }
                if (problema != null) {
                    JOptionPane.showMessageDialog(Asistente.this, problema, p.nombre(), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (p.sePuedePosponer()) {
                    ctx.estado.marcar(p.id(), EstadoAsistente.HECHO);
                }
                if (actual == pasos.size() - 1) {
                    ctx.estado.terminar();
                    terminado = true;
                    dispose();
                } else {
                    avanzar();
                }
            }
        });

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(izquierda, BorderLayout.WEST);
        raiz.add(derecha, BorderLayout.CENTER);
        setContentPane(raiz);
        setSize(980, 680);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(getOwner());
    }

    private void avanzar() {
        mostrarPaso(Math.min(actual + 1, pasos.size() - 1));
    }

    private int primerPasoPendiente() {
        for (int i = 1; i < pasos.size() - 1; i++) {
            if (ctx.estado.paso(pasos.get(i).id()) == null) {
                return i == 1 ? 0 : i; // si no hizo nada, empieza por la bienvenida
            }
        }
        return 0;
    }

    private void mostrarPaso(int i) {
        if (i < 0 || i >= pasos.size()) {
            return;
        }
        actual = i;
        Paso p = pasos.get(i);
        p.alMostrar();
        tarjetas.show(contenido, p.id());
        titulo.setText(p.titulo());
        descripcion.setText("<html>" + p.descripcion() + "</html>");
        progreso.setMaximum(pasos.size() - 1);
        progreso.setValue(i);
        atras.setVisible(i > 0);
        despues.setVisible(p.sePuedePosponer());
        siguiente.setText(i == 0 ? "Empezar" : i == pasos.size() - 1 ? "Empezar a vender" : "Guardar y seguir");
        pintarLista();
    }

    private void pintarLista() {
        lista.removeAll();
        for (int i = 0; i < pasos.size(); i++) {
            Paso p = pasos.get(i);
            String estado = ctx.estado.paso(p.id());
            String marca = i == actual ? "\u25CF" : EstadoAsistente.HECHO.equals(estado) ? "\u2713"
                    : EstadoAsistente.DESPUES.equals(estado) ? "\u2026" : "\u25CB";
            JLabel l = new JLabel(marca + "   " + p.nombre());
            l.setFont(l.getFont().deriveFont(i == actual ? Font.BOLD : Font.PLAIN, 15f));
            l.setForeground(i == actual ? Ui.acento() : EstadoAsistente.HECHO.equals(estado) ? Ui.QUINDE : Ui.suave());
            l.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            lista.add(l);
        }
        lista.revalidate();
        lista.repaint();
    }

    private void salir() {
        if (terminado) {
            dispose();
            return;
        }
        int r = JOptionPane.showConfirmDialog(this,
                "<html>\u00BFSalir del asistente?<br><br>Lo que ya guardaste se mantiene. Puedes terminarlo cuando quieras<br>"
                        + "desde <b>Sistema \u2192 Asistente de configuraci\u00F3n</b>.</html>",
                "Configura Quinde POS", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            ctx.estado.posponer();
            dispose();
        }
    }

    /** Para los pasos: el componente sobre el que abrir sus dialogos. */
    static Component padreDialogos(JComponent desde) {
        Window w = SwingUtilities.getWindowAncestor(desde);
        return w != null ? w : desde;
    }
}
