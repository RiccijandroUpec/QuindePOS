package com.openbravo.pos.sri.ride;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.openbravo.pos.sri.config.RutasConector;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja el RIDE (representacion impresa del comprobante electronico) con el
 * formato habitual del SRI: logo y datos del emisor a la izquierda, recuadro
 * con RUC, numero, autorizacion y codigo de barras de la clave de acceso a la
 * derecha, datos del comprador, detalle en tabla, informacion adicional y
 * forma de pago, y el cuadro de subtotales por tarifa.
 *
 * El logo del negocio es opcional: sri-conector/config/logo.png (o .jpg).
 */
final class RideRenderer {

    private static final float M = 28f;
    private static final float ANCHO = PDRectangle.A4.getWidth();
    private static final float ALTO = PDRectangle.A4.getHeight();
    private static final float CONTENIDO = ANCHO - 2 * M;
    private static final float RADIO = 8f;
    private static final Color GRIS = new Color(0xEE, 0xEE, 0xEE);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final PDFont normal = PDType1Font.HELVETICA;
    private final PDFont negrita = PDType1Font.HELVETICA_BOLD;
    private final ModeloRide r;
    private PDDocument doc;
    private PDPageContentStream cs;
    private float y;

    private RideRenderer(ModeloRide r) {
        this.r = r;
    }

    static byte[] generar(ModeloRide modelo) throws IOException {
        return new RideRenderer(modelo).dibujar();
    }

    private byte[] dibujar() throws IOException {
        try (PDDocument d = new PDDocument()) {
            doc = d;
            nuevaPagina();
            encabezado();
            if (r.docModificado != null) {
                documentoModificado();
            }
            comprador();
            detalle();
            pie();
            cs.close();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            doc.save(salida);
            return salida.toByteArray();
        }
    }

    private void nuevaPagina() throws IOException {
        if (cs != null) {
            cs.close();
        }
        PDPage pagina = new PDPage(PDRectangle.A4);
        doc.addPage(pagina);
        cs = new PDPageContentStream(doc, pagina);
        cs.setLineWidth(0.6f);
        y = ALTO - M;
    }

    // ------------------------------------------------------------------ encabezado

