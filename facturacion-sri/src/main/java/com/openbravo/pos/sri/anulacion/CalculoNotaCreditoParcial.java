package com.openbravo.pos.sri.anulacion;

import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Calculos de una Nota de Credito PARCIAL (devolver solo algunos productos o
 * cantidades de una factura autorizada). Sin base de datos ni SRI: solo
 * aritmetica, para poder probarla con tests.
 *
 * Por linea: base = precio unitario x cantidad devuelta - descuento
 * proporcional; IVA = base x tarifa (redondeo a 2 decimales, mitad hacia
 * arriba). Totales por impuesto agrupando por codigo + tarifa.
 */
public final class CalculoNotaCreditoParcial {

    /** Resultado listo para armar el Comprobante de la nota de credito. */
    public static final class Resultado {
        public final List<DetalleFactura> detalles;
        public final List<ImpuestoDetalle> totalesPorImpuesto;
        public final BigDecimal totalSinImpuestos;
        public final BigDecimal importeTotal;

        Resultado(List<DetalleFactura> detalles, List<ImpuestoDetalle> totalesPorImpuesto,
                  BigDecimal totalSinImpuestos, BigDecimal importeTotal) {
            this.detalles = detalles;
            this.totalesPorImpuesto = totalesPorImpuesto;
            this.totalSinImpuestos = totalSinImpuestos;
            this.importeTotal = importeTotal;
        }
    }

    private CalculoNotaCreditoParcial() {
    }

    /**
     * @param originales lineas de la factura original (en su orden)
     * @param cantidades cantidad a devolver por linea (mismo orden; 0 = no se devuelve)
     */
    public static Resultado calcular(List<DetalleFactura> originales, List<BigDecimal> cantidades) {
        if (originales.size() != cantidades.size()) {
            throw new IllegalArgumentException("Debe haber una cantidad por cada línea de la factura");
        }
        List<DetalleFactura> detalles = new ArrayList<>();
        Map<String, BigDecimal[]> totales = new LinkedHashMap<>();
        Map<String, ImpuestoDetalle> muestra = new HashMap<>();
        BigDecimal totalSinImpuestos = BigDecimal.ZERO;
        BigDecimal totalImpuestos = BigDecimal.ZERO;

        for (int i = 0; i < originales.size(); i++) {
            BigDecimal cantidad = cantidades.get(i) == null ? BigDecimal.ZERO : cantidades.get(i);
            if (cantidad.signum() <= 0) {
                continue;
            }
            DetalleFactura o = originales.get(i);
            if (cantidad.compareTo(o.getCantidad()) > 0) {
                throw new IllegalArgumentException("No se puede devolver más de lo vendido de " + o.getDescripcion());
            }
            BigDecimal descuento = o.getDescuento() == null || o.getCantidad().signum() == 0 ? BigDecimal.ZERO
                    : o.getDescuento().multiply(cantidad).divide(o.getCantidad(), 2, RoundingMode.HALF_UP);
            BigDecimal base = o.getPrecioUnitario().multiply(cantidad).subtract(descuento).setScale(2, RoundingMode.HALF_UP);

            List<ImpuestoDetalle> impuestos = new ArrayList<>();
            for (ImpuestoDetalle imp : o.getImpuestos()) {
                BigDecimal valor = base.multiply(imp.getTarifa()).setScale(2, RoundingMode.HALF_UP);
                impuestos.add(new ImpuestoDetalle(imp.getCodigoImpuesto(), imp.getTarifa(), base, valor));
                String clave = imp.getCodigoImpuesto() + "|" + imp.getTarifa().stripTrailingZeros().toPlainString();
                BigDecimal[] acumulado = totales.computeIfAbsent(clave, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                acumulado[0] = acumulado[0].add(base);
                acumulado[1] = acumulado[1].add(valor);
                muestra.put(clave, imp);
                totalImpuestos = totalImpuestos.add(valor);
            }
            detalles.add(new DetalleFactura(o.getCodigoPrincipal(), o.getDescripcion(), cantidad,
                    o.getPrecioUnitario(), descuento, base, impuestos));
            totalSinImpuestos = totalSinImpuestos.add(base);
        }
        if (detalles.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos un producto para devolver");
        }
        List<ImpuestoDetalle> totalesPorImpuesto = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : totales.entrySet()) {
            ImpuestoDetalle m = muestra.get(e.getKey());
            totalesPorImpuesto.add(new ImpuestoDetalle(m.getCodigoImpuesto(), m.getTarifa(), e.getValue()[0], e.getValue()[1]));
        }
        return new Resultado(detalles, totalesPorImpuesto, totalSinImpuestos,
                totalSinImpuestos.add(totalImpuestos).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Cantidades ya devueltas por codigo de producto, leyendo el XML de las
     * notas de credito anteriores de la misma factura (etiqueta
     * {@code detalle/codigoInterno} + {@code cantidad}).
     */
    public static Map<String, BigDecimal> yaAcreditadoPorCodigo(List<String> xmlsNotasCredito) {
        Map<String, BigDecimal> resultado = new HashMap<>();
        for (String xml : xmlsNotasCredito) {
            if (xml == null || xml.isBlank()) {
                continue;
            }
            try {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                NodeList detalles = doc.getElementsByTagName("detalle");
                for (int i = 0; i < detalles.getLength(); i++) {
                    Element d = (Element) detalles.item(i);
                    String codigo = texto(d, "codigoInterno");
                    String cantidad = texto(d, "cantidad");
                    if (codigo != null && cantidad != null) {
                        resultado.merge(codigo, new BigDecimal(cantidad.trim()), BigDecimal::add);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo leer una nota de crédito anterior de esta factura", e);
            }
        }
        return resultado;
    }

    /**
     * Cuanto queda por devolver de cada linea, repartiendo lo ya acreditado de
     * cada codigo entre sus lineas en orden.
     */
    public static List<BigDecimal> disponiblePorLinea(List<DetalleFactura> originales, Map<String, BigDecimal> yaAcreditado) {
        Map<String, BigDecimal> pendiente = new HashMap<>(yaAcreditado);
        List<BigDecimal> disponibles = new ArrayList<>();
        for (DetalleFactura o : originales) {
            BigDecimal acreditado = pendiente.getOrDefault(o.getCodigoPrincipal(), BigDecimal.ZERO);
            BigDecimal usado = acreditado.min(o.getCantidad());
            pendiente.put(o.getCodigoPrincipal(), acreditado.subtract(usado));
            disponibles.add(o.getCantidad().subtract(usado));
        }
        return disponibles;
    }

    private static String texto(Element padre, String etiqueta) {
        NodeList n = padre.getElementsByTagName(etiqueta);
        return n.getLength() == 0 ? null : n.item(0).getTextContent();
    }
}
