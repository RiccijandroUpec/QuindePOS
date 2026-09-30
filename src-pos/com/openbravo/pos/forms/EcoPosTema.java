package com.openbravo.pos.forms;

import com.formdev.flatlaf.FlatLaf;

import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;

/**
 * Temas modernos de EcoPos (FlatLaf, lib/flatlaf-*.jar): los registra en la
 * lista de apariencias de Configuracion > General, junto a las de siempre
 * (Metal, Substance...), y les aplica el estilo propio de EcoPos: acento
 * verde, esquinas redondeadas y tamanos comodos para pantallas tactiles.
 */
public final class EcoPosTema {

    public static final String CLARO = "com.formdev.flatlaf.FlatLightLaf";
    public static final String OSCURO = "com.formdev.flatlaf.FlatDarkLaf";

    private static final String ACENTO_ECOPOS = "#1B5E3F";
    private static boolean registrado;

    private EcoPosTema() {
    }

    /** Idempotente. Si FlatLaf no esta en el classpath, no hace nada (quedan los temas viejos). */
    public static synchronized void registrar() {
        if (registrado) {
            return;
        }
        registrado = true;
        try {
            Class.forName(CLARO);
        } catch (ClassNotFoundException e) {
            return;
        }
        UIManager.installLookAndFeel("Quinde Claro", CLARO);
        UIManager.installLookAndFeel("Quinde Oscuro", OSCURO);

        Map<String, String> estilo = new HashMap<String, String>();
        estilo.put("@accentColor", ACENTO_ECOPOS);
        estilo.put("Button.arc", "12");
        estilo.put("Component.arc", "10");
        estilo.put("TextComponent.arc", "8");
        estilo.put("Component.focusWidth", "1");
        estilo.put("ScrollBar.width", "16");
        estilo.put("ScrollBar.thumbArc", "999");
        estilo.put("ScrollBar.thumbInsets", "2,2,2,2");
        estilo.put("ScrollBar.showButtons", "false");
        estilo.put("Table.rowHeight", "30");
        estilo.put("Table.showHorizontalLines", "true");
        estilo.put("Table.intercellSpacing", "0,1");
        estilo.put("TabbedPane.tabHeight", "36");
        estilo.put("TitlePane.unifiedBackground", "true");
        FlatLaf.setGlobalExtraDefaults(estilo);
    }

    /** true si la clase de apariencia es uno de los temas FlatLaf. */
    public static boolean esTemaModerno(String claseLaf) {
        return claseLaf != null && claseLaf.startsWith("com.formdev.flatlaf.");
    }
}
