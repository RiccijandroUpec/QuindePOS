package com.openbravo.pos.forms;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Estilo moderno de EcoPos aplicado de forma global, sin tocar los
 * formularios generados por NetBeans: cada vez que un boton aparece en
 * pantalla, si su icono PNG viejo (o su nombre, para los botones de
 * Ticket.Buttons) esta en la tabla de abajo, se cambia por un icono SVG
 * nitido (Tabler Icons, MIT - images/svg/) y, en las acciones principales,
 * se le agrega un texto corto. Solo con los temas modernos (EcoPosTema); con
 * los viejos no hace nada.
 */
public final class EcoPosEstilo {

    private static final String YA_ESTILIZADO = "ecopos.estilo";
    private static final String CARPETA_SVG = "com/openbravo/images/svg/";

    private static final Color[] NEUTRO = {new Color(0x37474F), new Color(0xCFD8DC)};
    private static final Color[] VERDE = {new Color(0x1B5E3F), new Color(0xA8E6C1)};
    private static final Color[] ROJO = {new Color(0xC62828), new Color(0xEF5350)};

    private static final Map<String, Mapeo> POR_ICONO = new HashMap<String, Mapeo>();
    private static final Map<String, Mapeo> POR_NOMBRE = new HashMap<String, Mapeo>();
    private static final Map<String, String> TECLAS = new HashMap<String, String>();

