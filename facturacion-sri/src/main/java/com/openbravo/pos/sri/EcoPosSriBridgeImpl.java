package com.openbravo.pos.sri;

import com.openbravo.pos.sri.config.ClassLoaderPropio;
import com.openbravo.pos.sri.config.ConexionLoader;
import com.openbravo.pos.sri.config.ConfiguracionLoader;
import com.openbravo.pos.sri.config.RutasConector;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import com.openbravo.pos.sri.ui.ConfiguracionFrame;
import com.openbravo.pos.sri.ui.AccionesComprobante;
import com.openbravo.pos.sri.ui.FilaComprobante;
import com.openbravo.pos.sri.ui.HistorialFrame;
import com.openbravo.pos.sri.ui.MensajesSri;
import com.openbravo.pos.sri.ui.PanelComprobantes;
import com.openbravo.pos.sri.ui.PanelFacturacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementacion real de {@link EcoPosSriBridge} para el modo fusionado
 * (mismo proceso que ECOPos). La instancia {@link EcoPosSriGlue} (lado
 * ECOPos) via reflexion, a traves de un {@code URLClassLoader} dedicado al
 * jar sombreado de este modulo.
 *
 * Todo el trabajo de BD/SOAP (tickets nuevos Y reintentos periodicos) corre
 * en el unico hilo de {@code executor}: nunca hay dos hilos usando la misma
 * conexion, ni procesando el mismo ticket (y pidiendo secuencial) a la vez.
 *
 * El {@link ConectorPrincipal} se construye perezosamente, en ese mismo
 * hilo, justo antes de cada trabajo - y se reconstruye si la conexion se
 * cayo (MySQL cierra conexiones ociosas, reinicio de XAMPP, etc.) o si el
 * usuario cambio {@code datos-emisor.properties} desde la ventana de
 * configuracion. Asi el puente se crea siempre (aunque el emisor todavia no
 * este configurado o MySQL aun no haya levantado al arrancar ECOPos), y la
 * ventana de configuracion se puede abrir justamente para configurarlo.
 */
public final class EcoPosSriBridgeImpl implements EcoPosSriBridge {

    private static final Logger LOG = LoggerFactory.getLogger(EcoPosSriBridgeImpl.class);

    private static final long RETRASO_PRIMER_REINTENTO_MINUTOS = 2;
    private static final long INTERVALO_REINTENTOS_MINUTOS = 15;
    private static final int SEGUNDOS_VALIDAR_CONEXION = 5;

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final Path archivoEmisor;

    // Solo se tocan desde el hilo de executor.
    private Connection connection;
    private ConectorPrincipal conectorPrincipal;
    private FileTime versionEmisorCargada;

    private ScheduledExecutorService reintentosScheduler;

    /**
     * @param urlJdbc         la misma URL JDBC que usa ECOPos ({@code db.URL}).
     * @param usuario         usuario de BD de ECOPos.
     * @param clave           clave de BD de ECOPos, ya descifrada.
     * @param carpetaConector carpeta "sri-conector/" real (fuera del jar) - de ahi salen
     *                        config/datos-emisor.properties, config/correo.properties, etc.
     *                        Primero que nada fija la base de {@link RutasConector}, para
     *                        que ConfiguracionFrame/ConfiguracionCorreoFrame/HistorialFrame/
     *                        ConectorPrincipal (que asumen CWD=sri-conector/ en el modo
     *                        standalone) resuelvan bien sus rutas relativas tambien aqui,
     *                        donde el CWD real es el de ECOPos.
     * @param executor        un unico hilo: serializa todo el trabajo de BD/SOAP del conector.
     */
    public EcoPosSriBridgeImpl(String urlJdbc, String usuario, String clave, Path carpetaConector, ExecutorService executor) {
        RutasConector.establecerCarpetaBase(carpetaConector);
        // Driver MySQL empaquetado en ESTE jar (no el viejo de ECOPos, que ni
        // siquiera esta registrado en DriverManager cuando esto arranca).
        this.dataSource = ConexionLoader.desdeUrl(urlJdbc, usuario, clave);
        ConexionLoader.establecerDataSourceFusionado(dataSource);
        this.executor = executor;
        this.archivoEmisor = RutasConector.resolver("config/datos-emisor.properties");
    }

