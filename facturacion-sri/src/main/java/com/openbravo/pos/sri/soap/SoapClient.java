package com.openbravo.pos.sri.soap;

import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.soap.autorizacion.AutorizacionComprobantesOffline;
import com.openbravo.pos.sri.soap.autorizacion.AutorizacionComprobantesOfflineService;
import com.openbravo.pos.sri.soap.autorizacion.RespuestaComprobante;
import com.openbravo.pos.sri.soap.recepcion.RecepcionComprobantesOffline;
import com.openbravo.pos.sri.soap.recepcion.RecepcionComprobantesOfflineService;
import com.openbravo.pos.sri.soap.recepcion.RespuestaSolicitud;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.BindingProvider;
import java.net.URL;

/**
 * Cliente SOAP para los web services offline del SRI (Recepcion y
 * Autorizacion), sobre los stubs generados por CXF a partir del WSDL real
 * (ver {@code src/main/resources/wsdl/}). Elige el endpoint segun el
 * {@link Ambiente} y no guarda ningun estado propio - cada llamada es
 * independiente, los reintentos/estado del comprobante viven en
 * {@code ComprobanteRepository}.
 *
 * IMPORTANTE: las URLs de endpoint (celcer.sri.gob.ec / cel.sri.gob.ec) se
 * obtuvieron de la ficha tecnica oficial del SRI (v2.32, seccion 7.2), pero
 * confirma que sigan vigentes antes de usar en produccion - el SRI ha
 * cambiado endpoints en el pasado sin previo aviso.
 */
public class SoapClient {

    private static final Logger LOG = LoggerFactory.getLogger(SoapClient.class);

    private static final String ENDPOINT_RECEPCION_PRUEBAS =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    private static final String ENDPOINT_AUTORIZACION_PRUEBAS =
            "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline";
    private static final String ENDPOINT_RECEPCION_PRODUCCION =
            "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    private static final String ENDPOINT_AUTORIZACION_PRODUCCION =
            "https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline";

    private static final int CONEXION_TIMEOUT_MS = 15_000;
    private static final int RESPUESTA_TIMEOUT_MS = 30_000;

    private final RecepcionComprobantesOffline recepcionPort;
    private final AutorizacionComprobantesOffline autorizacionPort;

    public SoapClient(Ambiente ambiente) {
        this.recepcionPort = crearPuertoRecepcion(ambiente);
        this.autorizacionPort = crearPuertoAutorizacion(ambiente);
    }

    /** Envia el XML ya firmado (XAdES-BES) a Recepcion. No lanza excepcion en caso de rechazo (DEVUELTA) - eso se lee del estado de la respuesta. */
    public RespuestaSolicitud enviarRecepcion(byte[] xmlFirmado) {
        LOG.debug("Enviando comprobante a Recepcion ({} bytes)", xmlFirmado.length);
        RespuestaSolicitud respuesta = recepcionPort.validarComprobante(xmlFirmado);
        LOG.info("Respuesta de Recepcion: estado={}", respuesta.getEstado());
        return respuesta;
    }

    /** Consulta el estado de autorizacion de un comprobante ya recibido. Puede devolver PPR (en procesamiento) si el SRI aun no termino. */
    public RespuestaComprobante consultarAutorizacion(String claveAcceso) {
        LOG.debug("Consultando autorizacion de claveAcceso={}", claveAcceso);
        RespuestaComprobante respuesta = autorizacionPort.autorizacionComprobante(claveAcceso);
        LOG.info("Respuesta de Autorizacion para {}: {} comprobante(s)", claveAcceso, respuesta.getNumeroComprobantes());
        return respuesta;
    }

    private static RecepcionComprobantesOffline crearPuertoRecepcion(Ambiente ambiente) {
        URL wsdl = copiarWsdlATemporal("/wsdl/RecepcionComprobantesOffline.wsdl");
        RecepcionComprobantesOfflineService service = new RecepcionComprobantesOfflineService(wsdl);
        RecepcionComprobantesOffline port = service.getRecepcionComprobantesOfflinePort();
        String endpoint = ambiente == Ambiente.PRODUCCION ? ENDPOINT_RECEPCION_PRODUCCION : ENDPOINT_RECEPCION_PRUEBAS;
        configurarEndpointYTimeouts(port, endpoint);
        return port;
    }

    private static AutorizacionComprobantesOffline crearPuertoAutorizacion(Ambiente ambiente) {
        URL wsdl = copiarWsdlATemporal("/wsdl/AutorizacionComprobantesOffline.wsdl");
        AutorizacionComprobantesOfflineService service = new AutorizacionComprobantesOfflineService(wsdl);
        AutorizacionComprobantesOffline port = service.getAutorizacionComprobantesOfflinePort();
        String endpoint = ambiente == Ambiente.PRODUCCION ? ENDPOINT_AUTORIZACION_PRODUCCION : ENDPOINT_AUTORIZACION_PRUEBAS;
        configurarEndpointYTimeouts(port, endpoint);
        return port;
    }

    /**
     * Copia el WSDL empaquetado a un archivo temporal y devuelve su URL
     * {@code file:}. Necesario porque CXF 3.6.4 lanza un
     * {@link NullPointerException} dentro de {@code WSDLServiceFactory} al
     * recibir directamente una URL {@code jar:file:...!/...} (confirmado
     * lanzando el conector desde el jar empaquetado, no solo desde el
     * classpath de test de Maven) - un archivo real en disco lo evita.
     */
    private static URL copiarWsdlATemporal(String recursoClasspath) {
        try (java.io.InputStream entrada = SoapClient.class.getResourceAsStream(recursoClasspath)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encontro el WSDL empaquetado: " + recursoClasspath);
            }
            String nombreArchivo = recursoClasspath.substring(recursoClasspath.lastIndexOf('/') + 1);
            java.io.File temporal = java.io.File.createTempFile("ecopos-sri-", "-" + nombreArchivo);
            temporal.deleteOnExit();
            java.nio.file.Files.copy(entrada, temporal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temporal.toURI().toURL();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo copiar el WSDL empaquetado a un archivo temporal: " + recursoClasspath, e);
        }
    }

    private static void configurarEndpointYTimeouts(Object port, String endpoint) {
        ((BindingProvider) port).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);

        Client client = ClientProxy.getClient(port);
        HTTPConduit conduit = (HTTPConduit) client.getConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();
        policy.setConnectionTimeout(CONEXION_TIMEOUT_MS);
        policy.setReceiveTimeout(RESPUESTA_TIMEOUT_MS);
        conduit.setClient(policy);
    }
}
