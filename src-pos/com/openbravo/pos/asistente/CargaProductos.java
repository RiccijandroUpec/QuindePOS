package com.openbravo.pos.asistente;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carga de productos para el asistente: productos de ejemplo segun el tipo de negocio e
 * importacion desde un CSV (Excel -> Guardar como -> CSV). Los precios se escriben con IVA
 * incluido, como los piensa el dueno; aqui se calcula el precio sin IVA que guarda la base.
 */
final class CargaProductos {

    /** Una fila a cargar. */
    static final class Producto {
        final String nombre;
        final double precioConIva;
        final String categoria;
        final String codigo;
        final double iva;
        final double costo;
        final boolean cocina;

        Producto(String nombre, double precioConIva, String categoria, String codigo, double iva, double costo, boolean cocina) {
            this.nombre = nombre;
            this.precioConIva = precioConIva;
            this.categoria = categoria;
            this.codigo = codigo;
            this.iva = iva;
            this.costo = costo;
            this.cocina = cocina;
        }
    }

    /** Resultado de leer un CSV: productos validos y los problemas de cada fila. */
    static final class Lectura {
        final List<Producto> productos = new ArrayList<>();
        final List<String> problemas = new ArrayList<>();
    }

    static final String PLANTILLA = "nombre;precio;categoria;codigo;iva;costo\n"
            + "Coca-Cola 500 ml;0,85;Bebidas;7861234500012;15;0,55\n"
            + "Arroz 1 kg;1,20;Abarrotes;;0;0,95\n"
            + "Pan de yuca;0,35;Panader\u00EDa;;0;\n";

    private CargaProductos() {
    }

    // ------------------------------------------------------------------ ejemplos

    static List<Producto> ejemplos(String tipo) {
        List<Producto> l = new ArrayList<>();
        if (PasoTipo.RESTAURANTE.equals(tipo)) {
            l.add(new Producto("Almuerzo del d\u00EDa", 3.50, "Platos", null, 0.15, 0, true));
            l.add(new Producto("Seco de pollo", 5.00, "Platos", null, 0.15, 0, true));
            l.add(new Producto("Encebollado", 4.50, "Platos", null, 0.15, 0, true));
            l.add(new Producto("Ceviche de camar\u00F3n", 7.50, "Platos", null, 0.15, 0, true));
            l.add(new Producto("Hornado", 6.00, "Platos", null, 0.15, 0, true));
            l.add(new Producto("Jugo natural", 1.50, "Bebidas", null, 0.15, 0, true));
            l.add(new Producto("Cola 500 ml", 1.00, "Bebidas", null, 0.15, 0, false));
            l.add(new Producto("Agua 500 ml", 0.75, "Bebidas", null, 0.15, 0, false));
            l.add(new Producto("Postre del d\u00EDa", 2.00, "Postres", null, 0.15, 0, true));
        } else if (PasoTipo.CAFETERIA.equals(tipo)) {
            l.add(new Producto("Caf\u00E9 americano", 1.50, "Bebidas calientes", null, 0.15, 0, true));
            l.add(new Producto("Capuchino", 2.25, "Bebidas calientes", null, 0.15, 0, true));
            l.add(new Producto("Chocolate caliente", 2.00, "Bebidas calientes", null, 0.15, 0, true));
            l.add(new Producto("Jugo natural", 2.00, "Bebidas fr\u00EDas", null, 0.15, 0, true));
            l.add(new Producto("Agua 500 ml", 0.75, "Bebidas fr\u00EDas", null, 0.15, 0, false));
            l.add(new Producto("Pan de yuca", 0.35, "Panader\u00EDa", null, 0.15, 0, false));
            l.add(new Producto("Empanada de viento", 0.90, "Panader\u00EDa", null, 0.15, 0, true));
            l.add(new Producto("Humita", 1.50, "Panader\u00EDa", null, 0.15, 0, true));
            l.add(new Producto("Sanduche de jam\u00F3n y queso", 2.50, "Sanduches", null, 0.15, 0, true));
        } else if (PasoTipo.TIENDA.equals(tipo)) {
            l.add(new Producto("Arroz 1 kg", 1.20, "Abarrotes", null, 0, 0, false));
            l.add(new Producto("Az\u00FAcar 1 kg", 1.10, "Abarrotes", null, 0, 0, false));
            l.add(new Producto("Aceite 1 L", 3.75, "Abarrotes", null, 0, 0, false));
            l.add(new Producto("Leche 1 L", 1.05, "L\u00E1cteos", null, 0, 0, false));
            l.add(new Producto("Queso fresco", 3.20, "L\u00E1cteos", null, 0, 0, false));
            l.add(new Producto("Cola 500 ml", 0.85, "Bebidas", null, 0.15, 0, false));
            l.add(new Producto("Agua 600 ml", 0.60, "Bebidas", null, 0.15, 0, false));
            l.add(new Producto("Galletas", 0.50, "Snacks", null, 0.15, 0, false));
            l.add(new Producto("Jab\u00F3n de tocador", 0.90, "Limpieza", null, 0.15, 0, false));
            l.add(new Producto("Papel higi\u00E9nico x4", 2.40, "Limpieza", null, 0.15, 0, false));
        } else {
            l.add(new Producto("Producto general", 1.00, "General", null, 0.15, 0, false));
            l.add(new Producto("Servicio", 10.00, "General", null, 0.15, 0, false));
        }
        return l;
    }

