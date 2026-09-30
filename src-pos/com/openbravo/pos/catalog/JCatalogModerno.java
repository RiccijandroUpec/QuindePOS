package com.openbravo.pos.catalog;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.openbravo.basic.BasicException;
import com.openbravo.data.gui.JMessageDialog;
import com.openbravo.data.gui.MessageInf;
import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.forms.AvatarIniciales;
import com.openbravo.pos.forms.DataLogicSales;
import com.openbravo.pos.sales.TaxesLogic;
import com.openbravo.pos.ticket.CategoryInfo;
import com.openbravo.pos.ticket.ProductInfoExt;
import com.openbravo.pos.ticket.TaxInfo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.EventListenerList;

/**
 * Catalogo de la pantalla de venta con el estilo de los POS actuales (solo
 * con los temas modernos; con los viejos se sigue usando {@link JCatalog}):
 * buscador arriba (nombre, codigo de barras o referencia; Enter con un codigo
 * exacto lo agrega directo), categorias como "chips" horizontales, y los
 * productos como tarjetas grandes con foto (o iniciales de color), nombre y
 * precio.
 */
public class JCatalogModerno extends JPanel implements CatalogSelector {

    private static final int MAX_RESULTADOS = 200;
    private static final Dimension TARJETA = new Dimension(150, 128);
    private static final int TAM_FOTO = 56;
    /** Categoria "***" que EcoPos usa internamente (lineas sin producto); no se muestra como chip. */
    private static final String CATEGORIA_INTERNA = "xxx999";

    private final DataLogicSales dlSales;
    private final boolean impuestosIncluidos;
    private final EventListenerList oyentes = new EventListenerList();

    private TaxesLogic impuestos;
    private final Map<String, List<ProductInfoExt>> productosPorCategoria = new LinkedHashMap<String, List<ProductInfoExt>>();
    private final Map<String, List<CategoryInfo>> subcategoriasPorCategoria = new LinkedHashMap<String, List<CategoryInfo>>();
    private final List<ProductInfoExt> todos = new ArrayList<ProductInfoExt>();
    private List<CategoryInfo> raices = new ArrayList<CategoryInfo>();

    private final JTextField buscador = new JTextField();
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
    private final ButtonGroup grupoChips = new ButtonGroup();
    private final JPanel cuadricula = new JPanel(new CuadriculaLayout(TARJETA, 10));
    private final JScrollPane scroll = new JScrollPane(cuadricula);
    private final JLabel titulo = new JLabel();

    /** Categoria que se esta mostrando (null = "Todos"). */
    private String categoriaActual;

