package com.openbravo.pos.sri;

import com.openbravo.pos.sri.config.ConexionLoader;
import com.openbravo.pos.sri.config.ConfiguracionCorreoLoader;
import com.openbravo.pos.sri.config.ConfiguracionLoader;
import com.openbravo.pos.sri.config.RutasConector;
import com.openbravo.pos.sri.correo.NotificadorCorreo;
import com.openbravo.pos.sri.dominio.Comprobante;
import com.openbravo.pos.sri.dominio.ConfiguracionCorreo;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.dominio.EstadoComprobante;
import com.openbravo.pos.sri.dominio.TipoComprobante;
import com.openbravo.pos.sri.envio.EnvioComprobanteService;
import com.openbravo.pos.sri.firma.XadesBesSigner;
import com.openbravo.pos.sri.repository.ComprobanteRepository;
import com.openbravo.pos.sri.repository.TicketCrudo;
import com.openbravo.pos.sri.repository.TicketReader;
import com.openbravo.pos.sri.ride.RideGenerator;
import com.openbravo.pos.sri.scheduler.VigilantePendientes;
import com.openbravo.pos.sri.soap.SoapClient;
import com.openbravo.pos.sri.xml.ComprobanteXmlMapper;
import com.openbravo.pos.sri.xml.FacturaXmlWriter;
import com.openbravo.pos.sri.xml.TicketComprobanteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orquestador que conecta todas las piezas del conector en un proceso
 * corriendo continuamente: {@link VigilantePendientes} recibe los flags que
 * escribe el hook {@code Ticket.Close} de ECOPos, {@link TicketReader} lee el
 * ticket (solo lectura), {@link TicketComprobanteMapper} +
 * {@link ComprobanteXmlMapper}/{@link FacturaXmlWriter} generan el XML,
 * {@link XadesBesSigner} lo firma, {@link SoapClient} lo envia al SRI, y
 * {@link ComprobanteRepository} persiste el progreso en la tabla propia
 * {@code ecopos_sri_comprobantes}.
 *
 * Ademas del procesamiento inmediato disparado por el flag, un
 * {@link ScheduledExecutorService} reintenta periodicamente los comprobantes
 * que quedaron en ERROR o ENVIADO (sin red, SRI caido, o autorizacion aun en
 * PPR) - ver {@link ComprobanteRepository#listarTicketIdsParaReintentar}.
 */
public final class ConectorPrincipal {

    private static final Logger LOG = LoggerFactory.getLogger(ConectorPrincipal.class);

    private static final Path CONFIG_EMISOR_POR_DEFECTO = Path.of("config/datos-emisor.properties");
    private static final Path CONFIG_CONEXION_POR_DEFECTO = Path.of("config/conexion.properties");
    private static final Path CARPETA_PENDIENTES_POR_DEFECTO = Path.of("pendientes");

    /**
     * A diferencia de las 3 constantes de arriba (solo usadas en el modo
     * standalone, main() - CWD-relativas a proposito), esta se usa tambien
     * desde el modo fusionado (intentarEnvioAutomaticoPorCorreo, compartido
     * por ambos modos) - por eso pasa por RutasConector en vez de ser una
     * constante Path.of(...) fija. En modo standalone RutasConector resuelve
     * igual que antes (base = ".", sin cambios de comportamiento).
     */
    private static Path rutaCorreoPorDefecto() {
        return RutasConector.resolver("config/correo.properties");
    }

    private static final int MAX_INTENTOS_REINTENTO = 5;
    private static final long INTERVALO_REINTENTOS_MINUTOS = 15;

    private final DatosEmisor emisor;
    private final TicketReader ticketReader;
    private final ComprobanteRepository comprobanteRepository;
    private final EnvioComprobanteService envioComprobanteService;
    private ScheduledExecutorService reintentosScheduler;

    /** Modo standalone (servicio propio) - abre su propia conexion via un {@link javax.sql.DataSource}. */
    public ConectorPrincipal(DatosEmisor emisor, javax.sql.DataSource dataSource) throws SQLException {
        this(emisor, dataSource.getConnection());
    }

    /**
     * Modo fusionado (mismo proceso que ECOPos) - recibe una conexion ya
     * abierta, dedicada a este conector (no la de ECOPos - ver el puente
     * {@code EcoPosSriBridgeImpl}, que la abre y es quien controla su ciclo
     * de vida). No usa {@link VigilantePendientes}/{@link ConexionLoader} en
     * absoluto: quien invoque {@link #procesarTicket(String)} directamente
     * (el hook de ECOPos) reemplaza al watcher de archivos.
     */
    public ConectorPrincipal(DatosEmisor emisor, java.sql.Connection connection) {
        this.emisor = emisor;
        this.ticketReader = new TicketReader(connection);
        this.comprobanteRepository = new ComprobanteRepository(connection);
        XadesBesSigner firmador = new XadesBesSigner(emisor.getRutaCertificadoP12(), emisor.getClaveCertificado());
        SoapClient soapClient = new SoapClient(emisor.getAmbiente());
        this.envioComprobanteService = new EnvioComprobanteService(firmador, soapClient, comprobanteRepository);
    }

    /**
     * Arranca el planificador de reintentos y luego bloquea el hilo llamador
     * vigilando la carpeta de pendientes (ver {@link VigilantePendientes#iniciar()}).
     * Solo para el modo standalone - el modo fusionado llama
     * {@link #iniciarReintentosPeriodicos()} directamente, sin vigilar carpeta.
     */
    public void iniciar(Path carpetaPendientes) throws IOException {
        iniciarReintentosPeriodicos();
        new VigilantePendientes(carpetaPendientes, this::procesarTicket).iniciar();
    }

    /** Arranca (si no estaba ya arrancado) el reintento periodico de comprobantes en ERROR/ENVIADO. */
    public void iniciarReintentosPeriodicos() {
        if (reintentosScheduler != null) {
            return;
        }
        reintentosScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread hilo = new Thread(runnable, "sri-reintentos");
            hilo.setDaemon(true);
            return hilo;
        });
        reintentosScheduler.scheduleAtFixedRate(this::reintentarPendientes,
                INTERVALO_REINTENTOS_MINUTOS, INTERVALO_REINTENTOS_MINUTOS, TimeUnit.MINUTES);
    }

    /** Detiene el reintento periodico (modo fusionado: llamado al cerrar ECOPos). */
    public void detenerReintentosPeriodicos() {
        if (reintentosScheduler != null) {
            reintentosScheduler.shutdownNow();
            reintentosScheduler = null;
        }
    }

    /**
     * Una pasada de reintento de los comprobantes en ERROR/ENVIADO. Publico
     * para el modo fusionado, cuyo puente la agenda en su propio hilo unico
     * de trabajo (el mismo de los tickets nuevos) en vez de usar el
     * planificador de esta clase - asi nunca hay dos hilos usando la misma
     * conexion ni procesando el mismo ticket a la vez.
     */
    public void reintentarPendientes() {
        try {
            List<String> ticketIds = comprobanteRepository.listarTicketIdsParaReintentar(MAX_INTENTOS_REINTENTO);
            for (String ticketId : ticketIds) {
                try {
                    procesarTicket(ticketId);
                } catch (Exception e) {
                    LOG.warn("Reintento fallido para ticket {}, se reintentara en el siguiente ciclo", ticketId, e);
                }
            }
        } catch (SQLException e) {
            LOG.error("No se pudo listar comprobantes pendientes de reintento", e);
        }
    }

    /**
     * Procesa (o reprocesa) un ticket de principio a fin. Si el ticket ya
     * quedo AUTORIZADO en un intento anterior, no hace nada (idempotente).
     * Si falla en cualquier paso, deja el comprobante en estado ERROR con el
     * detalle en {@code mensajeError} y relanza la excepcion - quien llama
     * (el watcher del flag, o el planificador de reintentos) decide que
     * hacer con eso (el watcher no borra el flag, por lo que se reintentara).
     */
    public void procesarTicket(String ticketId) {
        try {
            procesarTicketInterno(ticketId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Fallo procesando ticket " + ticketId, e);
        }
    }

    private void procesarTicketInterno(String ticketId) throws Exception {
        Optional<ComprobanteRepository.RegistroExistente> registroPrevio =
                comprobanteRepository.buscarPorTicketId(ticketId);

        if (registroPrevio.isPresent() && registroPrevio.get().estado == EstadoComprobante.AUTORIZADO) {
            LOG.info("Ticket {} ya esta AUTORIZADO, no se reprocesa", ticketId);
            return;
        }

        Comprobante comprobante = prepararComprobante(ticketId, true);
        if (comprobante == null) {
            return;
        }
        comprobante.incrementarIntentos();

        String xmlSinFirmar = FacturaXmlWriter.toXml(ComprobanteXmlMapper.map(comprobante));
        envioComprobanteService.firmarEnviarYConsultar(comprobante, xmlSinFirmar);

        intentarEnvioAutomaticoPorCorreo(comprobante);
    }

    /**
     * Arma la factura de un ticket con su numero y clave de acceso, sin
     * enviarla al SRI. Se usa antes de imprimir el ticket, para que el ticket
     * salga ya con los datos de la factura (la clave de acceso es tambien el
     * numero de autorizacion). Idempotente: si ya existia, devuelve la misma.
     *
     * @param reservarSiFalta true para asignar numero (y guardar la fila) si el
     *                        ticket todavia no tiene factura; false solo consulta
     *                        (reimpresiones: nunca crea facturas nuevas).
     * @return el comprobante, o null si el ticket no existe o (sin reservar) no tiene factura
     */
    public Comprobante prepararComprobante(String ticketId, boolean reservarSiFalta) throws Exception {
        Optional<ComprobanteRepository.RegistroExistente> registroPrevio =
                comprobanteRepository.buscarPorTicketId(ticketId);
        if (registroPrevio.isEmpty() && !reservarSiFalta) {
            return null;
        }

        Optional<TicketCrudo> ticketCrudo = ticketReader.leer(ticketId);
        if (ticketCrudo.isEmpty()) {
            LOG.error("Ticket {} no existe en ECOPos - se descarta (el flag no debio generarse)", ticketId);
            return null;
        }

        // El secuencial (y, mas abajo, la claveAcceso) NUNCA se regeneran en
        // un reintento: el SRI exige reenviar exactamente el mismo comprobante
        // cuando se corrige un rechazo (ficha tecnica, seccion 5.10).
        String secuencial = registroPrevio.isPresent()
                ? registroPrevio.get().secuencial
                : comprobanteRepository.siguienteSecuencial(TipoComprobante.FACTURA);

        Comprobante comprobante = TicketComprobanteMapper.map(ticketCrudo.get(), emisor, secuencial);

        if (registroPrevio.isPresent()) {
            ComprobanteRepository.RegistroExistente registro = registroPrevio.get();
            // Reutiliza el id de la fila ya existente - si no, actualizarProgreso()
            // (que hace UPDATE ... WHERE id = ?) apuntaria a un id recien generado
            // que nunca se inserto, y el reintento no actualizaria nada en la tabla.
            comprobante.setId(registro.id);
            comprobante.setIntentos(registro.intentos);
            comprobante.setEstado(registro.estado);
            if (registro.claveAcceso != null) {
                comprobante.setClaveAcceso(registro.claveAcceso);
            }
        } else {
            comprobanteRepository.insertar(comprobante);
        }
        return comprobante;
    }

    /**
     * Si el comprobante quedo AUTORIZADO y el cliente tiene un correo (el
     * que el cajero ingreso al activar "Facturar SRI: SI" en la pantalla de
     * venta - ver {@code script.SriInvoiceOn.txt}/{@code TicketReader}), le
     * envia el XML+RIDE automaticamente. Nunca lanza - un problema de correo
     * (SMTP no configurado, servidor caido, etc.) no debe afectar el
     * resultado de la facturacion en si, que ya quedo resuelto en el SRI. Si
     * no hay correo o no hay {@code config/correo.properties}, se omite en
     * silencio (queda el envio manual desde el Historial como respaldo).
     */
    private void intentarEnvioAutomaticoPorCorreo(Comprobante comprobante) {
        if (comprobante.getEstado() != EstadoComprobante.AUTORIZADO) {
            return;
        }
        String destinatario = comprobante.getCliente().getEmail();
        if (destinatario == null || destinatario.isBlank()) {
            return;
        }
        Path archivoCorreo = rutaCorreoPorDefecto();
        if (!Files.exists(archivoCorreo)) {
            LOG.info("Comprobante {} autorizado con correo de cliente ({}), pero no existe {} - omitiendo envio automatico",
                    comprobante.getId(), destinatario, archivoCorreo);
            return;
        }
        try {
            byte[] pdf = RideGenerator.generar(comprobante.getXmlRespuestaSri(), comprobante.getFechaAutorizacion());
            ConfiguracionCorreo configuracionCorreo = ConfiguracionCorreoLoader.cargar(archivoCorreo);
            byte[] logo = NotificadorCorreo.logoDelNegocio();
            com.openbravo.pos.sri.correo.MensajeComprobante mensaje = com.openbravo.pos.sri.correo.MensajeComprobante
                    .armar(false, comprobante.getXmlRespuestaSri(), logo != null);
            new NotificadorCorreo(configuracionCorreo).enviarComprobante(destinatario, mensaje,
                    comprobante.getXmlRespuestaSri().getBytes(StandardCharsets.UTF_8), pdf, logo);
            LOG.info("Correo enviado automaticamente a {} para el comprobante {}", destinatario, comprobante.getId());
        } catch (Exception e) {
            LOG.warn("No se pudo enviar automaticamente el correo del comprobante {} a {}", comprobante.getId(), destinatario, e);
        }
    }

    public static void main(String[] args) throws Exception {
        Path archivoEmisor = args.length > 0 ? Path.of(args[0]) : CONFIG_EMISOR_POR_DEFECTO;
        Path archivoConexion = args.length > 1 ? Path.of(args[1]) : CONFIG_CONEXION_POR_DEFECTO;
        Path carpetaPendientes = args.length > 2 ? Path.of(args[2]) : CARPETA_PENDIENTES_POR_DEFECTO;

        DatosEmisor emisor = ConfiguracionLoader.cargar(archivoEmisor);
        javax.sql.DataSource dataSource = ConexionLoader.cargar(archivoConexion);

        ConectorPrincipal conector = new ConectorPrincipal(emisor, dataSource);
        LOG.info("ecopos-sri-connector iniciado (ambiente={}, ruc={})", emisor.getAmbiente(), emisor.getRuc());
        conector.iniciar(carpetaPendientes);
    }
}
