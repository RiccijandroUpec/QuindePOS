package com.openbravo.pos.sri.scheduler;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vigila la carpeta "sri-conector/pendientes" que el script Ticket.Close de
 * ECOPos usa como unico punto de integracion (ver
 * src-pos/com/openbravo/pos/templates/Ticket.Close.xml en el repo de
 * ECOPos). Por cada archivo `<ticketId>.flag` que aparece, invoca el
 * callback con el ticketId y borra el flag.
 *
 * No depende de ninguna clase de ECOPos: el contrato es exclusivamente
 * "un archivo con ese nombre existe en esa carpeta".
 *
 * Ademas del watch en tiempo real, hace un barrido inicial al arrancar
 * (por si el conector estuvo apagado y se acumularon flags), y expone
 * {@link #escanearPendientesAhora()} para que el planificador de
 * reintentos pueda forzar una re-conciliacion periodica sin depender
 * unicamente de eventos del sistema de archivos (que en algunos
 * filesystems/red pueden perderse).
 */
public class VigilantePendientes implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VigilantePendientes.class);

    private final Path carpetaPendientes;
    private final Consumer<String> alRecibirTicket;
    private WatchService watchService;
    private volatile boolean activo = true;

    public VigilantePendientes(Path carpetaPendientes, Consumer<String> alRecibirTicket) {
        this.carpetaPendientes = carpetaPendientes;
        this.alRecibirTicket = alRecibirTicket;
    }

    /**
     * Arranca el hilo de vigilancia. Bloquea el hilo llamador - se espera
     * que se invoque desde un hilo dedicado (ver Main/scheduler del
     * conector).
     */
    public void iniciar() throws IOException {
        Files.createDirectories(carpetaPendientes);
        escanearPendientesAhora();

        watchService = FileSystems.getDefault().newWatchService();
        carpetaPendientes.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        log.info("Vigilando carpeta de pendientes: {}", carpetaPendientes.toAbsolutePath());

        while (activo) {
            WatchKey key;
            try {
                key = watchService.poll(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (key == null) {
                // nada nuevo en este intervalo; igual re-escaneamos por si
                // el evento de filesystem se perdio (ej. sobre un recurso
                // de red)
                escanearPendientesAhora();
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                Object contexto = event.context();
                if (contexto != null) {
                    procesarNombreArchivo(contexto.toString());
                }
            }
            if (!key.reset()) {
                log.warn("El WatchKey para {} ya no es valido; se reintentara registrar", carpetaPendientes);
                break;
            }
        }
    }

    /** Barrido manual e idempotente de la carpeta; seguro de llamar repetidamente. */
    public void escanearPendientesAhora() {
        try (var stream = Files.list(carpetaPendientes)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".flag"))
                  .forEach(p -> procesarNombreArchivo(p.getFileName().toString()));
        } catch (IOException e) {
            log.error("No se pudo listar la carpeta de pendientes {}", carpetaPendientes, e);
        }
    }

    private void procesarNombreArchivo(String nombreArchivo) {
        if (!nombreArchivo.endsWith(".flag")) {
            return;
        }
        String ticketId = nombreArchivo.substring(0, nombreArchivo.length() - ".flag".length());
        Path flag = carpetaPendientes.resolve(nombreArchivo);
        if (!Files.exists(flag)) {
            return; // ya fue procesado y borrado por otra pasada
        }
        try {
            alRecibirTicket.accept(ticketId);
            Files.deleteIfExists(flag);
        } catch (Exception e) {
            // No borramos el flag si el procesamiento fallo: la proxima
            // pasada (o el siguiente arranque) lo volvera a intentar. El
            // reintento fino por comprobante (SRI caido, etc.) vive en
            // ecopos_sri_comprobantes.estado, no en este archivo flag.
            log.error("Error procesando ticket {} desde flag {}; se reintentara", ticketId, flag, e);
        }
    }

    @Override
    public void close() {
        activo = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error cerrando WatchService", e);
            }
        }
    }
}
