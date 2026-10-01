package com.openbravo.pos.sri.xml;

import com.openbravo.pos.sri.dominio.Cliente;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.DetalleFactura;
import com.openbravo.pos.sri.dominio.ImpuestoDetalle;
import com.openbravo.pos.sri.dominio.Pago;
import com.openbravo.pos.sri.xml.generado.Factura;
import com.openbravo.pos.sri.xml.generado.Impuesto;
import com.openbravo.pos.sri.xml.generado.InfoTributaria;
import com.openbravo.pos.sri.xml.generado.ObligadoContabilidad;
import com.openbravo.pos.sri.xml.generado.Pagos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Convierte un {@link Comprobante} (dominio, con {@code claveAcceso} ya
 * asignada) en el arbol de clases JAXB generado desde
 * {@code factura_V2.1.0.xsd}, listo para marshalling con
 * {@link FacturaXmlWriter}. No firma ni envia nada - eso vive en otras
 * capas (firma XAdES-BES, cliente SOAP).
 */
public final class ComprobanteXmlMapper {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TIPO_EMISION_NORMAL = "1";
    private static final String MONEDA = "DOLAR";

    private ComprobanteXmlMapper() {
    }

    public static Factura map(Comprobante comprobante) {
        if (comprobante.getClaveAcceso() == null) {
            throw new IllegalStateException(
                    "El comprobante debe tener claveAcceso asignada antes de generar el XML (ticket " +
                    comprobante.getTicketId() + ")");
        }

        Factura factura = new Factura();
        factura.setId("comprobante");
        factura.setVersion("2.1.0");
        factura.setInfoTributaria(mapInfoTributaria(comprobante));
        factura.setInfoFactura(mapInfoFactura(comprobante));
        factura.setDetalles(mapDetalles(comprobante));
        factura.setInfoAdicional(mapInfoAdicional(comprobante));
        return factura;
    }

    /**
     * "Informacion Adicional" con los datos de contacto del cliente (Direccion,
     * Telefono, Email), como en el RIDE habitual del SRI. Solo los que tengan
     * valor; null si no hay ninguno (el XSD no admite infoAdicional vacio).
     */
    private static Factura.InfoAdicional mapInfoAdicional(Comprobante c) {
        Cliente cliente = c.getCliente();
        if (cliente == null) {
            return null;
        }
        Factura.InfoAdicional info = new Factura.InfoAdicional();
        agregarCampo(info, "Dirección", cliente.getDireccion());
        agregarCampo(info, "Teléfono", cliente.getTelefono());
        agregarCampo(info, "Email", cliente.getEmail());
        return info.getCampoAdicional().isEmpty() ? null : info;
    }

    private static void agregarCampo(Factura.InfoAdicional info, String nombre, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        Factura.InfoAdicional.CampoAdicional campo = new Factura.InfoAdicional.CampoAdicional();
        campo.setNombre(nombre);
        String v = valor.trim();
        campo.setValue(v.length() > 300 ? v.substring(0, 300) : v);
        info.getCampoAdicional().add(campo);
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

    private static Factura.InfoFactura mapInfoFactura(Comprobante c) {
        DatosEmisor emisor = c.getEmisor();
        Cliente cliente = c.getCliente();

        Factura.InfoFactura info = new Factura.InfoFactura();
        info.setFechaEmision(c.getFechaEmision().format(FORMATO_FECHA));
        info.setDirEstablecimiento(vacioANulo(emisor.getDirEstablecimiento()));
        info.setContribuyenteEspecial(vacioANulo(emisor.getContribuyenteEspecial()));
        info.setObligadoContabilidad(emisor.isObligadoContabilidad() ? ObligadoContabilidad.SI : ObligadoContabilidad.NO);
        info.setTipoIdentificacionComprador(cliente.getTipoIdentificacion());
        info.setRazonSocialComprador(cliente.getRazonSocial());
        info.setIdentificacionComprador(cliente.getIdentificacion());
        info.setDireccionComprador(cliente.getDireccion());
        info.setTotalSinImpuestos(dosDecimales(c.getTotalSinImpuestos()));
        info.setTotalDescuento(dosDecimales(c.getTotalDescuento()));
        info.setTotalConImpuestos(mapTotalConImpuestos(c));
        info.setImporteTotal(dosDecimales(c.getImporteTotal()));
        info.setMoneda(MONEDA);
        info.setPagos(mapPagos(c));
        return info;
    }

    private static Factura.InfoFactura.TotalConImpuestos mapTotalConImpuestos(Comprobante c) {
        Factura.InfoFactura.TotalConImpuestos totalConImpuestos = new Factura.InfoFactura.TotalConImpuestos();
        for (ImpuestoDetalle impuesto : c.getTotalesPorImpuesto()) {
            Factura.InfoFactura.TotalConImpuestos.TotalImpuesto ti = new Factura.InfoFactura.TotalConImpuestos.TotalImpuesto();
            ti.setCodigo(impuesto.getCodigoImpuesto());
            ti.setCodigoPorcentaje(CodigoPorcentajeIva.paraTarifa(impuesto.getTarifa()));
            ti.setBaseImponible(dosDecimales(impuesto.getBaseImponible()));
            ti.setValor(dosDecimales(impuesto.getValor()));
            totalConImpuestos.getTotalImpuesto().add(ti);
        }
        return totalConImpuestos;
    }

    private static Pagos mapPagos(Comprobante c) {
        Pagos pagos = new Pagos();
        for (Pago pago : c.getPagos()) {
            Pagos.Pago p = new Pagos.Pago();
            p.setFormaPago(pago.getFormaPago().getCodigo());
            p.setTotal(dosDecimales(pago.getTotal()));
            pagos.getPago().add(p);
        }
        return pagos;
    }

    private static Factura.Detalles mapDetalles(Comprobante c) {
        Factura.Detalles detalles = new Factura.Detalles();
        for (DetalleFactura d : c.getDetalles()) {
            Factura.Detalles.Detalle detalle = new Factura.Detalles.Detalle();
            detalle.setCodigoPrincipal(d.getCodigoPrincipal());
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

    private static Factura.Detalles.Detalle.Impuestos mapImpuestosDetalle(DetalleFactura d) {
        Factura.Detalles.Detalle.Impuestos impuestos = new Factura.Detalles.Detalle.Impuestos();
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

    /** Los campos monetarios del comprobante se emiten siempre con 2 decimales (ficha tecnica, Anexo 1). */
    private static BigDecimal dosDecimales(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Varios campos opcionales del XSD (nombreComercial, dirEstablecimiento,
     * contribuyenteEspecial) tienen minOccurs="0" pero tambien un
     * minLength &gt;= 1 en su tipo - un valor en blanco (no nulo) se serializa
     * como una etiqueta vacia (ej. {@code <nombreComercial/>}), que el SRI
     * rechaza igual que si el elemento no debiera estar presente. Tratar
     * blanco como ausente (null, que JAXB omite del todo) evita ese rechazo -
     * confirmado con un envio real al SRI que era devuelto con
     * "35: ARCHIVO NO CUMPLE ESTRUCTURA XML" hasta corregir esto.
     */
    private static String vacioANulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }
}
