package com.openbravo.pos.sri.config;

import java.nio.file.Path;

/**
 * Resuelve las rutas relativas de configuracion ("config/datos-emisor.properties",
 * etc.) contra una carpeta base ajustable.
 *
 * En modo standalone (servicio propio, {@code ConectorPrincipal.main}) la
 * base es "." (el directorio de trabajo del proceso, tipicamente
 * {@code sri-conector/} - sin cambios respecto al comportamiento historico).
 *
 * En modo fusionado (mismo proceso que ECOPos) el directorio de trabajo es
 * el de ECOPos, no el de {@code sri-conector/} - {@code EcoPosSriBridgeImpl}
 * llama {@link #establecerCarpetaBase(Path)} con la carpeta real de
 * {@code sri-conector/} como primer paso de su construccion, antes de tocar
 * cualquier clase que dependa de estas rutas (PanelFacturacion,
 * PanelComprobantes, ConectorPrincipal).
 */
public final class RutasConector {

    private static volatile Path carpetaBase = Path.of(".");

    private RutasConector() {
    }

    /** Llamar una sola vez, antes de construir cualquier cosa que dependa de {@link #resolver(String)}. */
    public static void establecerCarpetaBase(Path carpeta) {
        carpetaBase = carpeta;
    }

    public static Path resolver(String rutaRelativa) {
        return carpetaBase.resolve(rutaRelativa);
    }
}
