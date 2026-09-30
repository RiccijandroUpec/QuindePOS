package com.openbravo.pos.forms;

import com.openbravo.pos.util.AltEncrypter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Copia de seguridad automatica de la base MySQL/MariaDB: una vez al dia, al
 * arrancar EcoPos y en segundo plano (nunca demora el arranque), guarda un
 * volcado comprimido con mysqldump en ~/EcoPos-respaldos (o en la carpeta de la
 * propiedad "backup.dir") y conserva los 14 mas recientes.
 *
 * Propiedades opcionales en ecopos.properties: backup.enabled=false para
 * apagarlo, backup.dir, backup.mysqldump (ruta de mysqldump si no esta en
 * C:\xampp\mysql\bin ni en el PATH) y backup.keep (cuantos conservar).
 */
public final class RespaldoAutomatico {

    private static final Logger LOG = Logger.getLogger(RespaldoAutomatico.class.getName());
    private static final Pattern URL_MYSQL = Pattern.compile("jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]+).*");
    private static final long HORAS_ENTRE_RESPALDOS = 20;
    private static final String PREFIJO = "ecopos-";

    private RespaldoAutomatico() {
    }

    /** Carpeta donde se guardan los respaldos. */
    public static File carpeta(AppProperties props) {
        String dir = props.getProperty("backup.dir");
        return dir == null || dir.trim().isEmpty()
                ? new File(System.getProperty("user.home"), "EcoPos-respaldos")
                : new File(dir.trim());
    }

    /** El respaldo mas reciente, o null si nunca se hizo uno. */
    public static File ultimo(AppProperties props) {
        File[] archivos = carpeta(props).listFiles();
        if (archivos == null) {
            return null;
        }
        File ultimo = null;
        for (File f : archivos) {
            if (f.getName().startsWith(PREFIJO) && f.getName().endsWith(".sql.gz")
                    && (ultimo == null || f.lastModified() > ultimo.lastModified())) {
                ultimo = f;
            }
        }
        return ultimo;
    }

    /** Lanza el respaldo en segundo plano si toca (hace mas de 20 h del ultimo). */
    public static void programar(final AppProperties props) {
        if ("false".equalsIgnoreCase(props.getProperty("backup.enabled"))) {
            return;
        }
        Thread hilo = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File ultimo = ultimo(props);
                    long horas = ultimo == null ? Long.MAX_VALUE
                            : TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - ultimo.lastModified());
                    if (horas >= HORAS_ENTRE_RESPALDOS) {
                        respaldarAhora(props);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "No se pudo hacer la copia de seguridad automatica", e);
                }
            }
        }, "ecopos-respaldo");
        hilo.setDaemon(true);
        hilo.setPriority(Thread.MIN_PRIORITY);
        hilo.start();
    }

    /** Hace el respaldo ya. Devuelve el archivo creado. */
    public static File respaldarAhora(AppProperties props) throws Exception {
        String url = props.getProperty("db.URL");
        Matcher m = url == null ? null : URL_MYSQL.matcher(url);
        if (m == null || !m.matches()) {
            throw new IllegalStateException("La copia autom\u00E1tica solo est\u00E1 disponible para MySQL/MariaDB");
        }
        String host = m.group(1);
        String puerto = m.group(2) == null ? "3306" : m.group(2);
        String base = m.group(3);
        String usuario = props.getProperty("db.user");
        String clave = props.getProperty("db.password");
        if (usuario != null && clave != null && clave.startsWith("crypt:")) {
            clave = new AltEncrypter("cypherkey" + usuario).decrypt(clave.substring(6));
        }

        File carpeta = carpeta(props);
        if (!carpeta.isDirectory() && !carpeta.mkdirs()) {
            throw new IllegalStateException("No se pudo crear la carpeta " + carpeta);
        }
        String marca = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
        File destino = new File(carpeta, PREFIJO + base + "-" + marca + ".sql.gz");
        File temporal = new File(carpeta, destino.getName() + ".parcial");

        List<String> comando = new ArrayList<String>(Arrays.asList(buscarMysqldump(props),
                "--host=" + host, "--port=" + puerto, "--user=" + usuario,
                "--single-transaction", "--routines", "--default-character-set=utf8", base));
        ProcessBuilder pb = new ProcessBuilder(comando);
        if (clave != null && !clave.isEmpty()) {
            pb.environment().put("MYSQL_PWD", clave); // no queda visible en la lista de procesos
        }
        pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(carpeta, "respaldos.log")));
        Process proceso = pb.start();
        try (InputStream entrada = proceso.getInputStream();
             OutputStream salida = new GZIPOutputStream(new FileOutputStream(temporal))) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = entrada.read(buffer)) > 0) {
                salida.write(buffer, 0, n);
            }
        }
        if (!proceso.waitFor(30, TimeUnit.MINUTES) || proceso.exitValue() != 0) {
            temporal.delete();
            throw new IllegalStateException("mysqldump termin\u00F3 con error (ver " + new File(carpeta, "respaldos.log") + ")");
        }
        if (!temporal.renameTo(destino)) {
            throw new IllegalStateException("No se pudo renombrar " + temporal);
        }
        limpiarViejos(carpeta, conservar(props));
        LOG.info("Copia de seguridad creada: " + destino);
        return destino;
    }

    private static int conservar(AppProperties props) {
        try {
            return Math.max(1, Integer.parseInt(props.getProperty("backup.keep")));
        } catch (Exception e) {
            return 14;
        }
    }

    private static void limpiarViejos(File carpeta, int conservar) {
        File[] archivos = carpeta.listFiles();
        if (archivos == null) {
            return;
        }
        List<File> respaldos = new ArrayList<File>();
        for (File f : archivos) {
            if (f.getName().startsWith(PREFIJO) && f.getName().endsWith(".sql.gz")) {
                respaldos.add(f);
            }
        }
        java.util.Collections.sort(respaldos, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = conservar; i < respaldos.size(); i++) {
            respaldos.get(i).delete();
        }
    }

    private static String buscarMysqldump(AppProperties props) {
        String configurado = props.getProperty("backup.mysqldump");
        if (configurado != null && !configurado.trim().isEmpty()) {
            return configurado.trim();
        }
        for (String candidato : new String[]{"C:\\xampp\\mysql\\bin\\mysqldump.exe", "/opt/lampp/bin/mysqldump",
                "/usr/bin/mysqldump", "/usr/local/bin/mysqldump"}) {
            if (new File(candidato).isFile()) {
                return candidato;
            }
        }
        return "mysqldump"; // del PATH
    }
}