    private void encabezado() throws IOException {
        float arriba = y;
        float abajo = arriba - 238;

        // Recuadro derecho: RUC, tipo, numero, autorizacion, clave de acceso.
        float xd = 300;
        float wd = ANCHO - M - xd;
        cajaRedondeada(xd, abajo, wd, arriba - abajo);
        float yy = arriba - 20;
        texto(negrita, 10, xd + 12, yy, "R.U.C.:");
        texto(normal, 10, xd + 70, yy, r.ruc);
        yy -= 18;
        texto(negrita, 13, xd + 12, yy, r.tipo);
        yy -= 17;
        texto(negrita, 9, xd + 12, yy, "No.:");
        texto(normal, 10, xd + 70, yy, r.numero);
        yy -= 17;
        texto(negrita, 9, xd + 12, yy, "NÚMERO DE AUTORIZACIÓN");
        yy -= 13;
        texto(normal, 8.5f, xd + 12, yy, r.claveAcceso);
        yy -= 17;
        texto(negrita, 8, xd + 12, yy, "FECHA Y HORA DE");
        texto(normal, 9, xd + 120, yy, r.fechaAutorizacion != null ? r.fechaAutorizacion.format(FECHA_HORA) : "");
        yy -= 10;
        texto(negrita, 8, xd + 12, yy, "AUTORIZACIÓN:");
        yy -= 16;
        texto(negrita, 8, xd + 12, yy, "AMBIENTE:");
        texto(normal, 9, xd + 120, yy, r.ambiente);
        yy -= 14;
        texto(negrita, 8, xd + 12, yy, "EMISIÓN:");
        texto(normal, 9, xd + 120, yy, r.emision);
        yy -= 16;
        texto(negrita, 9, xd + 12, yy, "CLAVE DE ACCESO");
        codigoBarras(r.claveAcceso, xd + 10, abajo + 20, wd - 20, 42);
        textoCentrado(normal, 7.5f, xd, wd, abajo + 8, r.claveAcceso);

        // Izquierda: logo (o el nombre en grande) y recuadro del emisor.
        float wi = xd - M - 10;
        float inicioEmisor = arriba - 110;
        PDImageXObject logo = cargarLogo();
        if (logo != null) {
            float escala = Math.min(wi / logo.getWidth(), 96f / logo.getHeight());
            float lw = logo.getWidth() * escala;
            float lh = logo.getHeight() * escala;
            cs.drawImage(logo, M + (wi - lw) / 2, arriba - 4 - lh - (96 - lh) / 2, lw, lh);
        } else {
            String nombre = vacio(r.nombreComercial).isEmpty() ? r.razonSocial : r.nombreComercial;
            List<String> lineas = envolver(vacio(nombre).toUpperCase(), negrita, 16, wi - 10);
            float yl = arriba - 50 + (lineas.size() - 1) * 10;
            for (String l : lineas.subList(0, Math.min(3, lineas.size()))) {
                textoCentrado(negrita, 16, M, wi, yl, l);
                yl -= 20;
            }
        }
        cajaRedondeada(M, abajo, wi, inicioEmisor - abajo);
        float ye = inicioEmisor - 16;
        for (String l : envolver(vacio(r.razonSocial), negrita, 9.5f, wi - 20)) {
            textoCentrado(negrita, 9.5f, M, wi, ye, l);
            ye -= 12;
        }
        if (!vacio(r.nombreComercial).isEmpty() && !r.nombreComercial.equalsIgnoreCase(vacio(r.razonSocial))) {
            for (String l : envolver(r.nombreComercial, negrita, 8.5f, wi - 20)) {
                textoCentrado(negrita, 8.5f, M, wi, ye, l);
                ye -= 11;
            }
        }
        ye -= 6;
        ye = campoEmisor(ye, wi, "Dirección Matriz:", r.dirMatriz);
        if (!vacio(r.dirEstablecimiento).isEmpty()) {
            ye = campoEmisor(ye, wi, "Dirección Sucursal:", r.dirEstablecimiento);
        }
        if (!vacio(r.contribuyenteEspecial).isEmpty()) {
            ye = campoEmisor(ye, wi, "Contribuyente Especial Nro:", r.contribuyenteEspecial);
        }
        ye = campoEmisor(ye, wi, "OBLIGADO A LLEVAR CONTABILIDAD:", vacio(r.obligadoContabilidad).isEmpty() ? "NO" : r.obligadoContabilidad);
        if (!vacio(r.agenteRetencion).isEmpty()) {
            ye = campoEmisor(ye, wi, "Agente de Retención Resolución No.:", r.agenteRetencion);
        }
        if (!vacio(r.contribuyenteRimpe).isEmpty()) {
            for (String l : envolver(r.contribuyenteRimpe, negrita, 7.5f, wi - 20)) {
                texto(negrita, 7.5f, M + 10, ye, l);
                ye -= 10;
            }
        }
        y = abajo - 10;
    }

    private float campoEmisor(float ye, float wi, String etiqueta, String valor) throws IOException {
        float anchoEtiqueta = Math.min(ancho(negrita, 7.5f, etiqueta) + 6, 110);
        List<String> etiquetas = envolver(etiqueta, negrita, 7.5f, anchoEtiqueta);
        List<String> valores = envolver(vacio(valor), normal, 8, wi - 20 - anchoEtiqueta);
        int filas = Math.max(etiquetas.size(), valores.size());
        for (int i = 0; i < filas; i++) {
            if (i < etiquetas.size()) {
                texto(negrita, 7.5f, M + 10, ye, etiquetas.get(i));
            }
            if (i < valores.size()) {
                texto(normal, 8, M + 10 + anchoEtiqueta, ye, valores.get(i));
            }
            ye -= 10;
        }
        return ye - 5;
    }

