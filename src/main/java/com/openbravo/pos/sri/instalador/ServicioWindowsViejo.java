package com.openbravo.pos.sri.instalador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Antes de 2026-07 el conector corria como un servicio de Windows aparte
 * (WinSW, id {@value #ID_SERVICIO}). Ahora corre dentro de EcoPos mismo, y
 * si ese servicio viejo sigue instalado reintentaria los mismos comprobantes
 * que EcoPos - por eso el instalador intenta detenerlo y desinstalarlo.
 *
 * Detener/borrar un servicio requiere una consola "como Administrador"; si
 * no se tiene ese permiso, no se rompe nada: solo se explica que hacer.
 */
final class ServicioWindowsViejo {

    static final String ID_SERVICIO = "ecopos-sri-connector";
    private static final String EXE_WINSW = "ecopos-sri-connector-service.exe";

    private ServicioWindowsViejo() {
    }

    static void retirarSiExiste(Path carpetaConector) {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            return;
        }
        try {
            if (ejecutar(List.of("sc", "query", ID_SERVICIO)) != 0) {
                System.out.println("[=] No hay servicio de Windows viejo del conector instalado.");
                return;
            }
            System.out.println("[!] Se encontro el servicio de Windows viejo '" + ID_SERVICIO + "' - ya no hace falta (el conector corre dentro de EcoPos). Retirandolo...");
            ejecutar(List.of("sc", "stop", ID_SERVICIO));
            Path exe = carpetaConector.resolve(EXE_WINSW);
            int resultado = Files.exists(exe)
                    ? ejecutar(List.of(exe.toAbsolutePath().toString(), "uninstall"))
                    : ejecutar(List.of("sc", "delete", ID_SERVICIO));
            if (resultado == 0 && ejecutar(List.of("sc", "query", ID_SERVICIO)) != 0) {
                System.out.println("[-] Servicio de Windows viejo detenido y desinstalado.");
            } else {
                imprimirInstruccionesManuales();
            }
        } catch (IOException e) {
            imprimirInstruccionesManuales();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void imprimirInstruccionesManuales() {
        System.out.println("[!] No se pudo retirar el servicio viejo automaticamente (hace falta una consola \"como Administrador\").");
        System.out.println("    Abre CMD como Administrador y corre:");
        System.out.println("        sc stop " + ID_SERVICIO);
        System.out.println("        sc delete " + ID_SERVICIO);
        System.out.println("    Mientras siga instalado, podria reintentar comprobantes al mismo tiempo que EcoPos.");
    }

    private static int ejecutar(List<String> comando) throws IOException, InterruptedException {
        Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        proceso.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        if (!proceso.waitFor(60, TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
            return -1;
        }
        return proceso.exitValue();
    }
}
