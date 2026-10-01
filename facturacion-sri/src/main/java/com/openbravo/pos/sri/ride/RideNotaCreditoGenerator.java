package com.openbravo.pos.sri.ride;

import com.openbravo.pos.sri.xml.generado.notacredito.NotaCredito;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * RIDE de una Nota de Credito - mismo formato que {@link RideGenerator} (lee el
 * XML ya AUTORIZADO por el SRI y lo dibuja con {@link RideRenderer}), con
 * dos diferencias de contenido que exige el Anexo 2 para este tipo de
 * documento: el titulo dice "NOTA DE CREDITO" y hay un bloque obligatorio
 * que referencia la factura que esta nota modifica (numero, fecha y motivo).
 */
public final class RideNotaCreditoGenerator {

    private RideNotaCreditoGenerator() {
    }

    public static byte[] generar(String xmlAutorizado, LocalDateTime fechaAutorizacion) throws IOException {
        return RideRenderer.generar(modelo(desmarshallar(xmlAutorizado), fechaAutorizacion));
    }

    static ModeloRide modelo(NotaCredito nc, LocalDateTime fechaAutorizacion) {
        var it = nc.getInfoTributaria();
        var info = nc.getInfoNotaCredito();
        ModeloRide r = new ModeloRide();
        r.tipo = "NOTA DE CRÉDITO";
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
        r.docModificado = info.getNumDocModificado();
        r.fechaDocModificado = info.getFechaEmisionDocSustento();
        r.motivo = info.getMotivo();

        BigDecimal descuento = BigDecimal.ZERO;
        for (NotaCredito.Detalles.Detalle d : nc.getDetalles().getDetalle()) {
            ModeloRide.Linea l = new ModeloRide.Linea();
            l.codigo = d.getCodigoInterno();
            l.descripcion = d.getDescripcion();
            l.cantidad = d.getCantidad();
            l.precioUnitario = d.getPrecioUnitario();
            l.descuento = d.getDescuento();
            l.total = d.getPrecioTotalSinImpuesto();
            if (d.getDescuento() != null) {
                descuento = descuento.add(d.getDescuento());
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
        r.totalDescuento = descuento;
        r.total = info.getValorModificacion();
        r.etiquetaTotal = "VALOR TOTAL A ACREDITAR";
        if (nc.getInfoAdicional() != null) {
            for (var c : nc.getInfoAdicional().getCampoAdicional()) {
                r.infoAdicional.add(new String[]{c.getNombre(), c.getValue()});
            }
        }
        return r;
    }

    private static NotaCredito desmarshallar(String xml) throws IOException {
        try {
            JAXBContext contexto = JAXBContext.newInstance(NotaCredito.class);
            Unmarshaller unmarshaller = contexto.createUnmarshaller();
            return (NotaCredito) unmarshaller.unmarshal(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("No se pudo leer el XML autorizado para generar el RIDE de la nota de credito", e);
        }
    }
}
