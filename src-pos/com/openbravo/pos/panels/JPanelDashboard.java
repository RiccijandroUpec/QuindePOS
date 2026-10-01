package com.openbravo.pos.panels;

import com.formdev.flatlaf.FlatLaf;
import com.openbravo.basic.BasicException;
import com.openbravo.format.Formats;
import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.JPanelView;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.MissingResourceException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Panel del negocio: lo que el dueno quiere ver al abrir la caja. Ventas de
 * hoy (con comparacion contra ayer a la misma hora), tickets, ticket promedio,
 * ventas por hora, productos mas vendidos, formas de pago y productos por
 * agotarse (stock actual en o por debajo del minimo configurado en Inventario).
 */
public class JPanelDashboard extends JPanel implements JPanelView {

    private final AppView app;

    private final JLabel ventasHoy = new JLabel();
    private final JLabel ventasComparacion = new JLabel();
    private final JLabel tickets = new JLabel();
    private final JLabel ticketPromedio = new JLabel();
    private final JLabel stockBajoTotal = new JLabel();
    private final JLabel facturasHoy = new JLabel();
    private final JLabel facturasDetalle = new JLabel();
    private final GraficoHoras grafico = new GraficoHoras();
    private final JPanel masVendidos = new JPanel();
    private final JPanel formasPago = new JPanel();
    private final JPanel stockBajo = new JPanel();
    private final JLabel actualizado = new JLabel();
    /** "Completa la configuracion de Quinde POS (3 de 7)" mientras el asistente no se termine. */
    private final JPanel avisoAsistente = new JPanel(new BorderLayout(12, 0));
    private final JLabel textoAsistente = new JLabel();

