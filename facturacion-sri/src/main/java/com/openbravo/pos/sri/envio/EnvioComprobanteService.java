package com.openbravo.pos.sri.envio;

import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.firma.XadesBesSigner;
import com.openbravo.pos.sri.repository.ComprobanteRepository;
import com.openbravo.pos.sri.soap.SoapClient;
import com.openbravo.pos.sri.soap.autorizacion.Autorizacion;
import com.openbravo.pos.sri.soap.autorizacion.RespuestaComprobante;
import com.openbravo.pos.sri.soap.recepcion.RespuestaSolicitud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Firma, envia a Recepcion, consulta Autorizacion y persiste el resultado de
 * CUALQUIER comprobante ya construido (factura o nota de credito) - el SRI
 * no distingue el tipo de comprobante en este flujo, solo recibe bytes de
 * un XML firmado y lo valida. Extraido de {@code ConectorPrincipal} (que
 * originalmente solo procesaba facturas) para que
 * {@code AnulacionService} (notas de credito) reuse exactamente la misma
 * logica en vez de duplicarla.
 */
public final class EnvioComprobanteService {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioComprobanteService.class);
    private static final long ESPERA_ANTES_DE_CONSULTAR_AUTORIZACION_MS = 3000;

    private final XadesBesSigner firmador;
    private final SoapClient soapClient;
    private final ComprobanteRepository comprobanteRepository;

    public EnvioComprobanteService(XadesBesSigner firmador, SoapClient soapClient, ComprobanteRepository comprobanteRepository) {
        this.firmador = firmador;
        this.soapClient = soapClient;
        this.comprobanteRepository = comprobanteRepository;
    }

    /**
     * Firma {@code xmlSinFirmar}, lo envia y consulta su autorizacion,
     * actualizando {@code comprobante} y persistiendo su progreso en cada
     * paso (igual que hacia {@code ConectorPrincipal.procesarTicketInterno}).
     * Deja el comprobante en ERROR (con la excepcion en {@code mensajeError})
     * y relanza si algo falla.
     */
    public void firmarEnviarYConsultar(Comprobante comprobante, String xmlSinFirmar) throws Exception {
        comprobante.setXmlGenerado(xmlSinFirmar);
        try {
            String xmlFirmado = firmador.firmar(xmlSinFirmar);
            comprobante.setXmlFirmado(xmlFirmado);

            RespuestaSolicitud respuestaRecepcion =
                    soapClient.enviarRecepcion(xmlFirmado.getBytes(StandardCharsets.UTF_8));

            if (!"RECIBIDA".equalsIgnoreCase(respuestaRecepcion.getEstado())) {
                comprobante.setEstado(EstadoComprobante.RECHAZADO);
                comprobante.setMensajeError(resumirRecepcion(respuestaRecepcion));
                comprobanteRepository.actualizarProgreso(comprobante);
                LOG.warn("Comprobante {} rechazado en Recepcion: {}", comprobante.getId(), comprobante.getMensajeError());
                return;
            }

            comprobante.setEstado(EstadoComprobante.ENVIADO);
            comprobanteRepository.actualizarProgreso(comprobante);

            // El SRI no autoriza de forma sincrona con la recepcion; hay que
            // esperar un momento antes de poder consultar la autorizacion.
            Thread.sleep(ESPERA_ANTES_DE_CONSULTAR_AUTORIZACION_MS);

            RespuestaComprobante respuestaAutorizacion = soapClient.consultarAutorizacion(comprobante.getClaveAcceso());
            aplicarRespuestaAutorizacion(comprobante, respuestaAutorizacion);
            comprobanteRepository.actualizarProgreso(comprobante);

        } catch (Exception e) {
            comprobante.setEstado(EstadoComprobante.ERROR);
            comprobante.setMensajeError(e.toString());
            comprobanteRepository.actualizarProgreso(comprobante);
            throw e;
        }
    }

    private static void aplicarRespuestaAutorizacion(Comprobante comprobante, RespuestaComprobante respuesta) {
        if (respuesta.getAutorizaciones() == null || respuesta.getAutorizaciones().getAutorizacion().isEmpty()) {
            // Sin autorizaciones todavia (SRI aun procesando, "PPR"): se deja
            // en ENVIADO para que el planificador de reintentos vuelva a
            // consultar en el siguiente ciclo.
            comprobante.setXmlRespuestaSri("Sin autorizacion todavia (en procesamiento)");
            return;
        }

        Autorizacion autorizacion = respuesta.getAutorizaciones().getAutorizacion().get(0);
        comprobante.setXmlRespuestaSri(autorizacion.getComprobante());

        if ("AUTORIZADO".equalsIgnoreCase(autorizacion.getEstado())) {
            comprobante.setEstado(EstadoComprobante.AUTORIZADO);
            comprobante.setNumeroAutorizacion(autorizacion.getNumeroAutorizacion());
            comprobante.setFechaAutorizacion(LocalDateTime.now());
        } else {
            comprobante.setEstado(EstadoComprobante.RECHAZADO);
            comprobante.setMensajeError(resumirAutorizacion(autorizacion));
        }
    }

    private static String resumirRecepcion(RespuestaSolicitud respuesta) {
        StringBuilder resumen = new StringBuilder("Estado: ").append(respuesta.getEstado());
        if (respuesta.getComprobantes() != null) {
            for (var comprobante : respuesta.getComprobantes().getComprobante()) {
                if (comprobante.getMensajes() == null) {
                    continue;
                }
                for (var mensaje : comprobante.getMensajes().getMensaje()) {
                    resumen.append(" | ").append(mensaje.getIdentificador()).append(": ").append(mensaje.getMensaje());
                }
            }
        }
        return resumen.toString();
    }

    private static String resumirAutorizacion(Autorizacion autorizacion) {
        StringBuilder resumen = new StringBuilder("Estado: ").append(autorizacion.getEstado());
        if (autorizacion.getMensajes() != null) {
            for (var mensaje : autorizacion.getMensajes().getMensaje()) {
                resumen.append(" | ").append(mensaje.getIdentificador()).append(": ").append(mensaje.getMensaje());
            }
        }
        return resumen.toString();
    }
}