    static {
        for (int i = 0; i <= 9; i++) {
            TECLAS.put("btn" + i + ".png", String.valueOf(i));
            TECLAS.put("btn" + i + "a.png", String.valueOf(i));
        }
        TECLAS.put("btn00.png", "00");
        TECLAS.put("btndot.png", ".");
        TECLAS.put("btnce.png", "CE");
        TECLAS.put("btnmult.png", "\u00D7");
        TECLAS.put("btnminus.png", "\u2212");
        TECLAS.put("btnplus.png", "+");
        TECLAS.put("btnequals.png", "=");

        // Barra superior de la venta
        icono("customer_add_sml.png", "user-plus", NEUTRO, "Nuevo cliente");
        icono("customer_sml.png", "user", NEUTRO, "Cliente");
        icono("sale_split_sml.png", "arrows-split", NEUTRO, "Dividir");
        icono("sale_new.png", "file-plus", VERDE, "Nueva");
        icono("sale_delete.png", "trash", ROJO, "Borrar");
        icono("sale_pending.png", "list-details", NEUTRO, null, "Ventas en espera");
        icono("printer24.png", "printer", NEUTRO, null);
        icono("scale.png", "scale", NEUTRO, null);
        // Columna junto a las lineas del ticket
        icono("1uparrow.png", "chevron-up", NEUTRO, null);
        icono("1downarrow.png", "chevron-down", NEUTRO, null);
        icono("editdelete.png", "circle-minus", ROJO, null);
        icono("search32.png", "search", NEUTRO, null);
        icono("search24.png", "search", NEUTRO, null);
        icono("sale_editline.png", "pencil", NEUTRO, null);
        icono("attributes.png", "tag", NEUTRO, null);
        icono("barcode.png", "barcode", NEUTRO, null);
        // Comunes
        icono("exit.png", "power", ROJO, null);
        icono("logout.png", "logout", NEUTRO, null);
        icono("password.png", "settings", NEUTRO, null);
        icono("cancel.png", "x", NEUTRO, null);
        icono("fileclose.png", "x", NEUTRO, null);
        icono("ok.png", "check", VERDE, null);
        icono("inbox.png", "arrow-back-up", NEUTRO, null);
        icono("display.png", "layout-sidebar-left-expand", NEUTRO, null);
        // Resto de la aplicacion (menu lateral, mantenimiento, informes, dialogos):
        // mismo icono moderno, sin texto extra.
        String[][] generales = {
            {"reports.png", "report-analytics"}, {"date.png", "calendar"}, {"reload.png", "refresh"},
            {"chart.png", "chart-bar"}, {"1rightarrow.png", "chevron-right"}, {"1leftarrow.png", "chevron-left"},
            {"2rightarrow.png", "chevrons-right"}, {"2leftarrow.png", "chevrons-left"}, {"2uparrow.png", "chevrons-up"},
            {"fileopen.png", "folder-open"}, {"tables.png", "armchair"}, {"products.png", "package"},
            {"products24.png", "package"}, {"package.png", "package"}, {"printer.png", "printer"},
            {"yast_printer.png", "printer"}, {"printer24_off.png", "printer-off"}, {"filesave.png", "device-floppy"},
            {"database.png", "database"}, {"customer.png", "user"}, {"user.png", "user"}, {"user_sml.png", "user"},
            {"users.png", "users"}, {"customerpay.png", "user-dollar"}, {"cash.png", "cash"},
            {"calculator.png", "calculator"}, {"bookmark.png", "bookmark"}, {"voucher.png", "ticket"},
            {"viewmag-.png", "zoom-out"}, {"restaurant_floor_sml.png", "layout-grid"}, {"pay.png", "cash-banknote"},
            {"payments.png", "cash-banknote"}, {"location.png", "map-pin"}, {"floors.png", "building"},
            {"encrypted.png", "lock"}, {"editnew.png", "file-plus"}, {"edit_group.png", "pencil-plus"},
            {"edit_group_sm.png", "pencil"}, {"edit.png", "pencil"}, {"configuration.png", "settings"},
            {"cheque.png", "file-dollar"}, {"ccard.png", "credit-card"}, {"category.png", "category"},
            {"subcategory.png", "folder"}, {"wallet.png", "wallet"}, {"utilities.png", "tool"},
            {"maintain.png", "tool"}, {"timer.png", "clock"}, {"sysadmin.png", "shield-lock"},
            {"roles.png", "user-shield"}, {"resources.png", "files"}, {"stockmaint.png", "building-warehouse"},
            {"stockdiary.png", "notebook"}, {"sort_incr.png", "sort-ascending"}, {"sales.png", "shopping-cart"},
            {"sale.png", "shopping-cart"}, {"saleedit.png", "receipt"}, {"refundit.png", "receipt-refund"},
            {"receive.png", "truck-delivery"}, {"run_script.png", "player-play"}, {"movetable.png", "arrows-move"},
            {"mime.png", "photo"}, {"mime2.png", "photo"}, {"mime3.png", "photo"}, {"camera.png", "camera"},
            {"leaves.png", "leaf"}, {"import.png", "file-import"}, {"coffee.png", "coffee"},
            {"bank.png", "building-bank"}, {"auxiliary.png", "puzzle"}, {"customer_add.png", "user-plus"},
            {"openbravo.png", "puzzle"}, {"ark2.png", "folder"}, {"atlantikdesigner.png", "adjustments-horizontal"},
        };
        for (String[] g : generales) {
            if (!POR_ICONO.containsKey(g[0])) {
                icono(g[0], g[1], NEUTRO, null);
            }
        }
        // Botones de Ticket.Buttons (se identifican por su "key")
        nombre("button.totaldiscount", "percentage", NEUTRO, "Descuento");
        nombre("button.print", "printer", NEUTRO, "Imprimir");
        nombre("button.opendrawer", "cash-register", NEUTRO, "Caj\u00F3n");
        nombre("button.sriinvoiceon", "circle-check", VERDE, null);
        nombre("button.sriinvoiceoff", "circle-x", NEUTRO, null);
    }

    private EcoPosEstilo() {
    }