    private PDImageXObject cargarLogo() {
        for (String nombre : new String[]{"config/logo.png", "config/logo.jpg", "config/logo.jpeg"}) {
            try {
                Path ruta = RutasConector.resolver(nombre);
                if (Files.isRegularFile(ruta)) {
                    return PDImageXObject.createFromFileByContent(ruta.toFile(), doc);
                }
            } catch (Exception e) {
                // Logo ilegible: el RIDE sale sin logo.
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ comprador

    private void documentoModificado() throws IOException {
        float alto = 50;
        float valor = M + 200;
        List<String> motivo = envolver(vacio(r.motivo), normal, 8.5f, CONTENIDO - 210);
        alto += (motivo.size() - 1) * 11;
        caja(M, y - alto, CONTENIDO, alto);
        float yy = y - 14;
        texto(negrita, 8.5f, M + 8, yy, "Comprobante que se modifica:");
        texto(normal, 8.5f, valor, yy, "FACTURA  " + vacio(r.docModificado));
        yy -= 13;
        texto(negrita, 8.5f, M + 8, yy, "Fecha Emisión (Comprobante a modificar):");
        texto(normal, 8.5f, valor, yy, vacio(r.fechaDocModificado));
        yy -= 13;
        texto(negrita, 8.5f, M + 8, yy, "Razón de Modificación:");
        for (String l : motivo) {
            texto(normal, 8.5f, valor, yy, l);
            yy -= 11;
        }
        y -= alto + 8;
    }

    private void comprador() throws IOException {
        boolean conDireccion = !vacio(r.compradorDireccion).isEmpty();
        float alto = conDireccion ? 56 : 42;
        caja(M, y - alto, CONTENIDO, alto);
        float x2 = M + 350;
        float yy = y - 15;
        texto(negrita, 8.5f, M + 8, yy, "Razón Social / Nombres y Apellidos:");
        texto(normal, 8.5f, M + 170, yy, recortar(vacio(r.compradorRazonSocial), normal, 8.5f, x2 - M - 175));
        texto(negrita, 8.5f, x2, yy, "Identificación:");
        texto(normal, 8.5f, x2 + 75, yy, vacio(r.compradorIdentificacion));
        yy -= 14;
        texto(negrita, 8.5f, M + 8, yy, "Fecha de Emisión:");
        texto(normal, 8.5f, M + 170, yy, vacio(r.fechaEmision));
        if (r.docModificado == null) {
            texto(negrita, 8.5f, x2, yy, "Guía de Remisión:");
            texto(normal, 8.5f, x2 + 85, yy, vacio(r.guiaRemision));
        }
        if (conDireccion) {
            yy -= 14;
            texto(negrita, 8.5f, M + 8, yy, "Dirección:");
            texto(normal, 8.5f, M + 170, yy, recortar(r.compradorDireccion, normal, 8.5f, CONTENIDO - 175));
        }
        y -= alto + 8;
    }

    // ------------------------------------------------------------------ detalle

    private static final float[] COLUMNAS = {62, 0, 52, 62, 52, 62};
    private static final String[][] TITULOS = {
        {"Cod.", "Principal"}, {"Descripción"}, {"Cantidad"}, {"Precio", "Unitario"}, {"Descuento"}, {"Precio", "Total"}
    };

    private float[] anchosColumnas() {
        float[] a = COLUMNAS.clone();
        float fijo = 0;
        for (float v : a) {
            fijo += v;
        }
        a[1] = CONTENIDO - fijo;
        return a;
    }

    private void encabezadoDetalle(float[] a) throws IOException {
        float alto = 24;
        cs.setNonStrokingColor(GRIS);
        cs.addRect(M, y - alto, CONTENIDO, alto);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
        float x = M;
        for (int i = 0; i < a.length; i++) {
            caja(x, y - alto, a[i], alto);
            String[] t = TITULOS[i];
            float yt = t.length == 1 ? y - 15 : y - 10;
            for (String s : t) {
                if (i == 1) {
                    texto(negrita, 8, x + 4, yt, s);
                } else {
                    textoCentrado(negrita, 8, x, a[i], yt, s);
                }
                yt -= 9;
            }
            x += a[i];
        }
        y -= alto;
    }

    private void detalle() throws IOException {
        float[] a = anchosColumnas();
        encabezadoDetalle(a);
        for (ModeloRide.Linea l : r.detalle) {
            List<String> desc = envolver(vacio(l.descripcion), normal, 8, a[1] - 8);
            List<String> extras = new ArrayList<>();
            for (String ad : l.adicionales) {
                extras.addAll(envolver(ad, normal, 7, a[1] - 8));
            }
            List<String> codigo = envolver(vacio(l.codigo), normal, 7.5f, a[0] - 6);
            int filas = Math.max(codigo.size(), desc.size() + extras.size());
            float alto = 6 + filas * 10;
            if (y - alto < M + 20) {
                nuevaPagina();
                encabezadoDetalle(a);
            }
            float x = M;
            for (float w : a) {
                caja(x, y - alto, w, alto);
                x += w;
            }
            float yt = y - 11;
            float yc = yt;
            for (String s : codigo) {
                texto(normal, 7.5f, M + 3, yc, s);
                yc -= 10;
            }
            float xDesc = M + a[0] + 4;
            for (String s : desc) {
                texto(normal, 8, xDesc, yt, s);
                yt -= 10;
            }
            for (String s : extras) {
                texto(normal, 7, xDesc + 4, yt, s);
                yt -= 10;
            }
            float xn = M + a[0] + a[1];
            textoDerecha(normal, 8, xn + a[2] - 4, y - 11, cantidad(l.cantidad));
            xn += a[2];
            textoDerecha(normal, 8, xn + a[3] - 4, y - 11, cantidad(l.precioUnitario));
            xn += a[3];
            textoDerecha(normal, 8, xn + a[4] - 4, y - 11, dinero(l.descuento));
            xn += a[4];
            textoDerecha(normal, 8, xn + a[5] - 4, y - 11, dinero(l.total));
            y -= alto;
        }
        y -= 10;
    }

    // ------------------------------------------------------------------ pie: adicional, pagos, totales

    private List<String[]> filasTotales() {
        List<String[]> f = new ArrayList<>();
        // Siempre la tarifa vigente (15%) y las de 0%, no objeto y exento, como en el formato del SRI;
        // otras tarifas (5%, 12%, 13%...) solo si el comprobante las tiene.
        f.add(new String[]{"SUBTOTAL 15%", dinero(base("4"))});
        for (String cod : r.baseIva.keySet()) {
            String t = ModeloRide.tarifaIva(cod);
            if (t != null && !"4".equals(cod) && !"0".equals(cod)) {
                f.add(new String[]{"SUBTOTAL " + t, dinero(base(cod))});
            }
        }
        f.add(new String[]{"SUBTOTAL 0%", dinero(base("0"))});
        f.add(new String[]{"SUBTOTAL NO OBJETO DE IVA", dinero(base("6"))});
        f.add(new String[]{"SUBTOTAL EXENTO DE IVA", dinero(base("7"))});
        f.add(new String[]{"SUBTOTAL SIN IMPUESTOS", dinero(r.subtotalSinImpuestos)});
        f.add(new String[]{"TOTAL DESCUENTO", dinero(r.totalDescuento)});
        f.add(new String[]{"ICE", dinero(r.ice)});
        f.add(new String[]{"IVA 15%", dinero(valor("4"))});
        for (String cod : r.valorIva.keySet()) {
            String t = ModeloRide.tarifaIva(cod);
            if (t != null && !"4".equals(cod) && !"0".equals(cod)) {
                f.add(new String[]{"IVA " + t, dinero(valor(cod))});
            }
        }
        f.add(new String[]{"IRBPNR", dinero(r.irbpnr)});
        if (r.propina != null) {
            f.add(new String[]{"PROPINA", dinero(r.propina)});
        }
        f.add(new String[]{r.etiquetaTotal, dinero(r.total)});
        return f;
    }

    private BigDecimal base(String cod) {
        return r.baseIva.getOrDefault(cod, BigDecimal.ZERO);
    }

    private BigDecimal valor(String cod) {
        return r.valorIva.getOrDefault(cod, BigDecimal.ZERO);
    }

    private void pie() throws IOException {
        float xDer = M + CONTENIDO - 225;
        float wEtiqueta = 150;
        float wValor = 75;
        float wIzq = xDer - M - 15;
        float filaAlto = 14;
        List<String[]> totales = filasTotales();

        // Alto de la columna izquierda (informacion adicional + forma de pago).
        List<List<String>> adicional = new ArrayList<>();
        float wNombre = 70;
        for (String[] c : r.infoAdicional) {
            adicional.add(envolver(vacio(c[1]), normal, 8, wIzq - wNombre - 16));
        }
        float altoAdicional = 0;
        if (!r.infoAdicional.isEmpty()) {
            altoAdicional = 26;
            for (List<String> v : adicional) {
                altoAdicional += Math.max(1, v.size()) * 11 + 2;
            }
        }
        float altoPagos = r.pagos.isEmpty() ? 0 : 16 + r.pagos.size() * 22 + 10;
        float altoIzq = altoAdicional + altoPagos;
        float altoDer = totales.size() * filaAlto;
        if (y - Math.max(altoIzq, altoDer) < M) {
            nuevaPagina();
        }
        float inicio = y;

        // Totales (derecha).
        float yt = inicio;
        for (int i = 0; i < totales.size(); i++) {
            boolean ultimo = i == totales.size() - 1;
            caja(xDer, yt - filaAlto, wEtiqueta, filaAlto);
            caja(xDer + wEtiqueta, yt - filaAlto, wValor, filaAlto);
            PDFont f = ultimo ? negrita : normal;
            texto(negrita, 8, xDer + 4, yt - 10, totales.get(i)[0] + " :");
            textoDerecha(f, 8.5f, xDer + wEtiqueta + wValor - 4, yt - 10, totales.get(i)[1]);
            yt -= filaAlto;
        }

        // Informacion adicional (izquierda).
        float yi = inicio;
        if (!r.infoAdicional.isEmpty()) {
            caja(M, yi - altoAdicional, wIzq, altoAdicional);
            texto(negrita, 9, M + 10, yi - 15, "Información Adicional");
            float yy = yi - 30;
            for (int i = 0; i < r.infoAdicional.size(); i++) {
                texto(negrita, 8, M + 10, yy, recortar(vacio(r.infoAdicional.get(i)[0]) + " :", negrita, 8, wNombre));
                List<String> v = adicional.get(i);
                if (v.isEmpty()) {
                    yy -= 13;
                }
                for (String s : v) {
                    texto(normal, 8, M + 10 + wNombre + 6, yy, s);
                    yy -= 11;
                }
                yy -= 2;
            }
            yi -= altoAdicional + 10;
        }

        // Forma de pago (izquierda, debajo).
        if (!r.pagos.isEmpty()) {
            float[] a = {wIzq - 150, 55, 45, 50};
            String[] t = {"Forma de Pago", "Valor", "Plazo", "Tiempo"};
            float x = M;
            for (int i = 0; i < a.length; i++) {
                caja(x, yi - 16, a[i], 16);
                texto(normal, 8, x + 4, yi - 11, t[i]);
                x += a[i];
            }
            yi -= 16;
            for (ModeloRide.Pago p : r.pagos) {
                List<String> d = envolver(vacio(p.descripcion), normal, 6.5f, a[0] - 8);
                float alto = Math.max(22, 6 + d.size() * 8);
                x = M;
                for (float w : a) {
                    caja(x, yi - alto, w, alto);
                    x += w;
                }
                float yd = yi - 9;
                for (String s : d) {
                    texto(normal, 6.5f, M + 4, yd, s);
                    yd -= 8;
                }
                texto(normal, 8, M + a[0] + 4, yi - 12, dinero(p.valor));
                texto(normal, 8, M + a[0] + a[1] + 4, yi - 12, vacio(p.plazo));
                texto(normal, 8, M + a[0] + a[1] + a[2] + 4, yi - 12, vacio(p.tiempo));
                yi -= alto;
            }
        }
    }

    // ------------------------------------------------------------------ primitivas

    private void codigoBarras(String clave, float x, float yBase, float anchoMax, float alto) throws IOException {
        if (clave == null || clave.isEmpty()) {
            return;
        }
        try {
            // Barras como rectangulos vectoriales: nitidas al imprimir y faciles de leer con pistola.
            BitMatrix m = new Code128Writer().encode(clave, BarcodeFormat.CODE_128, 1, 1);
            int inicio = 0;
            while (inicio < m.getWidth() && !m.get(inicio, 0)) {
                inicio++;
            }
            int fin = m.getWidth() - 1;
            while (fin > inicio && !m.get(fin, 0)) {
                fin--;
            }
            int modulos = fin - inicio + 1;
            float modulo = anchoMax / modulos;
            float xi = x + (anchoMax - modulo * modulos) / 2;
            cs.setNonStrokingColor(Color.BLACK);
            int c = inicio;
            while (c <= fin) {
                if (m.get(c, 0)) {
                    int n = c;
                    while (n <= fin && m.get(n, 0)) {
                        n++;
                    }
                    cs.addRect(xi + (c - inicio) * modulo, yBase, (n - c) * modulo, alto);
                    c = n;
                } else {
                    c++;
                }
            }
            cs.fill();
        } catch (Exception e) {
            // El codigo de barras es una ayuda: sin el, el RIDE sigue siendo valido.
        }
    }

    private void caja(float x, float yAbajo, float w, float h) throws IOException {
        cs.addRect(x, yAbajo, w, h);
        cs.stroke();
    }

    private void cajaRedondeada(float x, float yAbajo, float w, float h) throws IOException {
        float r0 = RADIO;
        float k = 0.5523f * r0;
        float x2 = x + w;
        float y2 = yAbajo + h;
        cs.moveTo(x + r0, yAbajo);
        cs.lineTo(x2 - r0, yAbajo);
        cs.curveTo(x2 - r0 + k, yAbajo, x2, yAbajo + r0 - k, x2, yAbajo + r0);
        cs.lineTo(x2, y2 - r0);
        cs.curveTo(x2, y2 - r0 + k, x2 - r0 + k, y2, x2 - r0, y2);
        cs.lineTo(x + r0, y2);
        cs.curveTo(x + r0 - k, y2, x, y2 - r0 + k, x, y2 - r0);
        cs.lineTo(x, yAbajo + r0);
        cs.curveTo(x, yAbajo + r0 - k, x + r0 - k, yAbajo, x + r0, yAbajo);
        cs.closePath();
        cs.stroke();
    }

    private void texto(PDFont f, float tam, float x, float yy, String s) throws IOException {
        String t = seguro(f, s);
        if (t.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(f, tam);
        cs.newLineAtOffset(x, yy);
        cs.showText(t);
        cs.endText();
    }

    private void textoCentrado(PDFont f, float tam, float x, float w, float yy, String s) throws IOException {
        texto(f, tam, x + (w - ancho(f, tam, s)) / 2, yy, s);
    }

    private void textoDerecha(PDFont f, float tam, float xDerecha, float yy, String s) throws IOException {
        texto(f, tam, xDerecha - ancho(f, tam, s), yy, s);
    }

    private float ancho(PDFont f, float tam, String s) {
        try {
            return f.getStringWidth(seguro(f, s)) / 1000f * tam;
        } catch (IOException e) {
            return s.length() * tam * 0.5f;
        }
    }

    /** Parte el texto en lineas que caben en el ancho (corta palabras demasiado largas). */
    private List<String> envolver(String s, PDFont f, float tam, float anchoMax) {
        List<String> lineas = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) {
            return lineas;
        }
        StringBuilder actual = new StringBuilder();
        for (String palabra : s.trim().split("\\s+")) {
            String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;
            if (ancho(f, tam, prueba) <= anchoMax) {
                actual.setLength(0);
                actual.append(prueba);
                continue;
            }
            if (actual.length() > 0) {
                lineas.add(actual.toString());
                actual.setLength(0);
            }
            while (ancho(f, tam, palabra) > anchoMax && palabra.length() > 1) {
                int n = palabra.length() - 1;
                while (n > 1 && ancho(f, tam, palabra.substring(0, n)) > anchoMax) {
                    n--;
                }
                lineas.add(palabra.substring(0, n));
                palabra = palabra.substring(n);
            }
            actual.append(palabra);
        }
        if (actual.length() > 0) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private String recortar(String s, PDFont f, float tam, float anchoMax) {
        if (ancho(f, tam, s) <= anchoMax) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && ancho(f, tam, t + "...") > anchoMax) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    /** Quita los caracteres que la fuente no puede dibujar (emojis, etc.) en vez de fallar. */
    private static String seguro(PDFont f, String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String c = new String(Character.toChars(cp));
            try {
                f.encode(c);
                b.append(c);
            } catch (Exception e) {
                b.append(cp == '\t' || cp == '\n' || cp == '\r' ? " " : "?");
            }
            i += Character.charCount(cp);
        }
        return b.toString();
    }

    private static String vacio(String s) {
        return s == null ? "" : s.trim();
    }

    private static String dinero(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        return x.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Cantidades y precios unitarios: al menos 2 decimales, hasta 6 si los tiene. */
    private static String cantidad(BigDecimal v) {
        if (v == null) {
            return "";
        }
        BigDecimal x = v.stripTrailingZeros();
        if (x.scale() < 2) {
            x = x.setScale(2, RoundingMode.HALF_UP);
        }
        return x.toPlainString().replace('.', ',');
    }
}
