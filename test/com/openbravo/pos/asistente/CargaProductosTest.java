package com.openbravo.pos.asistente;

import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class CargaProductosTest {

    private static File csv(String texto, String charset) throws Exception {
        File f = File.createTempFile("productos", ".csv");
        f.deleteOnExit();
        Files.write(f.toPath(), texto.getBytes(Charset.forName(charset)));
        return f;
    }

    @Test
    public void leeCsvDeExcelConPuntoYComaYComaDecimal() throws Exception {
        String texto = "Nombre;Precio;Categor\u00EDa;C\u00F3digo;IVA;Costo\r\n"
                + "Caf\u00E9 americano;1,50;Bebidas;;15;0,40\r\n"
                + "Pan de yuca;0,35;Panader\u00EDa;7861234500012;0;\r\n"
                + ";2,00;Sin nombre;;15;\r\n"
                + "Torta;abc;Postres;;15;\r\n"
                + "Agua;0,75;Bebidas;;10;\r\n";
        CargaProductos.Lectura r = CargaProductos.leerCsv(csv(texto, "windows-1252"));
        assertEquals(2, r.productos.size());
        assertEquals("Caf\u00E9 americano", r.productos.get(0).nombre);
        assertEquals(1.50, r.productos.get(0).precioConIva, 1e-9);
        assertEquals(0.15, r.productos.get(0).iva, 1e-9);
        assertEquals(0.40, r.productos.get(0).costo, 1e-9);
        assertEquals("Panader\u00EDa", r.productos.get(1).categoria);
        assertEquals("7861234500012", r.productos.get(1).codigo);
        assertEquals(0.0, r.productos.get(1).iva, 1e-9);
        assertEquals(3, r.problemas.size()); // sin nombre, precio invalido, IVA 10%
    }

    @Test
    public void leeCsvUtf8ConComasYComillas() throws Exception {
        String texto = "\uFEFFnombre,precio,categoria\n\"Sanduche, mixto\",2.50,Sanduches\n";
        CargaProductos.Lectura r = CargaProductos.leerCsv(csv(texto, "UTF-8"));
        assertEquals(1, r.productos.size());
        assertEquals("Sanduche, mixto", r.productos.get(0).nombre);
        assertEquals(2.50, r.productos.get(0).precioConIva, 1e-9);
        assertEquals(0.15, r.productos.get(0).iva, 1e-9); // sin columna IVA: 15%
    }

    @Test
    public void numeros() {
        assertEquals(1.25, CargaProductos.numero("1,25"), 1e-9);
        assertEquals(1.25, CargaProductos.numero("$1.25"), 1e-9);
        assertEquals(1250.5, CargaProductos.numero("1.250,50"), 1e-9);
        assertEquals(1250.5, CargaProductos.numero("1,250.50"), 1e-9);
        assertNull(CargaProductos.numero("abc"));
    }

    @Test
    public void activaYDesactivaOpcionesDeLaVenta() {
        String botones = "<configuration>\n"
                + "\t<!-- <button key=\"button.sendorder\" image=\"img.kit_print\" code=\"script.SendOrder\"/>  -->\n"
                + "    \t<!-- <button key=\"button.sendorder\" name=\"button.sendorder\" code=\"script.SendOrder\"/> -->\n"
                + "</configuration>";
        assertFalse(BotonesVenta.activo(botones, BotonesVenta.ENVIAR_A_COCINA));
        String activo = BotonesVenta.alternar(botones, BotonesVenta.ENVIAR_A_COCINA, true);
        assertTrue(BotonesVenta.activo(activo, BotonesVenta.ENVIAR_A_COCINA));
        assertTrue(activo.contains("<!-- <button key=\"button.sendorder\" name=")); // la otra linea no se toca
        String otraVez = BotonesVenta.alternar(activo, BotonesVenta.ENVIAR_A_COCINA, false);
        assertFalse(BotonesVenta.activo(otraVez, BotonesVenta.ENVIAR_A_COCINA));
        assertEquals(activo, BotonesVenta.alternar(activo, BotonesVenta.ENVIAR_A_COCINA, true)); // idempotente
    }
}
