package com.openbravo.pos.asistente;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Productos: empezar vacio, cargar ejemplos segun el tipo de negocio o importar un CSV de Excel. */
final class PasoProductos extends Paso {

    private static final Logger LOG = Logger.getLogger(PasoProductos.class.getName());
    private static final String VACIO = "vacio";
    private static final String EJEMPLOS = "ejemplos";
    private static final String IMPORTAR = "importar";

    private String opcion;
    private final JLabel existentes = Ui.nota("");
    private final JPanel detalle = Ui.columna();
    private final JLabel archivoElegido = Ui.texto("");
    private CargaProductos.Lectura lectura;

    PasoProductos(ContextoAsistente ctx) {
        super(ctx);
    }

    @Override
    String id() {
        return "productos";
    }

    @Override
    String nombre() {
        return "Productos";
    }

    @Override
    String titulo() {
        return "Tus productos";
    }

    @Override
    String descripcion() {
        return "Los precios se escriben con IVA incluido, como los cobras. Despu\u00E9s los editas en Inventario.";
    }

    @Override
    protected JComponent crearPanel() {
        JPanel col = Ui.columna();
        JPanel tarjetas = new JPanel(new GridLayout(1, 3, 12, 0));
        tarjetas.setOpaque(false);
        tarjetas.setAlignmentX(Component.LEFT_ALIGNMENT);
        tarjetas.setMaximumSize(new Dimension(640, 78));
        ButtonGroup grupo = new ButtonGroup();
        tarjetas.add(tarjeta(EJEMPLOS, "Productos de ejemplo", "Para probar enseguida", grupo));
        tarjetas.add(tarjeta(IMPORTAR, "Importar de Excel", "Archivo CSV con tu lista", grupo));
        tarjetas.add(tarjeta(VACIO, "Empezar vac\u00EDo", "Los cargo yo despu\u00E9s", grupo));
        col.add(tarjetas);
        col.add(javax.swing.Box.createVerticalStrut(8));
        col.add(existentes);
        col.add(detalle);
        return col;
    }

