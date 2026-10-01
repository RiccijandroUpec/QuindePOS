package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import com.openbravo.pos.sri.xml.generado.notacredito.Impuesto;
import com.openbravo.pos.sri.xml.generado.notacredito.InfoTributaria;
import com.openbravo.pos.sri.xml.generado.notacredito.NotaCredito;
import com.openbravo.pos.sri.xml.generado.notacredito.ObligadoContabilidad;
import com.openbravo.pos.sri.xml.generado.notacredito.TotalConImpuestos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Convierte un {@link Comprobante} de tipo
 * {@link com.openbravo.pos.sri.dominio.TipoComprobante#NOTA_CREDITO} en el
 * arbol de clases JAXB generado desde {@code notaCredito_V1.1.0.xsd}. Espejo
 * de {@link ComprobanteXmlMapper} (misma logica de detalles/impuestos/campos
 * en blanco), pero apuntando a {@code infoNotaCredito} en vez de
 * {@code infoFactura} - los tipos generados viven en un paquete Java
 * distinto ({@code xml.generado.notacredito}) porque el XSD de nota de
 * credito define tipos con el mismo nombre que el de factura pero forma
 * distinta (ver comentario en {@code pom.xml}).
 */
public final class NotaCreditoXmlMapper {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TIPO_EMISION_NORMAL = "1";
    private static final String MONEDA = "DOLAR";

    private NotaCreditoXmlMapper() {
    }

    public static NotaCredito map(Comprobante comprobante) {
        if (comprobante.getClaveAcceso() == null) {
            throw new IllegalStateException(
                    "El comprobante debe tener claveAcceso asignada antes de generar el XML (nota de credito, factura original "
                    + comprobante.getComprobanteOriginalId() + ")");
        }

        NotaCredito notaCredito = new NotaCredito();
        notaCredito.setId("comprobante");
        notaCredito.setVersion("1.1.0");
        notaCredito.setInfoTributaria(mapInfoTributaria(comprobante));
        notaCredito.setInfoNotaCredito(mapInfoNotaCredito(comprobante));
        notaCredito.setDetalles(mapDetalles(comprobante));
        return notaCredito;
    }

    private static InfoTributaria mapInfoTributaria(Comprobante c) {
        DatosEmisor emisor = c.getEmisor();

        InfoTributaria it = new InfoTributaria();
        it.setAmbiente(String.valueOf(c.getAmbiente().getCodigo()));
        it.setTipoEmision(TIPO_EMISION_NORMAL);
        it.setRazonSocial(emisor.getRazonSocial());
        it.setNombreComercial(vacioANulo(emisor.getNombreComercial()));
        it.setRuc(emisor.getRuc());
        it.setClaveAcceso(c.getClaveAcceso());
        it.setCodDoc(c.getTipo().getCodigo());
        it.setEstab(emisor.getEstablecimiento());
        it.setPtoEmi(emisor.getPuntoEmision());
        it.setSecuencial(c.getSecuencial());
        it.setDirMatriz(emisor.getDirMatriz());
        return it;
    }

    private static NotaCredito.InfoNotaCredito mapInfoNotaCredito(Comprobante c) {
        DatosEmisor emisor = c.getEmisor();
        Cliente cliente = c.getCliente();

        NotaCredito.InfoNotaCredito info = new NotaCredito.InfoNotaCredito();
        info.setFechaEmision(c.getFechaEmision().format(FORMATO_FECHA));
        info.setDirEstablecimiento(vacioANulo(emisor.getDirEstablecimiento()));
        info.setTipoIdentificacionComprador(cliente.getTipoIdentificacion());
        info.setRazonSocialComprador(cliente.getRazonSocial());
        info.setIdentificacionComprador(cliente.getIdentificacion());
        info.setContribuyenteEspecial(vacioANulo(emisor.getContribuyenteEspecial()));
        info.setObligadoContabilidad(emisor.isObligadoContabilidad() ? ObligadoContabilidad.SI : ObligadoContabilidad.NO);
        info.setCodDocModificado(c.getCodDocModificado());
        info.setNumDocModificado(c.getNumDocModificado());
        info.setFechaEmisionDocSustento(c.getFechaEmisionDocSustento().format(FORMATO_FECHA));
        info.setTotalSinImpuestos(dosDecimales(c.getTotalSinImpuestos()));
        info.setValorModificacion(dosDecimales(c.getImporteTotal()));
        info.setMoneda(MONEDA);
        info.setTotalConImpuestos(mapTotalConImpuestos(c));
        info.setMotivo(c.getMotivo());
        return info;
    }

    private static TotalConImpuestos mapTotalConImpuestos(Comprobante c) {
        TotalConImpuestos totalConImpuestos = new TotalConImpuestos();
        for (ImpuestoDetalle impuesto : c.getTotalesPorImpuesto()) {
            TotalConImpuestos.TotalImpuesto ti = new TotalConImpuestos.TotalImpuesto();
            ti.setCodigo(impuesto.getCodigoImpuesto());
            ti.setCodigoPorcentaje(CodigoPorcentajeIva.paraTarifa(impuesto.getTarifa()));
            ti.setBaseImponible(dosDecimales(impuesto.getBaseImponible()));
            ti.setValor(dosDecimales(impuesto.getValor()));
            totalConImpuestos.getTotalImpuesto().add(ti);
        }
        return totalConImpuestos;
    }

    private static NotaCredito.Detalles mapDetalles(Comprobante c) {
        NotaCredito.Detalles detalles = new NotaCredito.Detalles();
        for (DetalleFactura d : c.getDetalles()) {
            NotaCredito.Detalles.Detalle detalle = new NotaCredito.Detalles.Detalle();
            detalle.setCodigoInterno(d.getCodigoPrincipal());
            detalle.setDescripcion(d.getDescripcion());
            detalle.setCantidad(d.getCantidad());
            detalle.setPrecioUnitario(d.getPrecioUnitario());
            detalle.setDescuento(dosDecimales(d.getDescuento()));
            detalle.setPrecioTotalSinImpuesto(dosDecimales(d.getPrecioTotalSinImpuesto()));
            detalle.setImpuestos(mapImpuestosDetalle(d));
            detalles.getDetalle().add(detalle);
        }
        return detalles;
    }

    private static NotaCredito.Detalles.Detalle.Impuestos mapImpuestosDetalle(DetalleFactura d) {
        NotaCredito.Detalles.Detalle.Impuestos impuestos = new NotaCredito.Detalles.Detalle.Impuestos();
        for (ImpuestoDetalle impuestoDetalle : d.getImpuestos()) {
            Impuesto impuesto = new Impuesto();
            impuesto.setCodigo(impuestoDetalle.getCodigoImpuesto());
            impuesto.setCodigoPorcentaje(CodigoPorcentajeIva.paraTarifa(impuestoDetalle.getTarifa()));
            impuesto.setTarifa(impuestoDetalle.getTarifa().multiply(BigDecimal.valueOf(100)));
            impuesto.setBaseImponible(dosDecimales(impuestoDetalle.getBaseImponible()));
            impuesto.setValor(dosDecimales(impuestoDetalle.getValor()));
            impuestos.getImpuesto().add(impuesto);
        }
        return impuestos;
    }

    private static BigDecimal dosDecimales(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** Ver el comentario identico en {@link ComprobanteXmlMapper#vacioANulo}. */
    private static String vacioANulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }
}