    public JCatalogModerno(DataLogicSales dlSales, boolean impuestosIncluidos) {
        this.dlSales = dlSales;
        this.impuestosIncluidos = impuestosIncluidos;

        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        FlatSVGIcon lupa = new FlatSVGIcon("com/openbravo/images/svg/search.svg", 18, 18);
        lupa.setColorFilter(new FlatSVGIcon.ColorFilter().add(Color.BLACK, new Color(0x78909C), new Color(0x90A4AE)));
        buscador.putClientProperty("JTextField.placeholderText", "Buscar producto o c\u00F3digo  (F2)");
        buscador.putClientProperty("JTextField.leadingIcon", lupa);
        buscador.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        buscador.setFont(buscador.getFont().deriveFont(15f));
        buscador.setPreferredSize(new Dimension(360, 40));
        buscador.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filtrar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtrar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filtrar();
            }
        });
        buscador.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                agregarPorCodigoExacto();
            }
        });

        // Atajos: F2 = ir al buscador, Esc (dentro del buscador) = limpiar la busqueda.
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0), "ecopos.buscar");
        getActionMap().put("ecopos.buscar", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buscador.requestFocusInWindow();
                buscador.selectAll();
            }
        });
        buscador.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "ecopos.limpiar");
        buscador.getActionMap().put("ecopos.limpiar", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buscador.setText("");
            }
        });
        buscador.setToolTipText("Buscar (F2). Enter agrega el producto si el c\u00F3digo coincide. Esc limpia.");

        chips.setOpaque(false);
        JScrollPane scrollChips = new JScrollPane(chips, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollChips.setBorder(null);
        scrollChips.setOpaque(false);
        scrollChips.getViewport().setOpaque(false);
        scrollChips.getHorizontalScrollBar().setUnitIncrement(24);

        JPanel arriba = new JPanel(new BorderLayout(10, 0));
        arriba.setOpaque(false);
        arriba.add(buscador, BorderLayout.WEST);
        arriba.add(scrollChips, BorderLayout.CENTER);

        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 13f));
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        titulo.setVisible(false);

        JPanel norte = new JPanel(new BorderLayout(0, 4));
        norte.setOpaque(false);
        norte.add(arriba, BorderLayout.NORTH);
        norte.add(titulo, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);

        cuadricula.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true));
        scroll.getVerticalScrollBar().setUnitIncrement(32);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    // --- CatalogSelector ---------------------------------------------------

    @Override
    public void loadCatalog() throws BasicException {
        impuestos = new TaxesLogic(dlSales.getTaxList().list());
        productosPorCategoria.clear();
        subcategoriasPorCategoria.clear();
        todos.clear();
        raices = dlSales.getRootCategories();
        for (CategoryInfo raiz : raices) {
            cargarCategoria(raiz.getID());
        }

        chips.removeAll();
        for (java.util.Enumeration<AbstractButton> e = grupoChips.getElements(); e.hasMoreElements();) {
            grupoChips.remove(e.nextElement());
        }
        chips.add(crearChip("Todos", null));
        for (CategoryInfo raiz : raices) {
            if (!CATEGORIA_INTERNA.equals(raiz.getID())) {
                chips.add(crearChip(raiz.getName(), raiz.getID()));
            }
        }
        ((JToggleButton) chips.getComponent(0)).setSelected(true);
        chips.revalidate();
        mostrarCategoria(null);
    }

    private void cargarCategoria(String idCategoria) throws BasicException {
        if (productosPorCategoria.containsKey(idCategoria)) {
            return;
        }
        List<ProductInfoExt> productos = dlSales.getProductCatalog(idCategoria);
        productosPorCategoria.put(idCategoria, productos);
        for (ProductInfoExt p : productos) {
            if (!contieneId(todos, p.getID())) {
                todos.add(p);
            }
        }
        List<CategoryInfo> subcategorias = dlSales.getSubcategories(idCategoria);
        subcategoriasPorCategoria.put(idCategoria, subcategorias);
        for (CategoryInfo sub : subcategorias) {
            cargarCategoria(sub.getID());
        }
    }

    @Override
    public void showCatalogPanel(String id) {
        if (id == null) {
            if (!buscador.getText().isEmpty()) {
                return; // no borrar la busqueda del usuario
            }
            mostrarCategoria(categoriaActual);
            return;
        }
        try {
            List<ProductInfoExt> complementos = dlSales.getProductComments(id);
            if (complementos.isEmpty()) {
                return; // producto sin complementos: se queda la vista actual
            }
            ProductInfoExt producto = dlSales.getProductInfo(id);
            mostrarProductos("Complementos de " + (producto == null ? "" : producto.getName()), complementos,
                    new ArrayList<CategoryInfo>());
        } catch (BasicException e) {
            // sin complementos que mostrar: se queda la vista actual
        }
    }

    @Override
    public void setComponentEnabled(boolean valor) {
        buscador.setEnabled(valor);
        for (Component c : chips.getComponents()) {
            c.setEnabled(valor);
        }
        for (Component c : cuadricula.getComponents()) {
            c.setEnabled(valor);
        }
        setEnabled(valor);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void addActionListener(ActionListener l) {
        oyentes.add(ActionListener.class, l);
    }

    @Override
    public void removeActionListener(ActionListener l) {
        oyentes.remove(ActionListener.class, l);
    }

    // --- vistas ------------------------------------------------------------

    private void mostrarCategoria(String idCategoria) {
        categoriaActual = idCategoria;
        if (idCategoria == null) {
            mostrarProductos(null, todos, new ArrayList<CategoryInfo>());
        } else {
            try {
                cargarCategoria(idCategoria);
            } catch (BasicException e) {
                JMessageDialog.showMessage(this, new MessageInf(MessageInf.SGN_WARNING, AppLocal.getIntString("message.notactive"), e));
                return;
            }
            mostrarProductos(null, productosPorCategoria.get(idCategoria), subcategoriasPorCategoria.get(idCategoria));
        }
    }

    private void filtrar() {
        String texto = buscador.getText().trim().toLowerCase(Locale.ROOT);
        if (texto.isEmpty()) {
            mostrarCategoria(categoriaActual);
            return;
        }
        List<ProductInfoExt> encontrados = new ArrayList<ProductInfoExt>();
        for (ProductInfoExt p : todos) {
            if (coincide(p.getName(), texto) || coincide(p.getCode(), texto) || coincide(p.getReference(), texto)) {
                encontrados.add(p);
                if (encontrados.size() >= MAX_RESULTADOS) {
                    break;
                }
            }
        }
        mostrarProductos(encontrados.isEmpty() ? "Sin resultados para \u201C" + buscador.getText().trim() + "\u201D"
                : encontrados.size() + " resultado(s)", encontrados, new ArrayList<CategoryInfo>());
    }

    private void agregarPorCodigoExacto() {
        String texto = buscador.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        ProductInfoExt unico = null;
        for (ProductInfoExt p : todos) {
            if (texto.equalsIgnoreCase(p.getCode()) || texto.equalsIgnoreCase(p.getReference())) {
                unico = p;
                break;
            }
        }
        if (unico == null) {
            try {
                unico = dlSales.getProductInfoByCode(texto);
            } catch (BasicException e) {
                unico = null;
            }
        }
        if (unico == null && cuadricula.getComponentCount() == 1 && cuadricula.getComponent(0) instanceof JButton) {
            ((JButton) cuadricula.getComponent(0)).doClick();
            buscador.setText("");
            return;
        }
        if (unico != null) {
            disparar(unico);
            buscador.setText("");
        }
    }

    private void mostrarProductos(String encabezado, List<ProductInfoExt> productos, List<CategoryInfo> subcategorias) {
        titulo.setText(encabezado == null ? "" : encabezado);
        titulo.setVisible(encabezado != null);
        cuadricula.removeAll();
        if (subcategorias != null) {
            for (final CategoryInfo sub : subcategorias) {
                JButton carpeta = crearTarjeta(sub.getName(), "Categor\u00EDa", sub.getImage(), true);
                carpeta.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        mostrarCategoria(sub.getID());
                    }
                });
                cuadricula.add(carpeta);
            }
        }
        if (productos != null) {
            for (final ProductInfoExt p : productos) {
                JButton tarjeta = crearTarjeta(p.getName(), precio(p), p.getImage(), false);
                tarjeta.setToolTipText(p.getTextTip() == null || p.getTextTip().isEmpty() ? p.getName() : p.getTextTip());
                tarjeta.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        disparar(p);
                    }
                });
                cuadricula.add(tarjeta);
            }
        }
        if (cuadricula.getComponentCount() == 0 && encabezado == null) {
            JLabel vacio = new JLabel("<html><center>Todav\u00EDa no hay productos en esta categor\u00EDa.<br>"
                    + "Agr\u00E9galos en Almac\u00E9n &gt; Productos.</center></html>", SwingConstants.CENTER);
            vacio.setForeground(new Color(0x78909C));
            vacio.setPreferredSize(new Dimension(TARJETA.width * 3, TARJETA.height));
            cuadricula.add(vacio);
        }
        cuadricula.revalidate();
        cuadricula.repaint();
        scroll.getVerticalScrollBar().setValue(0);
    }

    private String precio(ProductInfoExt p) {
        if (impuestosIncluidos && impuestos != null) {
            TaxInfo tax = impuestos.getTaxInfo(p.getTaxCategoryID());
            return p.printPriceSellTax(tax);
        }
        return p.printPriceSell();
    }

    private JButton crearTarjeta(String nombre, String detalle, BufferedImage foto, boolean esCarpeta) {
        String nombreHtml = escapar(nombre == null ? "" : nombre);
        JButton b = new JButton("<html><center><b>" + nombreHtml + "</b><br><span style='color:"
                + (esCarpeta ? "#78909C" : (com.formdev.flatlaf.FlatLaf.isLafDark() ? "#81C784" : "#2E7D32")) + "'>" + escapar(detalle) + "</span></center></html>");
        b.setIcon(foto != null ? new ImageIcon(escalar(foto)) : new AvatarIniciales(nombre, TAM_FOTO - 8));
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setIconTextGap(6);
        b.setFocusable(false);
        b.setMargin(new Insets(6, 4, 6, 4));
        b.setFont(b.getFont().deriveFont(12.5f));
        b.setPreferredSize(TARJETA);
        return b;
    }

    private static Image escalar(BufferedImage foto) {
        double escala = Math.min((double) TAM_FOTO / foto.getWidth(), (double) TAM_FOTO / foto.getHeight());
        int ancho = Math.max(1, (int) Math.round(foto.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(foto.getHeight() * escala));
        return foto.getScaledInstance(ancho, alto, Image.SCALE_SMOOTH);
    }

    private JToggleButton crearChip(String texto, final String idCategoria) {
        JToggleButton chip = new JToggleButton(texto);
        chip.putClientProperty("JButton.buttonType", "roundRect");
        chip.setFocusable(false);
        chip.setFont(chip.getFont().deriveFont(Font.BOLD, 13f));
        chip.setMargin(new Insets(6, 14, 6, 14));
        chip.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buscador.setText("");
                mostrarCategoria(idCategoria);
            }
        });
        grupoChips.add(chip);
        return chip;
    }

    private void disparar(ProductInfoExt producto) {
        ActionEvent evento = new ActionEvent(producto, ActionEvent.ACTION_PERFORMED, producto.getID());
        for (EventListener l : oyentes.getListeners(ActionListener.class)) {
            ((ActionListener) l).actionPerformed(evento);
        }
    }

    private static boolean coincide(String valor, String textoMinusculas) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(textoMinusculas);
    }

    private static boolean contieneId(List<ProductInfoExt> lista, String id) {
        for (ProductInfoExt p : lista) {
            if (p.getID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Cuadricula que acomoda tarjetas de tamano fijo en tantas columnas como quepan (y crece hacia abajo). */
    private static final class CuadriculaLayout implements LayoutManager {
        private final Dimension celda;
        private final int espacio;

        CuadriculaLayout(Dimension celda, int espacio) {
            this.celda = celda;
            this.espacio = espacio;
        }

        private int columnas(Container padre) {
            Insets in = padre.getInsets();
            int ancho = padre.getWidth();
            if (ancho <= 0 && padre.getParent() != null) {
                ancho = padre.getParent().getWidth();
            }
            ancho -= in.left + in.right;
            return Math.max(1, (ancho + espacio) / (celda.width + espacio));
        }

        @Override
        public void layoutContainer(Container padre) {
            Insets in = padre.getInsets();
            int cols = columnas(padre);
            for (int i = 0; i < padre.getComponentCount(); i++) {
                Component c = padre.getComponent(i);
                Dimension tam = c instanceof JLabel ? c.getPreferredSize() : celda;
                int fila = i / cols;
                int col = i % cols;
                c.setBounds(in.left + col * (celda.width + espacio), in.top + fila * (celda.height + espacio), tam.width, tam.height);
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container padre) {
            Insets in = padre.getInsets();
            int cols = columnas(padre);
            int filas = (padre.getComponentCount() + cols - 1) / cols;
            return new Dimension(in.left + in.right + cols * (celda.width + espacio),
                    in.top + in.bottom + Math.max(1, filas) * (celda.height + espacio));
        }

        @Override
        public Dimension minimumLayoutSize(Container padre) {
            return new Dimension(celda.width, celda.height);
        }

        @Override
        public void addLayoutComponent(String nombre, Component c) {
        }

        @Override
        public void removeLayoutComponent(Component c) {
        }
    }
}