    private JToggleButton tarjeta(final String valor, String titulo, String texto, ButtonGroup grupo) {
        JToggleButton b = new JToggleButton("<html><b style='font-size:13px'>" + titulo + "</b><br>" + texto + "</html>");
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
        b.setHorizontalAlignment(JLabel.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        grupo.add(b);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                opcion = valor;
                mostrarDetalle();
            }
        });
        return b;
    }

    @Override
    void alMostrar() {
        int n = cantidadProductos();
        existentes.setText("<html><body style='width:" + Ui.ANCHO_TEXTO + "px'>" + (n > 0
                ? "Ya tienes " + n + " producto" + (n == 1 ? "" : "s") + ". Lo que cargues se suma (no se repiten nombres ni c\u00F3digos)."
                : "Todav\u00EDa no tienes productos.") + "</body></html>");
        mostrarDetalle();
    }

    private void mostrarDetalle() {
        detalle.removeAll();
        if (EJEMPLOS.equals(opcion)) {
            String tipo = ctx.estado.valor("tipo");
            List<CargaProductos.Producto> lista = CargaProductos.ejemplos(tipo);
            detalle.add(Ui.subtitulo("Se van a agregar " + lista.size() + " productos"));
            StringBuilder sb = new StringBuilder();
            for (CargaProductos.Producto p : lista) {
                sb.append("\u2022 ").append(p.nombre).append(" \u2014 $").append(String.format("%.2f", p.precioConIva).replace('.', ','))
                        .append(" <span style='color:gray'>(").append(p.categoria).append(", IVA ").append((int) Math.round(p.iva * 100)).append("%)</span><br>");
            }
            detalle.add(Ui.texto(sb.toString()));
            detalle.add(Ui.nota("Son de ejemplo: c\u00E1mbiales el precio o b\u00F3rralos en Inventario \u2192 Productos."));
        } else if (IMPORTAR.equals(opcion)) {
            detalle.add(Ui.subtitulo("Importar desde Excel"));
            detalle.add(Ui.texto("1. Descarga la plantilla y \u00E1brela en Excel.<br>"
                    + "2. Llena una fila por producto: <b>nombre</b>, <b>precio</b> (con IVA), categor\u00EDa, c\u00F3digo de barras, IVA (0 o 15) y costo.<br>"
                    + "3. Gu\u00E1rdala como <b>CSV</b> (Archivo \u2192 Guardar como \u2192 CSV) y elige el archivo aqu\u00ED."));
            JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
            fila.setOpaque(false);
            fila.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton plantilla = new JButton("Descargar plantilla");
            plantilla.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    guardarPlantilla();
                }
            });
            JButton elegir = new JButton("Elegir archivo CSV\u2026");
            elegir.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    elegirArchivo();
                }
            });
            fila.add(plantilla);
            fila.add(javax.swing.Box.createHorizontalStrut(10));
            fila.add(elegir);
            detalle.add(fila);
            detalle.add(archivoElegido);
        } else if (VACIO.equals(opcion)) {
            detalle.add(Ui.subtitulo("Sin productos por ahora"));
            detalle.add(Ui.texto("Los agregas en <b>Inventario \u2192 Productos</b>, con su c\u00F3digo de barras, foto y precio."));
        }
        detalle.revalidate();
        detalle.repaint();
    }

    private void guardarPlantilla() {
        JFileChooser s = new JFileChooser();
        s.setSelectedFile(new File("productos-quinde.csv"));
        if (s.showSaveDialog(Asistente.padreDialogos(detalle)) == JFileChooser.APPROVE_OPTION) {
            try {
                // Con BOM, para que Excel lea bien las tildes.
                byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                byte[] texto = CargaProductos.PLANTILLA.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] todo = new byte[bom.length + texto.length];
                System.arraycopy(bom, 0, todo, 0, bom.length);
                System.arraycopy(texto, 0, todo, bom.length, texto.length);
                java.nio.file.Files.write(s.getSelectedFile().toPath(), todo);
            } catch (Exception e) {
                javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(detalle), "No se pudo guardar: " + e.getMessage());
            }
        }
    }

    private void elegirArchivo() {
        JFileChooser s = new JFileChooser();
        s.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Lista de productos (*.csv)", "csv", "txt"));
        if (s.showOpenDialog(Asistente.padreDialogos(detalle)) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            lectura = CargaProductos.leerCsv(s.getSelectedFile());
            StringBuilder sb = new StringBuilder("<b>" + s.getSelectedFile().getName() + "</b>: ")
                    .append(lectura.productos.size()).append(" productos listos para cargar");
            if (!lectura.problemas.isEmpty()) {
                sb.append(" y <span style='color:#C62828'>").append(lectura.problemas.size()).append(" filas con problemas</span>:<br>");
                for (int i = 0; i < Math.min(5, lectura.problemas.size()); i++) {
                    sb.append("\u2022 ").append(lectura.problemas.get(i)).append("<br>");
                }
                if (lectura.problemas.size() > 5) {
                    sb.append("\u2026 y ").append(lectura.problemas.size() - 5).append(" m\u00E1s.");
                }
            } else {
                sb.append(".");
            }
            archivoElegido.setText("<html><body style='width:" + Ui.ANCHO_TEXTO + "px'>" + sb + "</body></html>");
        } catch (Exception e) {
            lectura = null;
            archivoElegido.setText("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    @Override
    String guardar() {
        if (opcion == null && ctx.estado.hecho(id())) {
            return null; // ya se cargaron antes
        }
        if (opcion == null) {
            return "Elige c\u00F3mo quieres empezar con tus productos.";
        }
        if (VACIO.equals(opcion)) {
            return null;
        }
        List<CargaProductos.Producto> lista;
        if (EJEMPLOS.equals(opcion)) {
            lista = CargaProductos.ejemplos(ctx.estado.valor("tipo"));
        } else {
            if (lectura == null || lectura.productos.isEmpty()) {
                return "Elige un archivo CSV con al menos un producto v\u00E1lido.";
            }
            lista = lectura.productos;
        }
        try {
            int[] r = CargaProductos.guardar(ctx.session.getConnection(), lista);
            javax.swing.JOptionPane.showMessageDialog(Asistente.padreDialogos(detalle),
                    r[0] + " productos agregados" + (r[1] > 0 ? " (" + r[1] + " ya exist\u00EDan y se saltaron)." : "."),
                    nombre(), javax.swing.JOptionPane.INFORMATION_MESSAGE);
            opcion = null;
            lectura = null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudieron cargar los productos", e);
            return "No se pudieron cargar los productos: " + e.getMessage();
        }
        return null;
    }

    private int cantidadProductos() {
        try (PreparedStatement ps = ctx.session.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM PRODUCTS WHERE REFERENCE <> 'xxx999'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