    /** Solo desde el hilo de executor. Devuelve un conector listo, con conexion viva y emisor al dia. */
    private ConectorPrincipal obtenerConector() throws Exception {
        FileTime versionEmisorActual = Files.exists(archivoEmisor) ? Files.getLastModifiedTime(archivoEmisor) : null;
        boolean conexionViva = connection != null && connection.isValid(SEGUNDOS_VALIDAR_CONEXION);
        boolean emisorCambio = versionEmisorActual == null || !versionEmisorActual.equals(versionEmisorCargada);

        if (conectorPrincipal != null && conexionViva && !emisorCambio) {
            return conectorPrincipal;
        }
        if (versionEmisorActual == null) {
            throw new IllegalStateException("Falta " + archivoEmisor + " - configura los datos del emisor SRI primero");
        }
        if (!conexionViva) {
            cerrarConexionSilenciosamente();
            connection = dataSource.getConnection();
            LOG.info("Conexion dedicada del conector SRI (re)abierta");
        }
        DatosEmisor emisor = ConfiguracionLoader.cargar(archivoEmisor);
        conectorPrincipal = new ConectorPrincipal(emisor, connection);
        versionEmisorCargada = versionEmisorActual;
        LOG.info("Conector SRI listo (ambiente={}, ruc={})", emisor.getAmbiente(), emisor.getRuc());
        return conectorPrincipal;
    }

    @Override
    public int versionContrato() {
        return VERSION_CONTRATO;
    }

    @Override
    public void procesarTicketAsync(String ticketId) {
        executor.submit(() -> {
            ClassLoaderPropio.fijarEnHiloActual();
            try {
                obtenerConector().procesarTicket(ticketId);
            } catch (Throwable e) {
                // Un Runnable enviado a un ExecutorService que termina con una
                // excepcion no capturada se traga en silencio (Future.get()
                // la reportaria, pero aqui no se llama get()) - sin este
                // catch, un fallo real del conector no dejaria rastro alguno.
                // El reintento periodico lo vuelve a intentar si quedo en ERROR.
                LOG.error("Fallo procesando el ticket {} en el modo fusionado", ticketId, e);
            }
        });
    }

    @Override
    public javax.swing.JComponent crearPanelFacturacion() {
        ClassLoaderPropio.fijarEnHiloActual();
        return new PanelFacturacion();
    }

    @Override
    public javax.swing.JComponent crearPanelComprobantes() {
        ClassLoaderPropio.fijarEnHiloActual();
        return new PanelComprobantes();
    }

    @Override
    public String[] estadoFacturaDeTicket(String ticketId) {
        FilaComprobante fila = filaDeTicket(ticketId);
        if (fila == null) {
            return null;
        }
        return new String[]{fila.estado.name(), fila.numero,
                fila.estado == com.openbravo.pos.sri.dominio.EstadoComprobante.AUTORIZADO ? null : MensajesSri.explicar(fila.mensajeError)};
    }

    /** Espera maxima para preparar la factura antes de imprimir (el cajero no debe quedarse esperando). */
    private static final long SEGUNDOS_PREPARAR_FACTURA = 6;

    @Override
    public java.util.Map<String, String> facturaParaTicket(String ticketId, boolean reservarSiFalta) {
        java.util.concurrent.Future<java.util.Map<String, String>> tarea = executor.submit(() -> {
            ClassLoaderPropio.fijarEnHiloActual();
            com.openbravo.pos.sri.dominio.Comprobante c = obtenerConector().prepararComprobante(ticketId, reservarSiFalta);
            return c == null ? null : datosParaTicket(c);
        });
        try {
            return tarea.get(SEGUNDOS_PREPARAR_FACTURA, TimeUnit.SECONDS);
        } catch (Exception e) {
            // El ticket se imprime igual (sin los datos de la factura); la factura sigue su curso.
            LOG.warn("No se pudo preparar a tiempo la factura del ticket {} para imprimirla", ticketId, e);
            return null;
        }
    }