    // ------------------------------------------------------------------ CSV

    /** Lee un CSV con ; o , como separador y la primera fila de titulos (nombre, precio, ...). */
    static Lectura leerCsv(File archivo) throws Exception {
        Lectura r = new Lectura();
        List<String> lineas = new ArrayList<>();
        byte[] bytes = java.nio.file.Files.readAllBytes(archivo.toPath());
        String texto = decodificar(bytes);
        for (String l : texto.split("\\r?\\n")) {
            if (!l.trim().isEmpty()) {
                lineas.add(l);
            }
        }
        if (lineas.isEmpty()) {
            r.problemas.add("El archivo est\u00E1 vac\u00EDo.");
            return r;
        }
        char sep = lineas.get(0).indexOf(';') >= 0 ? ';' : ',';
        String[] titulos = partir(lineas.get(0), sep);
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < titulos.length; i++) {
            col.put(normalizar(titulos[i]), i);
        }
        Integer cNombre = buscar(col, "nombre", "producto", "descripcion");
        Integer cPrecio = buscar(col, "precio", "pvp", "precio de venta", "precioventa");
        if (cNombre == null || cPrecio == null) {
            r.problemas.add("La primera fila debe tener los t\u00EDtulos: nombre;precio;categoria;codigo;iva;costo "
                    + "(al menos nombre y precio).");
            return r;
        }
        Integer cCategoria = buscar(col, "categoria");
        Integer cCodigo = buscar(col, "codigo", "codigo de barras", "codigobarras");
        Integer cIva = buscar(col, "iva", "impuesto");
        Integer cCosto = buscar(col, "costo", "precio de compra", "preciocompra");
        for (int n = 1; n < lineas.size(); n++) {
            String[] c = partir(lineas.get(n), sep);
            String fila = "Fila " + (n + 1) + ": ";
            String nombre = celda(c, cNombre);
            if (nombre.isEmpty()) {
                r.problemas.add(fila + "falta el nombre.");
                continue;
            }
            Double precio = numero(celda(c, cPrecio));
            if (precio == null || precio < 0) {
                r.problemas.add(fila + "el precio de \u201C" + nombre + "\u201D no es un n\u00FAmero.");
                continue;
            }
            Double iva = cIva == null || celda(c, cIva).isEmpty() ? Double.valueOf(15) : numero(celda(c, cIva).replace("%", ""));
            if (iva == null || !PasoCaja.esTarifaSri(iva / 100.0)) {
                r.problemas.add(fila + "el IVA de \u201C" + nombre + "\u201D debe ser 0 o 15.");
                continue;
            }
            Double costo = cCosto == null || celda(c, cCosto).isEmpty() ? Double.valueOf(0) : numero(celda(c, cCosto));
            String categoria = celda(c, cCategoria);
            r.productos.add(new Producto(nombre, precio, categoria.isEmpty() ? "General" : categoria,
                    celda(c, cCodigo), iva / 100.0, costo == null ? 0 : costo, false));
        }
        return r;
    }

    /** UTF-8 (con o sin BOM) o, si no lo es, Windows-1252 (lo que guarda Excel en espanol). */
    private static String decodificar(byte[] b) {
        int inicio = b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(b, inicio, b.length - inicio)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(b, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    private static String[] partir(String linea, char sep) {
        List<String> partes = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (ch == '"') {
                if (comillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    comillas = !comillas;
                }
            } else if (ch == sep && !comillas) {
                partes.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        partes.add(actual.toString().trim());
        return partes.toArray(new String[0]);
    }

    private static String celda(String[] c, Integer i) {
        return i == null || i >= c.length ? "" : c[i].trim();
    }

    private static Integer buscar(Map<String, Integer> col, String... nombres) {
        for (String n : nombres) {
            if (col.containsKey(n)) {
                return col.get(n);
            }
        }
        return null;
    }

    private static String normalizar(String s) {
        return java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace("\uFEFF", "");
    }

    /** "1,25", "1.25", "$1,25" o "1.250,50" -> numero. */
    static Double numero(String s) {
        String t = s.replace("$", "").replace(" ", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains(",") && t.contains(".")) {
            t = t.lastIndexOf(',') > t.lastIndexOf('.') ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        } else {
            t = t.replace(',', '.');
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ guardar

    /**
     * Guarda los productos (los que ya existen con el mismo nombre o codigo se saltan).
     * Devuelve {agregados, saltados}.
     */
    static int[] guardar(Connection con, List<Producto> productos) throws SQLException {
        Map<String, String> categorias = new HashMap<>();
        Map<Double, String> impuestos = categoriasImpuesto(con);
        int agregados = 0;
        int saltados = 0;
        for (Producto p : productos) {
            String taxcat = impuestos.get(redondear(p.iva));
            if (taxcat == null || existe(con, "NAME", p.nombre) || (p.codigo != null && !p.codigo.isEmpty() && existe(con, "CODE", p.codigo))) {
                saltados++;
                continue;
            }
            String categoria = categorias.get(p.categoria.toLowerCase());
            if (categoria == null) {
                categoria = categoria(con, p.categoria);
                categorias.put(p.categoria.toLowerCase(), categoria);
            }
            String id = UUID.randomUUID().toString();
            String codigo = p.codigo == null || p.codigo.isEmpty() ? codigoLibre(con) : p.codigo;
            double precioSinIva = Math.round(p.precioConIva / (1 + p.iva) * 1000000.0) / 1000000.0;
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO PRODUCTS (ID, REFERENCE, CODE, NAME, PRICEBUY, PRICESELL, "
                    + "CATEGORY, TAXCAT, ISSERVICE, ISKITCHEN, ISVPRICE, ISVERPATRIB, TEXTTIP, WARRANTY) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0, 0, '', 0)")) {
                ps.setString(1, id);
                ps.setString(2, codigo);
                ps.setString(3, codigo);
                ps.setString(4, p.nombre);
                ps.setDouble(5, p.costo);
                ps.setDouble(6, precioSinIva);
                ps.setString(7, categoria);
                ps.setString(8, taxcat);
                ps.setBoolean(9, p.cocina);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO PRODUCTS_CAT (PRODUCT, CATORDER) VALUES (?, NULL)")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            agregados++;
        }
        return new int[]{agregados, saltados};
    }

    /** Tarifa (0.15, 0...) -> id de la categoria de impuesto que la usa. */
    private static Map<Double, String> categoriasImpuesto(Connection con) throws SQLException {
        Map<Double, String> m = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT CATEGORY, RATE FROM TAXES ORDER BY ID");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Double t = redondear(rs.getDouble(2));
                if (!m.containsKey(t)) {
                    m.put(t, rs.getString(1));
                }
            }
        }
        return m;
    }

    private static Double redondear(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static boolean existe(Connection con, String campo, String valor) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM PRODUCTS WHERE " + campo + " = ?")) {
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String categoria(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT ID FROM CATEGORIES WHERE NAME = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO CATEGORIES (ID, NAME, CATSHOWNAME) VALUES (?, ?, 1)")) {
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
        return id;
    }

    /** Codigo interno libre (Q0001, Q0002...) para productos sin codigo de barras. */
    private static String codigoLibre(Connection con) throws SQLException {
        for (int i = 1; i < 1000000; i++) {
            String c = String.format("Q%04d", i);
            if (!existe(con, "CODE", c) && !existe(con, "REFERENCE", c)) {
                return c;
            }
        }
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
