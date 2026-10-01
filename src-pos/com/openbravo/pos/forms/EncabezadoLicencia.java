package com.openbravo.pos.forms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cambia el aviso de licencia que traian los recursos (en ingles, con el nombre del
 * programa original) por el de Quinde POS en espanol. La GPL exige conservar el aviso de
 * copyright y de licencia: se conservan los dos (copyright original incluido), solo cambia
 * la redaccion. El resto del recurso no se toca.
 *
 * Tambien se puede ejecutar sobre una carpeta (main) para actualizar los archivos fuente.
 */
public final class EncabezadoLicencia {

    static final String[] TEXTO = {
        "Quinde POS - punto de venta libre para Ecuador",
        "Copyright (c) 2009-2014 uniCenta, 2026 Quinde POS",
        "Este archivo es software libre: puede redistribuirlo y/o modificarlo bajo los",
        "t\u00E9rminos de la Licencia P\u00FAblica General GNU (GPL), versi\u00F3n 3 o posterior.",
        "Se distribuye SIN NINGUNA GARANT\u00CDA. Vea <https://www.gnu.org/licenses/>.",
    };

    /** Otras frases de los encabezados que nombraban al programa original. */
    private static final String[][] FRASES = {
        {"This file controls all Menu Items in uniCenta oPOS", "Este archivo define las opciones del men\u00FA de Quinde POS"},
        {"has full access to all uniCenta oPOS", "has full access to all Quinde POS"},
        {"Copyright (c) 2009-2014 uniCenta, 2026 EcoPos</text>", "Quinde POS - punto de venta libre</text>"},
    };

    private EncabezadoLicencia() {
    }

    /** El texto con el aviso nuevo; el mismo texto si no tiene el aviso viejo. */
    public static String reemplazar(String texto) {
        if (texto == null) {
            return null;
        }
        String original = texto;
        for (String[] f : FRASES) {
            texto = texto.replace(f[0], f[1]);
        }
        String r = reemplazarAviso(texto);
        return r.equals(original) ? original : r;
    }

    private static String reemplazarAviso(String texto) {
        String nl = texto.contains("\r\n") ? "\r\n" : "\n";
        List<String> lineas = new ArrayList<>(Arrays.asList(texto.split("\r?\n", -1)));
        int limite = Math.min(lineas.size(), 40);
        int inicio = -1;
        for (int i = 0; i < limite; i++) {
            String l = lineas.get(i).toLowerCase();
            if (l.contains("unicenta opos") && (l.contains("point of sale") || l.contains("this file is part"))) {
                inicio = i;
                break;
            }
        }
        if (inicio < 0) {
            return texto;
        }
        int fin = -1;
        for (int i = inicio; i < Math.min(lineas.size(), inicio + 30); i++) {
            if (lineas.get(i).contains("gnu.org/licenses")) {
                fin = i;
                break;
            }
        }
        if (fin < 0) {
            return texto;
        }
        List<String> nuevo;
        int desde;
        int hasta;
        if (lineas.get(inicio).trim().startsWith("//")) {
            // Comentarios de script: se incluyen las lineas "//" vacias de alrededor y la de asteriscos.
            desde = inicio;
            while (desde > 0 && esRelleno(lineas.get(desde - 1))) {
                desde--;
            }
            hasta = fin;
            if (hasta + 1 < lineas.size() && esRelleno(lineas.get(hasta + 1))) {
                hasta++;
            }
            nuevo = new ArrayList<>();
            for (String t : TEXTO) {
                nuevo.add("// " + t);
            }
            nuevo.add("// " + repetir('*', 76));
        } else {
            // Comentario XML <!-- ... -->: se reemplaza el comentario entero.
            desde = -1;
            for (int i = inicio; i >= 0; i--) {
                if (lineas.get(i).contains("<!--")) {
                    desde = i;
                    break;
                }
            }
            hasta = -1;
            for (int i = fin; i < lineas.size(); i++) {
                if (lineas.get(i).contains("-->")) {
                    hasta = i;
                    break;
                }
            }
            if (desde < 0 || hasta < 0 || !lineas.get(desde).trim().startsWith("<!--") || !lineas.get(hasta).trim().endsWith("-->")) {
                return texto;
            }
            nuevo = new ArrayList<>();
            nuevo.add("<!--");
            for (String t : TEXTO) {
                nuevo.add("    " + t);
            }
            nuevo.add("-->");
        }
        List<String> resultado = new ArrayList<>(lineas.subList(0, desde));
        resultado.addAll(nuevo);
        resultado.addAll(lineas.subList(hasta + 1, lineas.size()));
        return String.join(nl, resultado);
    }

    /** "//", "// *****" o "//    " (lineas de relleno del aviso viejo). */
    private static boolean esRelleno(String linea) {
        String t = linea.trim();
        return t.startsWith("//") && t.substring(2).replace("*", "").trim().isEmpty();
    }

    private static String repetir(char c, int n) {
        char[] cs = new char[n];
        Arrays.fill(cs, c);
        return new String(cs);
    }

    /** Uso: java ... EncabezadoLicencia carpeta  (actualiza los .xml y .txt de la carpeta). */
    public static void main(String[] args) throws Exception {
        File[] archivos = new File(args[0]).listFiles();
        if (archivos == null) {
            return;
        }
        int n = 0;
        for (File f : archivos) {
            String nombre = f.getName();
            if (!f.isFile() || !(nombre.endsWith(".xml") || nombre.endsWith(".txt"))) {
                continue;
            }
            String texto = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String nuevo = reemplazar(texto);
            if (!nuevo.equals(texto)) {
                Files.write(f.toPath(), nuevo.getBytes(StandardCharsets.UTF_8));
                n++;
            }
        }
        System.out.println("Archivos actualizados: " + n);
    }
}