    /** Llamar una vez al arrancar, despues de fijar la apariencia. */
    public static void instalar() {
        if (!EcoPosTema.esTemaModerno(UIManager.getLookAndFeel().getClass().getName())) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent evento) {
                if (!(evento instanceof HierarchyEvent)) {
                    return;
                }
                HierarchyEvent he = (HierarchyEvent) evento;
                if ((he.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                        && he.getComponent() instanceof AbstractButton
                        && he.getComponent().isShowing()) {
                    estilizar((AbstractButton) he.getComponent());
                }
            }
        }, AWTEvent.HIERARCHY_EVENT_MASK);
    }

    static void estilizar(AbstractButton boton) {
        if (boton.getClientProperty(YA_ESTILIZADO) != null) {
            return;
        }
        boton.putClientProperty(YA_ESTILIZADO, Boolean.TRUE);

        Icon iconoActual = boton.getIcon();
        Mapeo mapeo = null;
        if (iconoActual instanceof ImageIcon) {
            String descripcion = ((ImageIcon) iconoActual).getDescription();
            if (descripcion != null) {
                mapeo = POR_ICONO.get(descripcion.substring(descripcion.lastIndexOf('/') + 1));
            }
        }
        if (iconoActual instanceof ImageIcon && ((ImageIcon) iconoActual).getDescription() != null) {
            String descripcion = ((ImageIcon) iconoActual).getDescription();
            String tecla = TECLAS.get(descripcion.substring(descripcion.lastIndexOf('/') + 1));
            if (tecla != null) {
                estilizarTecla(boton, tecla);
                return;
            }
        }
        if (mapeo == null && boton.getName() != null) {
            mapeo = POR_NOMBRE.get(boton.getName());
        }
        if (mapeo == null) {
            return;
        }

        int alto = iconoActual == null ? 24 : iconoActual.getIconHeight();
        int tam = Math.max(18, Math.min(26, Math.round(alto * 0.8f)));
        FlatSVGIcon svg = new FlatSVGIcon(CARPETA_SVG + mapeo.svg + ".svg", tam, tam);
        svg.setColorFilter(new FlatSVGIcon.ColorFilter().add(Color.BLACK, mapeo.colores[0], mapeo.colores[1]));
        boton.setIcon(svg);
        boton.setDisabledIcon(null);
        boton.setPressedIcon(null);
        boton.setRolloverIcon(null);

        if (mapeo.ayuda != null) {
            boton.setToolTipText(mapeo.ayuda);
        }

        String texto = boton.getText();
        boolean sinTexto = texto == null || texto.trim().isEmpty();
        // El texto extra solo en la pantalla de venta (y en los botones de
        // Ticket.Buttons): en las barras de mantenimiento todos son solo icono.
        boolean enVenta = boton.getName() != null && POR_NOMBRE.containsKey(boton.getName())
                || javax.swing.SwingUtilities.getAncestorOfClass(com.openbravo.pos.sales.JPanelTicket.class, boton) != null;
        if (mapeo.texto != null && sinTexto && enVenta) {
            boton.setText(mapeo.texto);
            texto = mapeo.texto;
        }
        // Texto real (no un indicador de 1 caracter como el "*" de Pendientes):
        // al lado del icono, y liberar el ancho fijo que los formularios
        // pensaron solo para el icono (si no, queda cortado: "E...").
        if (texto != null && texto.trim().length() > 1) {
            int altoOriginal = boton.getPreferredSize().height;
            boton.setIconTextGap(6);
            boton.setHorizontalTextPosition(SwingConstants.TRAILING);
            boton.setVerticalTextPosition(SwingConstants.CENTER);
            boton.setPreferredSize(null);
            boton.setMinimumSize(null);
            boton.setMaximumSize(null);
            java.awt.Dimension natural = boton.getPreferredSize();
            java.awt.Dimension tactil = new java.awt.Dimension(natural.width + 10, Math.max(altoOriginal, 40));
            boton.setPreferredSize(tactil);
            boton.setMinimumSize(tactil);
            boton.setMaximumSize(tactil);
            if (boton.getParent() != null) {
                boton.getParent().revalidate();
            }
        }
    }

    private static final Map<String, String> FORMAS_PAGO = new HashMap<String, String>();

    static {
        FORMAS_PAGO.put("payment.cash", "cash-banknote");
        FORMAS_PAGO.put("refund.cash", "cash-banknote");
        FORMAS_PAGO.put("payment.cheque", "file-dollar");
        FORMAS_PAGO.put("refund.cheque", "file-dollar");
        FORMAS_PAGO.put("payment.paper", "ticket");
        FORMAS_PAGO.put("refund.paper", "ticket");
        FORMAS_PAGO.put("payment.magcard", "credit-card");
        FORMAS_PAGO.put("refund.magcard", "credit-card");
        FORMAS_PAGO.put("payment.free", "gift");
        FORMAS_PAGO.put("payment.debt", "user-dollar");
        FORMAS_PAGO.put("payment.bank", "arrows-exchange");
        FORMAS_PAGO.put("payment.deuna", "qrcode");
    }

    /** Icono de una pestana de forma de pago: SVG moderno con el tema nuevo, o el PNG de siempre. */
    public static Icon iconoFormaPago(String clave, java.net.URL pngViejo) {
        String svg = FORMAS_PAGO.get(clave);
        if (svg != null && EcoPosTema.esTemaModerno(UIManager.getLookAndFeel().getClass().getName())) {
            FlatSVGIcon icono = new FlatSVGIcon(CARPETA_SVG + svg + ".svg", 26, 26);
            icono.setColorFilter(new FlatSVGIcon.ColorFilter().add(Color.BLACK, NEUTRO[0], NEUTRO[1]));
            return icono;
        }
        return new ImageIcon(pngViejo);
    }

    /** Teclado numerico: la imagen de cada tecla se cambia por texto nitido que sigue al tema. */
    private static void estilizarTecla(AbstractButton boton, String tecla) {
        boton.setIcon(null);
        boton.setDisabledIcon(null);
        boton.setPressedIcon(null);
        boton.setRolloverIcon(null);
        boton.setHorizontalTextPosition(SwingConstants.CENTER);
        boton.setVerticalTextPosition(SwingConstants.CENTER);
        if ("=".equals(tecla)) {
            boton.setText("<html><center><span style='font-size:18pt'>=</span><br><span style='font-size:10pt'>Cobrar</span></center></html>");
            boton.setFont(boton.getFont().deriveFont(java.awt.Font.BOLD, 12f));
            boton.setMargin(new java.awt.Insets(2, 2, 2, 2));
            boton.setBackground(VERDE[0]);
            boton.setForeground(Color.WHITE);
            boton.setToolTipText("Cobrar esta venta");
            return;
        }
        boton.setText(tecla);
        boton.setFont(boton.getFont().deriveFont(java.awt.Font.PLAIN, tecla.length() > 1 ? 18f : 24f));
        if ("CE".equals(tecla)) {
            boton.setForeground(ROJO[com.formdev.flatlaf.FlatLaf.isLafDark() ? 1 : 0]);
        } else if ("+".equals(tecla) || "\u2212".equals(tecla) || "\u00D7".equals(tecla)) {
            boton.setForeground(VERDE[com.formdev.flatlaf.FlatLaf.isLafDark() ? 1 : 0]);
        }
    }

    private static void icono(String png, String svg, Color[] colores, String texto) {
        icono(png, svg, colores, texto, null);
    }

    private static void icono(String png, String svg, Color[] colores, String texto, String ayuda) {
        POR_ICONO.put(png, new Mapeo(svg, colores, texto, ayuda));
    }

    private static void nombre(String clave, String svg, Color[] colores, String texto) {
        POR_NOMBRE.put(clave, new Mapeo(svg, colores, texto, null));
    }

    private static final class Mapeo {
        final String svg;
        final Color[] colores;
        final String texto;
        final String ayuda;

        Mapeo(String svg, Color[] colores, String texto, String ayuda) {
            this.svg = svg;
            this.colores = colores;
            this.texto = texto;
            this.ayuda = ayuda;
        }
    }
}
