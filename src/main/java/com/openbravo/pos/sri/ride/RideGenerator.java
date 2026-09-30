package com.openbravo.pos.sri.ride;

import com.openbravo.pos.sri.xml.FacturaXmlReader;
import com.openbravo.pos.sri.xml.generado.Factura;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Genera la Representacion Impresa de Documento Electronico (RIDE) de una
 * factura en PDF a partir del XML autorizado por el SRI, con el formato
 * habitual del SRI (ver {@link RideRenderer}) y todos los campos que exige el
 * Anexo 2 de la ficha tecnica: RUC, numero, numero de autorizacion, clave de
 * acceso + codigo de barras, ambiente, emision, datos del emisor y comprador,
 * detalle, subtotales por tarifa, informacion adicional y forma de pago. El
 * PDF tiene validez tributaria igual que cualquier otra representacion
 * impresa que cumpla esos requisitos (Resolucion 233, junio 2018, seccion
 * 8.19 de la ficha tecnica).
 *
 * Recibe el XML ya AUTORIZADO (el que el SRI devuelve dentro de
 * {@code <autorizacion><comprobante>}, guardado en
 * {@code ecopos_sri_comprobantes.xml_respuesta_sri}) y lo desmarshalla con
 * JAXB sobre las mismas clases generadas del XSD que usa
 * {@link com.openbravo.pos.sri.xml.ComprobanteXmlMapper} - el
 * {@code ds:Signature} incrustado se ignora, solo interesan los datos del
 * comprobante.
 */
public final class RideGenerator {

    private RideGenerator() {
    }

    public static byte[] generar(String xmlAutorizado) throws IOException {
        return generar(xmlAutorizado, null);
    }

    /**
     * @param fechaAutorizacion fecha/hora en que el SRI autorizo el comprobante
     *                          (columna {@code ecopos_sri_comprobantes.fecha_autorizacion} -
     *                          no viaja dentro del XML del comprobante, solo en la
     *                          respuesta completa de autorizacion). Puede ser null
     *                          si aun no se conoce.
     */
    public static byte[] generar(String xmlAutorizado, LocalDateTime fechaAutorizacion) throws IOException {
        return RideRenderer.generar(modelo(FacturaXmlReader.leer(xmlAutorizado), fechaAutorizacion));
    }

    static ModeloRide modelo(Factura factura, LocalDateTime fechaAutorizacion) {
        var it = factura.getInfoTributaria();
        var info = factura.getInfoFactura();
        ModeloRide r = new ModeloRide();
        r.tipo = "FACTURA";
        r.ruc = it.getRuc();
        r.numero = it.getEstab() + "-" + it.getPtoEmi() + "-" + it.getSecuencial();
        r.claveAcceso = it.getClaveAcceso();
        r.ambiente = ModeloRide.ambiente(it.getAmbiente());
        r.emision = ModeloRide.emision(it.getTipoEmision());
        r.fechaAutorizacion = fechaAutorizacion;
        r.razonSocial = it.getRazonSocial();
        r.nombreComercial = it.getNombreComercial();
        r.dirMatriz = it.getDirMatriz();
        r.agenteRetencion = it.getAgenteRetencion();
        r.contribuyenteRimpe = it.getContribuyenteRimpe();
        r.dirEstablecimiento = info.getDirEstablecimiento();
        r.contribuyenteEspecial = info.getContribuyenteEspecial();
        r.obligadoContabilidad = info.getObligadoContabilidad() != null ? info.getObligadoContabilidad().value() : "NO";

        r.compradorRazonSocial = info.getRazonSocialComprador();
        r.compradorIdentificacion = info.getIdentificacionComprador();
        r.fechaEmision = info.getFechaEmision();
        r.guiaRemision = info.getGuiaRemision();
        r.compradorDireccion = info.getDireccionComprador();

        for (Factura.Detalles.Detalle d : factura.getDetalles().getDetalle()) {
            ModeloRide.Linea l = new ModeloRide.Linea();
            l.codigo = d.getCodigoPrincipal();
            l.descripcion = d.getDescripcion();
            l.cantidad = d.getCantidad();
            l.precioUnitario = d.getPrecioUnitario();
            l.descuento = d.getDescuento();
            l.total = d.getPrecioTotalSinImpuesto();
            if (d.getPrecioSinSubsidio() != null) {
                l.adicionales.add("Precio sin subsidio: " + d.getPrecioSinSubsidio().toPlainString());
            }
            if (d.getDetallesAdicionales() != null) {
                for (var ad : d.getDetallesAdicionales().getDetAdicional()) {
                    l.adicionales.add(ad.getNombre() + ": " + ad.getValor());
                }
            }
            r.detalle.add(l);
        }

        if (info.getTotalConImpuestos() != null) {
            for (var ti : info.getTotalConImpuestos().getTotalImpuesto()) {
                r.sumarImpuesto(ti.getCodigo(), ti.getCodigoPorcentaje(), ti.getBaseImponible(), ti.getValor());
            }
        }
        r.subtotalSinImpuestos = info.getTotalSinImpuestos();
        r.totalDescuento = info.getTotalDescuento();
        r.propina = info.getPropina() != null ? info.getPropina() : java.math.BigDecimal.ZERO;
        r.total = info.getImporteTotal();

        if (info.getPagos() != null) {
            for (var p : info.getPagos().getPago()) {
                ModeloRide.Pago pago = new ModeloRide.Pago();
                pago.descripcion = ModeloRide.formaPago(p.getFormaPago());
                pago.valor = p.getTotal();
                pago.plazo = p.getPlazo() != null ? p.getPlazo().stripTrailingZeros().toPlainString() : "";
                pago.tiempo = p.getUnidadTiempo();
                r.pagos.add(pago);
            }
        }
        if (factura.getInfoAdicional() != null) {
            for (var c : factura.getInfoAdicional().getCampoAdicional()) {
                r.infoAdicional.add(new String[]{c.getNombre(), c.getValue()});
            }
        }
        return r;
    }
}