    private static java.util.Map<String, String> datosParaTicket(com.openbravo.pos.sri.dominio.Comprobante c) {
        java.util.Map<String, String> d = new java.util.LinkedHashMap<>();
        d.put("numero", c.getEmisor().getEstablecimiento() + "-" + c.getEmisor().getPuntoEmision() + "-" + c.getSecuencial());
        d.put("claveAcceso", c.getClaveAcceso());
        d.put("ambiente", c.getAmbiente() == com.openbravo.pos.sri.dominio.Ambiente.PRODUCCION ? "PRODUCCION" : "PRUEBAS");
        d.put("emision", "NORMAL");
        d.put("fechaEmision", c.getFechaEmision() != null
                ? c.getFechaEmision().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
        d.put("estado", c.getEstado() != null ? c.getEstado().name() : "");
        d.put("compradorRazonSocial", vacio(c.getCliente().getRazonSocial()));
        d.put("compradorIdentificacion", vacio(c.getCliente().getIdentificacion()));
        d.put("compradorDireccion", vacio(c.getCliente().getDireccion()));
        d.put("compradorEmail", vacio(c.getCliente().getEmail()));
        StringBuilder pagos = new StringBuilder();
        for (com.openbravo.pos.sri.dominio.Pago p : c.getPagos()) {
            String desc = p.getFormaPago().getDescripcion();
            if (pagos.indexOf(desc) < 0) {
                pagos.append(pagos.length() == 0 ? "" : "|").append(desc);
            }
        }
        d.put("formasPago", pagos.toString());
        return d;
    }

    private static String vacio(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public void verRideDeTicket(java.awt.Component padre, String ticketId) {
        ClassLoaderPropio.fijarEnHiloActual();
        FilaComprobante fila = filaDeTicket(ticketId);
        if (fila != null) {
            AccionesComprobante.verRide(padre, fila);
        }
    }

    @Override
    public void notaCreditoDeTicket(java.awt.Component padre, String ticketId) {
        ClassLoaderPropio.fijarEnHiloActual();
        FilaComprobante fila = filaDeTicket(ticketId);
        if (fila != null) {
            AccionesComprobante.notaCredito(padre, fila, null);
        }
    }

    /** Lectura rapida con su propia conexion (no la del hilo de trabajo, que puede estar esperando al SRI). */
    private FilaComprobante filaDeTicket(String ticketId) {
        try (java.sql.Connection con = dataSource.getConnection()) {
            return FilaComprobante.deTicket(con, ticketId);
        } catch (Exception e) {
            LOG.warn("No se pudo leer la factura del ticket {}", ticketId, e);
            return null;
        }
    }

    @Override
    public void abrirConfiguracionEmisor() {
        SwingUtilities.invokeLater(() -> {
            ClassLoaderPropio.fijarEnHiloActual();
            new ConfiguracionFrame().setVisible(true);
        });
    }

    @Override
    public void abrirConfiguracionCorreo() {
        SwingUtilities.invokeLater(() -> {
            ClassLoaderPropio.fijarEnHiloActual();
            new ConfiguracionFrame().setVisible(true);
        });
    }

    @Override
    public void abrirHistorial() {
        SwingUtilities.invokeLater(() -> {
            ClassLoaderPropio.fijarEnHiloActual();
            new HistorialFrame().setVisible(true);
        });
    }

    @Override
    public synchronized void iniciarReintentosPeriodicos() {
        if (reintentosScheduler != null) {
            return;
        }
        reintentosScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread hilo = new Thread(runnable, "sri-reintentos");
            hilo.setDaemon(true);
            return hilo;
        });
        // El planificador solo ENCOLA la pasada en el hilo unico de trabajo.
        reintentosScheduler.scheduleWithFixedDelay(() -> executor.submit(this::reintentarUnaVez),
                RETRASO_PRIMER_REINTENTO_MINUTOS, INTERVALO_REINTENTOS_MINUTOS, TimeUnit.MINUTES);
    }

    private void reintentarUnaVez() {
        if (!Files.exists(archivoEmisor)) {
            return; // conector instalado pero todavia sin configurar - nada que reintentar
        }
        ClassLoaderPropio.fijarEnHiloActual();
        try {
            obtenerConector().reintentarPendientes();
        } catch (Throwable e) {
            LOG.error("Fallo la pasada de reintentos del conector SRI", e);
        }
    }

    @Override
    public synchronized void detenerReintentosPeriodicos() {
        if (reintentosScheduler != null) {
            reintentosScheduler.shutdownNow();
            reintentosScheduler = null;
        }
    }

    @Override
    public void cerrar() {
        detenerReintentosPeriodicos();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        cerrarConexionSilenciosamente();
    }

    private void cerrarConexionSilenciosamente() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.warn("No se pudo cerrar limpiamente la conexion dedicada del conector", e);
            }
            connection = null;
        }
    }
}
