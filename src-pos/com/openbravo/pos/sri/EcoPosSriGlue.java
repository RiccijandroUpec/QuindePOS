package com.openbravo.pos.sri;

import com.openbravo.pos.forms.AppProperties;
import com.openbravo.pos.util.AltEncrypter;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Carga ecopos-sri-connector (un modulo Maven separado, jar sombreado) en el
 * mismo proceso/JVM que ECOPos, sin agregarlo nunca al classpath propio de
 * ECOPos (start.bat no cambia): el jar se ubica en tiempo de ejecucion desde
 * una ruta relativa fija y se carga con un {@link ClassLoaderConector}
 * (child-first: las librerias del conector ganan sobre las versiones viejas
 * que trae ECOPos en lib/ - wsdl4j, saaj, JavaMail, etc.; un URLClassLoader
 * normal parent-first rompia la firma y el SOAP en tiempo de ejecucion). El
 * hilo de trabajo del conector usa ese classloader tambien como context
 * classloader (JAXB/CXF/jakarta.mail buscan sus implementaciones por ahi).
 *
 * Si el jar no existe (SRI fusionado no instalado) o la carga por reflexion
 * falla, {@link #getInstance(AppProperties)} devuelve {@code null} - un
 * problema aca nunca debe impedir que ECOPos mismo arranque. La conexion a
 * BD y la configuracion del emisor NO se tocan aqui: el conector las abre
 * perezosamente (y las reabre si hace falta) en su propio hilo, asi que ni
 * un MySQL todavia apagado ni un emisor sin configurar dejan el puente
 * desactivado para siempre.
 */
public final class EcoPosSriGlue {

    private static final Logger LOG = Logger.getLogger(EcoPosSriGlue.class.getName());

    private static final String CARPETA_CONECTOR_RELATIVA = "sri-conector";
    private static final String RUTA_JAR_RELATIVA = CARPETA_CONECTOR_RELATIVA + "/ecopos-sri-connector.jar";

    private static EcoPosSriBridge instancia;
    private static String problema;
    private static ClassLoaderConector classLoaderConector;
    private static boolean inicializado;

    private EcoPosSriGlue() {
    }

    /**
     * Arranca el puente (si el jar del conector esta instalado) y su
     * reintento periodico. Pensado para llamarse una vez al arrancar ECOPos
     * ({@code StartPOS}); nunca lanza.
     */
    public static synchronized void startAtBoot(AppProperties propiedades) {
        EcoPosSriBridge bridge = getInstance(propiedades);
        if (bridge != null) {
            bridge.iniciarReintentosPeriodicos();
        }
    }

    /** Devuelve el puente ya inicializado, o lo inicializa la primera vez. Null si no esta instalado o algo fallo. */
    public static synchronized EcoPosSriBridge getInstance(AppProperties propiedades) {
        if (inicializado) {
            return instancia;
        }
        inicializado = true;
        try {
            instancia = construir(propiedades);
            if (instancia != null) {
                // Clases anonimas en vez de lambdas: build_working.xml compila ECOPos con -source 1.7.
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        shutdown();
                    }
                }, "sri-conector-shutdown"));
            }
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "No se pudo inicializar ecopos-sri-connector (modo fusionado) - la facturacion SRI queda desactivada", e);
            instancia = null;
        }
        return instancia;
    }

    private static EcoPosSriBridge construir(AppProperties propiedades) throws Exception {
        File dirname = new File(System.getProperty("dirname.path", "./"));
        File archivoJar = new File(dirname, RUTA_JAR_RELATIVA);
        if (!archivoJar.exists()) {
            LOG.info("No existe " + archivoJar + " - ecopos-sri-connector no esta instalado, se omite");
            return null;
        }

        classLoaderConector = new ClassLoaderConector(
                new URL[]{archivoJar.toURI().toURL()},
                EcoPosSriGlue.class.getClassLoader());

        Path carpetaConector = new File(dirname, CARPETA_CONECTOR_RELATIVA).toPath();
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread hilo = new Thread(runnable, "sri-conector-trabajo");
                hilo.setDaemon(true);
                hilo.setContextClassLoader(classLoaderConector);
                return hilo;
            }
        });

        // El conector abre (y reabre si se cae) su PROPIA conexion con el
        // driver MySQL empaquetado en su jar - NO DriverManager desde aqui: el
        // driver de ECOPos se registra recien al crear la sesion (despues de
        // startAtBoot) y desde un classloader propio, asi que aqui no estaria
        // disponible. Mismas credenciales que ECOPos (AppConfig), descifrando
        // la clave igual que AppViewConnection.createSession.
        String usuario = propiedades.getProperty("db.user");
        String clave = propiedades.getProperty("db.password");
        if (usuario != null && clave != null && clave.startsWith("crypt:")) {
            AltEncrypter cypher = new AltEncrypter("cypherkey" + usuario);
            clave = cypher.decrypt(clave.substring(6));
        }

        Class<?> claseImpl = classLoaderConector.loadClass("com.openbravo.pos.sri.EcoPosSriBridgeImpl");
        Constructor<?> constructor = claseImpl.getConstructor(String.class, String.class, String.class, Path.class, ExecutorService.class);
        EcoPosSriBridge puente = (EcoPosSriBridge) constructor.newInstance(
                propiedades.getProperty("db.URL"), usuario, clave, carpetaConector, executor);

        // El modulo tiene que estar compilado con la misma version del contrato que Quinde POS:
        // si no, falla con errores raros a mitad de una venta. Mejor detectarlo aqui y avisar.
        int versionModulo;
        try {
            versionModulo = puente.versionContrato();
        } catch (AbstractMethodError e) {
            versionModulo = 1;
        }
        if (versionModulo != EcoPosSriBridge.VERSION_CONTRATO) {
            problema = "El m\u00F3dulo de facturaci\u00F3n (sri-conector) es de otra versi\u00F3n (" + versionModulo
                    + ") que Quinde POS (" + EcoPosSriBridge.VERSION_CONTRATO + "). Actual\u00EDzalo: "
                    + "ant -f build_working.xml sri, o copia el ecopos-sri-connector.jar de la misma versi\u00F3n.";
            LOG.warning(problema);
            try {
                puente.cerrar();
            } catch (Throwable e) {
                // Se descarta igual.
            }
            executor.shutdownNow();
            return null;
        }
        return puente;
    }

    /** Si el modulo esta instalado pero no se pudo usar (por ejemplo, otra version), el motivo; si no, null. */
    public static synchronized String getProblema() {
        return problema;
    }

    /** Libera los recursos del puente y del classloader dedicado. Registrado como shutdown hook de la JVM. */
    public static synchronized void shutdown() {
        if (instancia != null) {
            try {
                instancia.cerrar();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error cerrando ecopos-sri-connector", e);
            }
        }
        if (classLoaderConector != null) {
            try {
                classLoaderConector.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error cerrando el classloader de ecopos-sri-connector", e);
            }
        }
    }
}
