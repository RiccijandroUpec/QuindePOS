package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.ClaveAccesoGenerator;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import com.openbravo.pos.sri.dominio.Pago;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.repository.TicketCrudo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte un {@link TicketCrudo} (lectura fiel de las tablas de ECOPos) en
 * un {@link Comprobante} de dominio, ya con {@code claveAcceso} asignada -
 * listo para {@link ComprobanteXmlMapper}. Esta es la unica capa que conoce
 * las convenciones de ECOPos (nombres de campos crudos, formas de pago sin
 * catalogo cerrado, etc.) y las traduce a los conceptos que exige el SRI.
 *
 * El {@code secuencial} se recibe ya calculado (ver
 * {@code ComprobanteRepository.siguienteSecuencial()}) en vez de
 * consultarse aqui, para que este mapeo siga siendo una funcion pura y
 * facil de probar sin base de datos.
 */
public final class TicketComprobanteMapper {

    private static final String TIPO_EMISION_NORMAL = "1";

    private TicketComprobanteMapper() {
    }

    public static Comprobante map(TicketCrudo ticket, DatosEmisor emisor, String secuencial) {
        Cliente cliente = mapCliente(ticket);
        List<DetalleFactura> detalles = mapDetalles(ticket);
        List<ImpuestoDetalle> totalesPorImpuesto = agruparImpuestosPorCodigoPorcentaje(detalles);
        List<Pago> pagos = mapPagos(ticket);

        BigDecimal totalSinImpuestos = sumarPrecioTotalSinImpuesto(detalles);
        BigDecimal totalDescuento = BigDecimal.ZERO; // ECOPos no expone descuento por linea en TicketCrudo
        BigDecimal totalImpuestos = sumarValorImpuestos(totalesPorImpuesto);
        BigDecimal importeTotal = totalSinImpuestos.subtract(totalDescuento).add(totalImpuestos)
                .setScale(2, RoundingMode.HALF_UP);

        Comprobante comprobante = new Comprobante(
                ticket.ticketId, TipoComprobante.FACTURA, emisor.getAmbiente(),
                ticket.fecha, emisor, cliente, detalles, totalesPorImpuesto, pagos,
                totalSinImpuestos, totalDescuento, importeTotal, secuencial);

        comprobante.setClaveAcceso(generarClaveAcceso(ticket, emisor, secuencial));
        return comprobante;
    }

    private static String generarClaveAcceso(TicketCrudo ticket, DatosEmisor emisor, String secuencial) {
        return ClaveAccesoGenerator.generar(
                ticket.fecha.toLocalDate(), TipoComprobante.FACTURA, emisor.getRuc(),
                emisor.getAmbiente(), emisor.getEstablecimiento(), emisor.getPuntoEmision(),
                secuencial, TIPO_EMISION_NORMAL);
    }

    private static Cliente mapCliente(TicketCrudo ticket) {
        if (ticket.clienteId == null || ticket.clienteTaxId == null || ticket.clienteTaxId.isEmpty()) {
            return Cliente.consumidorFinal();
        }
        String tipoIdentificacion = TipoIdentificacionResolver.paraIdentificacion(ticket.clienteTaxId);
        String razonSocial = (ticket.clienteNombre != null && !ticket.clienteNombre.isEmpty())
                ? ticket.clienteNombre
                : "CONSUMIDOR FINAL";
        return new Cliente(tipoIdentificacion, ticket.clienteTaxId, razonSocial,
                ticket.clienteDireccion, ticket.clienteEmail, ticket.clienteTelefono);
    }

    private static List<DetalleFactura> mapDetalles(TicketCrudo ticket) {
        List<DetalleFactura> detalles = new ArrayList<>();
        for (TicketCrudo.LineaCruda linea : ticket.lineas) {
            String codigoPrincipal = (linea.referencia != null && !linea.referencia.isEmpty())
                    ? linea.referencia
                    : linea.productoId;
            String descripcion = (linea.nombre != null && !linea.nombre.isEmpty())
                    ? linea.nombre
                    : codigoPrincipal;

            BigDecimal descuento = BigDecimal.ZERO; // TICKETLINES no trae descuento por linea
            BigDecimal precioTotalSinImpuesto = linea.unidades.multiply(linea.precio)
                    .setScale(2, RoundingMode.HALF_UP);

            ImpuestoDetalle impuestoLinea = ImpuestoDetalle.iva(
                    linea.tasaImpuesto, precioTotalSinImpuesto,
                    precioTotalSinImpuesto.multiply(linea.tasaImpuesto).setScale(2, RoundingMode.HALF_UP));

            detalles.add(new DetalleFactura(codigoPrincipal, descripcion, linea.unidades, linea.precio,
                    descuento, precioTotalSinImpuesto, List.of(impuestoLinea)));
        }
        return detalles;
    }

    /**
     * Agrupa los impuestos de todas las lineas por {@code codigoPorcentaje}
     * (no por la tarifa fraccion cruda: BigDecimal con distinta escala,
     * ej. 0.15 vs 0.150, no es igual segun equals/hashCode aunque
     * represente el mismo valor - agrupar por el codigo de catalogo evita
     * esa trampa y ademas es lo que el XSD realmente exige: un
     * <totalImpuesto> por codigo, no por tarifa).
     */
    private static List<ImpuestoDetalle> agruparImpuestosPorCodigoPorcentaje(List<DetalleFactura> detalles) {
        Map<String, BigDecimal> tarifaPorCodigo = new LinkedHashMap<>();
        Map<String, BigDecimal> baseImponiblePorCodigo = new LinkedHashMap<>();
        Map<String, BigDecimal> valorPorCodigo = new LinkedHashMap<>();

        for (DetalleFactura detalle : detalles) {
            for (ImpuestoDetalle impuesto : detalle.getImpuestos()) {
                String codigoPorcentaje = CodigoPorcentajeIva.paraTarifa(impuesto.getTarifa());
                tarifaPorCodigo.putIfAbsent(codigoPorcentaje, impuesto.getTarifa());
                baseImponiblePorCodigo.merge(codigoPorcentaje, impuesto.getBaseImponible(), BigDecimal::add);
                valorPorCodigo.merge(codigoPorcentaje, impuesto.getValor(), BigDecimal::add);
            }
        }

        List<ImpuestoDetalle> totales = new ArrayList<>();
        for (String codigoPorcentaje : tarifaPorCodigo.keySet()) {
            totales.add(ImpuestoDetalle.iva(
                    tarifaPorCodigo.get(codigoPorcentaje),
                    baseImponiblePorCodigo.get(codigoPorcentaje),
                    valorPorCodigo.get(codigoPorcentaje)));
        }
        return totales;
    }

    private static List<Pago> mapPagos(TicketCrudo ticket) {
        List<Pago> pagos = new ArrayList<>();
        for (TicketCrudo.PagoCrudo pago : ticket.pagos) {
            pagos.add(new Pago(FormaPagoResolver.paraNombreEcoPos(pago.nombre), pago.total));
        }
        return pagos;
    }

    private static BigDecimal sumarPrecioTotalSinImpuesto(List<DetalleFactura> detalles) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleFactura detalle : detalles) {
            total = total.add(detalle.getPrecioTotalSinImpuesto());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumarValorImpuestos(List<ImpuestoDetalle> totalesPorImpuesto) {
        BigDecimal total = BigDecimal.ZERO;
        for (ImpuestoDetalle impuesto : totalesPorImpuesto) {
            total = total.add(impuesto.getValor());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