    public JPanelDashboard(AppView app) {
        this.app = app;
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titulo = new JLabel("Panel del negocio");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
        JButton refrescar = new JButton("Actualizar");
        refrescar.setFocusable(false);
        refrescar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cargar();
            }
        });
        actualizado.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setOpaque(false);
        cabecera.add(titulo, BorderLayout.WEST);
        JPanel derecha = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        derecha.add(actualizado);
        derecha.add(refrescar);
        cabecera.add(derecha, BorderLayout.EAST);

        JPanel tarjetas = new JPanel(new GridLayout(1, 5, 12, 0));
        tarjetas.setOpaque(false);
        tarjetas.add(tarjeta("Ventas de hoy", ventasHoy, ventasComparacion));
        tarjetas.add(tarjeta("Tickets", tickets, null));
        tarjetas.add(tarjeta("Ticket promedio", ticketPromedio, null));
        tarjetas.add(tarjeta("Por agotarse", stockBajoTotal, null));
        tarjetas.add(tarjeta("Facturas electr\u00F3nicas hoy", facturasHoy, facturasDetalle));

        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(cabecera, BorderLayout.NORTH);
        JPanel medio = new JPanel(new BorderLayout(0, 12));
        medio.setOpaque(false);
        medio.add(avisoAsistente, BorderLayout.NORTH);
        medio.add(tarjetas, BorderLayout.CENTER);
        norte.add(medio, BorderLayout.CENTER);
        construirAvisoAsistente();
        add(norte, BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridLayout(1, 2, 12, 0));
        centro.setOpaque(false);
        centro.add(seccion("Ventas por hora (hoy)", grafico));
        JPanel listas = new JPanel(new GridLayout(3, 1, 0, 12));
        listas.setOpaque(false);
        listas.add(seccion("M\u00E1s vendidos hoy", lista(masVendidos)));
        listas.add(seccion("Formas de pago hoy", lista(formasPago)));
        listas.add(seccion("Productos por agotarse", lista(stockBajo)));
        centro.add(listas);
        add(centro, BorderLayout.CENTER);
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public void activate() throws BasicException {
        cargar();
        actualizarAvisoAsistente();
    }

    private static final String PERMISO_ASISTENTE = "com.openbravo.pos.asistente.AccionAsistente";

    private void construirAvisoAsistente() {
        avisoAsistente.setBackground(com.formdev.flatlaf.FlatLaf.isLafDark() ? new java.awt.Color(0x17352A) : new java.awt.Color(0xE8F6EE));
        avisoAsistente.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0x2E9E6B), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        textoAsistente.setFont(textoAsistente.getFont().deriveFont(15f));
        avisoAsistente.add(textoAsistente, BorderLayout.CENTER);
        JButton continuar = new JButton("Continuar la configuraci\u00F3n");
        continuar.setFocusable(false);
        continuar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                com.openbravo.pos.asistente.Asistente.abrir(JPanelDashboard.this, app, new Runnable() {
                    @Override
                    public void run() {
                        actualizarAvisoAsistente();
                    }
                });
            }
        });
        avisoAsistente.add(continuar, BorderLayout.EAST);
        avisoAsistente.setVisible(false);
    }

    private void actualizarAvisoAsistente() {
        boolean puede = app.getAppUserView() != null && app.getAppUserView().getUser() != null
                && app.getAppUserView().getUser().hasPermission(PERMISO_ASISTENTE);
        int[] avance = puede ? com.openbravo.pos.asistente.Asistente.avance(app) : null;
        if (avance == null) {
            avisoAsistente.setVisible(false);
            return;
        }
        textoAsistente.setText("<html><b>Completa la configuraci\u00F3n de Quinde POS</b> \u2014 "
                + avance[0] + " de " + avance[1] + " pasos listos (tu negocio, impresora, productos, facturaci\u00F3n\u2026)</html>");
        avisoAsistente.setVisible(true);
    }

    @Override
    public boolean deactivate() {
        return true;
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    private void cargar() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp hoy = new Timestamp(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_MONTH, -1);
        Timestamp ayer = new Timestamp(cal.getTimeInMillis());
        Timestamp ahoraAyer = new Timestamp(System.currentTimeMillis() - 24L * 3600 * 1000);

        try {
            Connection con = app.getSession().getConnection();
            double[] totalHoy = ventas(con, hoy, null);
            double[] totalAyer = ventas(con, ayer, ahoraAyer);

            ventasHoy.setText(Formats.CURRENCY.formatValue(totalHoy[0]));
            tickets.setText(String.valueOf((long) totalHoy[1]));
            ticketPromedio.setText(totalHoy[1] > 0 ? Formats.CURRENCY.formatValue(totalHoy[0] / totalHoy[1]) : "\u2014");
            if (totalAyer[0] > 0) {
                double cambio = (totalHoy[0] - totalAyer[0]) / totalAyer[0] * 100.0;
                ventasComparacion.setText(String.format("%s%.0f%% vs. ayer a esta hora", cambio >= 0 ? "\u25B2 +" : "\u25BC ", cambio));
                ventasComparacion.setForeground(cambio >= 0 ? verde() : rojo());
            } else {
                ventasComparacion.setText("Ayer a esta hora: sin ventas");
                ventasComparacion.setForeground(UIManager.getColor("Label.disabledForeground"));
            }

            grafico.setDatos(ventasPorHora(con, hoy));
            llenarMasVendidos(con, hoy);
            llenarFormasPago(con, hoy);
            int bajos = llenarStockBajo(con);
            llenarFacturacion(con, hoy);
            stockBajoTotal.setText(String.valueOf(bajos));
            stockBajoTotal.setForeground(bajos > 0 ? rojo() : UIManager.getColor("Label.foreground"));
            java.io.File respaldo = com.openbravo.pos.forms.RespaldoAutomatico.ultimo(app.getProperties());
            actualizado.setText("Actualizado " + Formats.TIME.formatValue(new Date()) + "   \u00B7   "
                    + (respaldo == null ? "Sin copias de seguridad todav\u00EDa"
                    : "\u00DAltima copia de seguridad: " + Formats.TIMESTAMP.formatValue(new Date(respaldo.lastModified()))));
        } catch (SQLException e) {
            actualizado.setText("No se pudieron leer las ventas: " + e.getMessage());
        }
        revalidate();
        repaint();
    }

    /** {total cobrado, tickets de venta} entre desde y hasta (hasta null = ahora). */
    private static double[] ventas(Connection con, Timestamp desde, Timestamp hasta) throws SQLException {
        String sql = "SELECT SUM(P.TOTAL), COUNT(DISTINCT R.ID) FROM RECEIPTS R "
                + "JOIN TICKETS T ON T.ID = R.ID JOIN PAYMENTS P ON P.RECEIPT = R.ID "
                + "WHERE T.TICKETTYPE = 0 AND R.DATENEW >= ?" + (hasta == null ? "" : " AND R.DATENEW < ?");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, desde);
            if (hasta != null) {
                ps.setTimestamp(2, hasta);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new double[]{rs.getDouble(1), rs.getDouble(2)};
            }
        }
    }

    private static double[] ventasPorHora(Connection con, Timestamp desde) throws SQLException {
        double[] horas = new double[24];
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT R.DATENEW, SUM(P.TOTAL) FROM RECEIPTS R JOIN TICKETS T ON T.ID = R.ID "
                + "JOIN PAYMENTS P ON P.RECEIPT = R.ID WHERE T.TICKETTYPE = 0 AND R.DATENEW >= ? GROUP BY R.ID, R.DATENEW")) {
            ps.setTimestamp(1, desde);
            try (ResultSet rs = ps.executeQuery()) {
                Calendar c = Calendar.getInstance();
                while (rs.next()) {
                    c.setTime(rs.getTimestamp(1));
                    horas[c.get(Calendar.HOUR_OF_DAY)] += rs.getDouble(2);
                }
            }
        }
        return horas;
    }

    private void llenarMasVendidos(Connection con, Timestamp desde) throws SQLException {
        masVendidos.removeAll();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT PR.NAME, SUM(L.UNITS), SUM(L.UNITS * L.PRICE) FROM TICKETLINES L "
                + "JOIN RECEIPTS R ON R.ID = L.TICKET JOIN TICKETS T ON T.ID = R.ID "
                + "JOIN PRODUCTS PR ON PR.ID = L.PRODUCT WHERE T.TICKETTYPE = 0 AND R.DATENEW >= ? "
                + "GROUP BY PR.NAME ORDER BY 2 DESC")) {
            ps.setTimestamp(1, desde);
            ps.setMaxRows(5);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    masVendidos.add(fila(rs.getString(1), Formats.DOUBLE.formatValue(rs.getDouble(2)) + " u \u00B7 "
                            + Formats.CURRENCY.formatValue(rs.getDouble(3)), null));
                }
            }
        }
        if (masVendidos.getComponentCount() == 0) {
            masVendidos.add(vacio("Todav\u00EDa no hay ventas hoy"));
        }
    }

    private void llenarFormasPago(Connection con, Timestamp desde) throws SQLException {
        formasPago.removeAll();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT P.PAYMENT, SUM(P.TOTAL), COUNT(*) FROM PAYMENTS P JOIN RECEIPTS R ON R.ID = P.RECEIPT "
                + "JOIN TICKETS T ON T.ID = R.ID WHERE T.TICKETTYPE = 0 AND R.DATENEW >= ? GROUP BY P.PAYMENT ORDER BY 2 DESC")) {
            ps.setTimestamp(1, desde);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    formasPago.add(fila(nombrePago(rs.getString(1)), Formats.CURRENCY.formatValue(rs.getDouble(2))
                            + " \u00B7 " + rs.getInt(3) + " pago(s)", null));
                }
            }
        }
        if (formasPago.getComponentCount() == 0) {
            formasPago.add(vacio("Todav\u00EDa no hay cobros hoy"));
        }
    }

    /** Facturas electronicas de hoy: autorizadas y cuantas hay que revisar (si el conector SRI esta instalado). */
    private void llenarFacturacion(Connection con, Timestamp desde) {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT SUM(CASE WHEN estado = 'AUTORIZADO' THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN estado IN ('RECHAZADO','ERROR') THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN estado IN ('PENDIENTE','ENVIADO') THEN 1 ELSE 0 END) "
                + "FROM ecopos_sri_comprobantes WHERE fecha_emision >= ?")) {
            ps.setTimestamp(1, desde);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int autorizadas = rs.getInt(1);
                int revisar = rs.getInt(2);
                int proceso = rs.getInt(3);
                facturasHoy.setText(String.valueOf(autorizadas));
                facturasHoy.setForeground(verde());
                if (revisar > 0) {
                    facturasDetalle.setText(revisar + " por revisar (Comprobantes electr\u00F3nicos)");
                    facturasDetalle.setForeground(rojo());
                } else {
                    facturasDetalle.setText(proceso > 0 ? proceso + " en proceso" : "todas autorizadas");
                    facturasDetalle.setForeground(UIManager.getColor("Label.disabledForeground"));
                }
            }
        } catch (SQLException e) {
            facturasHoy.setText("\u2014");
            facturasDetalle.setText("facturaci\u00F3n no instalada");
            facturasDetalle.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }

    /** Productos cuyo stock actual esta en o por debajo del minimo configurado. Devuelve cuantos son. */
    private int llenarStockBajo(Connection con) throws SQLException {
        stockBajo.removeAll();
        int total = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT PR.NAME, SC.UNITS, SL.STOCKSECURITY FROM STOCKCURRENT SC "
                + "JOIN STOCKLEVEL SL ON SL.PRODUCT = SC.PRODUCT AND SL.LOCATION = SC.LOCATION "
                + "JOIN PRODUCTS PR ON PR.ID = SC.PRODUCT "
                + "WHERE SL.STOCKSECURITY IS NOT NULL AND SC.UNITS <= SL.STOCKSECURITY ORDER BY SC.UNITS")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total++;
                    if (total <= 8) {
                        double unidades = rs.getDouble(2);
                        stockBajo.add(fila(rs.getString(1), (unidades <= 0 ? "agotado" : "quedan " + Formats.DOUBLE.formatValue(unidades))
                                + " (m\u00EDnimo " + Formats.DOUBLE.formatValue(rs.getDouble(3)) + ")", rojo()));
                    }
                }
            }
        }
        if (total == 0) {
            stockBajo.add(vacio("<html>Nada por agotarse. Define el stock m\u00EDnimo de cada producto en "
                    + "Inventario \u2192 Stock (m\u00EDnimo por almac\u00E9n).</html>"));
        } else if (total > 8) {
            stockBajo.add(vacio("\u2026 y " + (total - 8) + " m\u00E1s"));
        }
        return total;
    }

    private static String nombrePago(String tipo) {
        try {
            String traducido = AppLocal.getIntString("transpayment." + tipo);
            return traducido == null || traducido.startsWith("transpayment.") ? tipo : traducido;
        } catch (MissingResourceException e) {
            return tipo;
        }
    }

    private static boolean oscuro() {
        return FlatLaf.isLafDark();
    }

    private static Color verde() {
        return oscuro() ? new Color(0xA8E6C1) : new Color(0x1B5E3F);
    }

    private static Color rojo() {
        return oscuro() ? new Color(0xEF9A9A) : new Color(0xC62828);
    }

    private static JPanel tarjeta(String etiqueta, JLabel valor, JLabel detalle) {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 2));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel l = new JLabel(etiqueta);
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        l.setFont(l.getFont().deriveFont(13f));
        valor.setFont(valor.getFont().deriveFont(Font.BOLD, 26f));
        p.add(l);
        p.add(valor);
        if (detalle != null) {
            detalle.setFont(detalle.getFont().deriveFont(12f));
            p.add(detalle);
        }
        return p;
    }

    private static JPanel seccion(String titulo, JComponent contenido) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel t = new JLabel(titulo);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 14f));
        p.add(t, BorderLayout.NORTH);
        p.add(contenido, BorderLayout.CENTER);
        return p;
    }

    private static JScrollPane lista(JPanel panel) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JScrollPane s = new JScrollPane(panel);
        s.setBorder(null);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        return s;
    }

    private static JPanel fila(String nombre, String valor, Color colorValor) {
        JPanel f = new JPanel(new BorderLayout(8, 0));
        f.setOpaque(false);
        f.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JLabel n = new JLabel(nombre);
        JLabel v = new JLabel(valor, SwingConstants.RIGHT);
        if (colorValor != null) {
            v.setForeground(colorValor);
        }
        f.add(n, BorderLayout.CENTER);
        f.add(v, BorderLayout.EAST);
        return f;
    }

    private static JLabel vacio(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        return l;
    }

    /**
     * Barras de ventas por hora: una sola serie (sin leyenda), barras delgadas
     * con extremo redondeado anclado al eje, cuadricula tenue y tooltip por
     * barra. Muestra al menos de 8:00 a 20:00, ampliando si hubo ventas fuera.
     */
    private static final class GraficoHoras extends JComponent {
        private double[] datos = new double[24];
        private int desde = 8;
        private int hasta = 20;

        GraficoHoras() {
            setToolTipText("");
            setPreferredSize(new Dimension(400, 260));
        }

        void setDatos(double[] nuevos) {
            this.datos = nuevos;
            int primera = -1;
            int ultima = -1;
            for (int h = 0; h < 24; h++) {
                if (nuevos[h] != 0) {
                    if (primera < 0) {
                        primera = h;
                    }
                    ultima = h;
                }
            }
            desde = primera < 0 ? 8 : Math.min(primera, 8);
            hasta = ultima < 0 ? 20 : Math.max(ultima, 20);
            repaint();
        }

        private double maximo() {
            double max = 0;
            for (double v : datos) {
                max = Math.max(max, v);
            }
            return max <= 0 ? 1 : max;
        }

        private int izquierda() {
            return 64;
        }

        private double anchoHora() {
            return (getWidth() - izquierda() - 8.0) / (hasta - desde + 1);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int h = desde + (int) ((e.getX() - izquierda()) / anchoHora());
            if (e.getX() < izquierda() || h < desde || h > hasta) {
                return null;
            }
            return String.format("%02d:00 \u2013 %02d:59  \u00B7  %s", h, h, Formats.CURRENCY.formatValue(datos[h]));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont().deriveFont(11f));
            FontMetrics fm = g2.getFontMetrics();
            Color texto = UIManager.getColor("Label.disabledForeground");
            Color grilla = UIManager.getColor("Component.borderColor");

            int arriba = 8;
            int abajo = getHeight() - fm.getHeight() - 6;
            double max = maximo();
            double alto = abajo - arriba;

            for (int i = 0; i <= 4; i++) {
                int y = (int) Math.round(abajo - alto * i / 4.0);
                g2.setColor(grilla);
                g2.drawLine(izquierda(), y, getWidth() - 8, y);
                g2.setColor(texto);
                String etiqueta = Formats.CURRENCY.formatValue(max * i / 4.0);
                g2.drawString(etiqueta, izquierda() - 6 - fm.stringWidth(etiqueta), y + fm.getAscent() / 2 - 1);
            }

            double ancho = anchoHora();
            double barra = Math.max(4, Math.min(28, ancho * 0.6));
            Color color = oscuro() ? new Color(0xA8E6C1) : new Color(0x2E9E6B);
            for (int h = desde; h <= hasta; h++) {
                double x = izquierda() + (h - desde) * ancho + (ancho - barra) / 2;
                double valor = datos[h];
                if (valor > 0) {
                    double y = abajo - alto * valor / max;
                    double radio = Math.min(4, barra / 2);
                    Path2D forma = new Path2D.Double();
                    forma.moveTo(x, abajo);
                    forma.lineTo(x, y + radio);
                    forma.quadTo(x, y, x + radio, y);
                    forma.lineTo(x + barra - radio, y);
                    forma.quadTo(x + barra, y, x + barra, y + radio);
                    forma.lineTo(x + barra, abajo);
                    forma.closePath();
                    g2.setColor(color);
                    g2.fill(forma);
                }
                if ((h - desde) % 2 == 0) {
                    g2.setColor(texto);
                    String hora = String.format("%02dh", h);
                    g2.drawString(hora, (int) (x + barra / 2 - fm.stringWidth(hora) / 2.0), getHeight() - 4);
                }
            }
            g2.dispose();
        }
    }
}
