package com.openbravo.pos.instalacion;

import com.openbravo.pos.forms.AppConfig;
import com.openbravo.pos.forms.EcoPosTema;
import com.openbravo.pos.util.AltEncrypter;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Lo usa el instalador de Windows al final de la instalacion: se conecta al servidor de base de
 * datos (el MariaDB que trae el instalador o uno que ya exista), crea la base si no existe y
 * escribe la configuracion de Quinde POS con la clave cifrada.
 *
 * <pre>
 * java -cp ecopos.jar com.openbravo.pos.instalacion.ConfigurarBase
 *      --config C:\ProgramData\QuindePOS\config\quinde.properties --dir "C:\Program Files\Quinde POS"
 *      --host 127.0.0.1 --puerto 3310 --usuario root --clave-archivo clave.txt --base quindepos
 *      [--esperar 60] [--generar-clave] [--mysqldump ruta] [--resultado archivo] [--guardar-aunque-falle] [--forzar]
 * </pre>
 * Si la configuracion ya existe (una actualizacion) no la toca, salvo con --forzar. La clave puede
 * ir en --clave o, mejor, en un archivo (--clave-archivo) para que no quede en la linea de comandos.
 * --esperar reintenta la conexion esos segundos (el servicio de MariaDB recien arranca).
 * --generar-clave (MariaDB incluido, recien creado con root sin clave): le pone a root una clave
 * aleatoria que solo queda, cifrada, en la configuracion.
 * Con --guardar-aunque-falle se escribe la configuracion aunque no conecte: Quinde POS abre su
 * ventana de Configuracion para corregir los datos. Codigo de salida 0 = listo, 1 = no se pudo
 * conectar o crear la base (el motivo queda en --resultado).
 */
public final class ConfigurarBase {

    private ConfigurarBase() {
    }

    public static void main(String[] args) {
        Map<String, String> a = argumentos(args);
        String resultado = a.get("resultado");
        try {
            String mensaje = configurar(a);
            escribir(resultado, "OK\n" + mensaje);
            System.out.println(mensaje);
            System.exit(0);
        } catch (Exception e) {
            String motivo = e.getMessage() == null ? e.toString() : e.getMessage();
            escribir(resultado, "ERROR\n" + motivo);
            System.out.println("ERROR: " + motivo);
            System.exit(1);
        }
    }

    static String configurar(Map<String, String> a) throws Exception {
        File archivo = new File(requerido(a, "config"));
        if (archivo.isFile() && !a.containsKey("forzar")) {
            return "Se conserva la configuraci\u00F3n existente: " + archivo;
        }
        String dir = requerido(a, "dir");
        String host = a.containsKey("host") ? a.get("host") : "localhost";
        String puerto = a.containsKey("puerto") ? a.get("puerto") : "3306";
        String usuario = requerido(a, "usuario");
        String clave = clave(a);
        String base = a.containsKey("base") ? a.get("base") : "quindepos";
        if (!base.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("El nombre de la base solo puede tener letras, n\u00FAmeros y _.");
        }
        if (!puerto.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("El puerto debe ser un n\u00FAmero (por ejemplo 3306).");
        }
        int esperar = a.containsKey("esperar") ? Integer.parseInt(a.get("esperar")) : 0;

        // El driver se carga del jar en lib/, igual que lo hace Quinde POS al arrancar.
        File driverlib = new File(new File(dir, "lib"), "mysql-connector-java-5.1.49.jar");
        String servidor = "jdbc:mysql://" + host + ":" + puerto + "/";
        Exception fallo = null;
        try {
            String nueva = a.containsKey("generar-clave") ? claveAleatoria() : null;
            crearBase(driverlib, servidor, usuario, clave, base, esperar, nueva);
            if (nueva != null) {
                clave = nueva;
            }
        } catch (Exception e) {
            if (!a.containsKey("guardar-aunque-falle")) {
                throw e;
            }
            fallo = e;
        }

        archivo.getAbsoluteFile().getParentFile().mkdirs();
        System.setProperty("dirname.path", dir.endsWith(File.separator) ? dir : dir + File.separator);
        AppConfig config = new AppConfig(archivo);
        config.load(); // valores por defecto (no existe el archivo)
        config.setProperty("db.driverlib", driverlib.getAbsolutePath());
        config.setProperty("db.driver", "com.mysql.jdbc.Driver");
        config.setProperty("db.URL", servidor + base);
        config.setProperty("db.user", usuario);
        config.setProperty("db.password", clave.isEmpty() ? "" : "crypt:" + new AltEncrypter("cypherkey" + usuario).encrypt(clave));
        // La base esta vacia: Quinde POS crea sus tablas al abrir, sin preguntar.
        config.setProperty("db.crear.sinpreguntar", "si");
        config.setProperty("swing.defaultlaf", EcoPosTema.CLARO);
        config.setProperty("user.language", "es");
        config.setProperty("user.country", "EC");
        config.setProperty("user.variant", "");
        if (a.containsKey("mysqldump")) {
            config.setProperty("backup.mysqldump", a.get("mysqldump"));
        }
        config.save();
        if (fallo != null) {
            throw new IllegalStateException(fallo.getMessage()
                    + " Se guard\u00F3 la configuraci\u00F3n igual: al abrir Quinde POS podr\u00E1s corregir los datos.", fallo);
        }
        return "Base \u201C" + base + "\u201D lista en " + host + ":" + puerto + ". Configuraci\u00F3n: " + archivo;
    }

    private static void crearBase(File driverlib, String servidor, String usuario, String clave, String base, int esperar,
            String nuevaClave) throws Exception {
        ClassLoader cargador = new URLClassLoader(new URL[]{driverlib.toURI().toURL()}, ConfigurarBase.class.getClassLoader());
        Driver driver = (Driver) Class.forName("com.mysql.jdbc.Driver", true, cargador).newInstance();
        Properties datos = new Properties();
        datos.setProperty("user", usuario);
        datos.setProperty("password", clave);
        long limite = System.currentTimeMillis() + esperar * 1000L;
        while (true) {
            try (Connection con = driver.connect(servidor + "?useSSL=false&connectTimeout=10000", datos);
                 Statement st = con.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + base + "` CHARACTER SET utf8 COLLATE utf8_general_ci");
                if (nuevaClave != null) {
                    // root@localhost, root@127.0.0.1, root@::1... (la clave es solo letras y numeros).
                    java.util.List<String> hosts = new java.util.ArrayList<>();
                    try (java.sql.ResultSet rs = st.executeQuery("SELECT Host FROM mysql.user WHERE User = '" + usuario.replace("'", "''") + "'")) {
                        while (rs.next()) {
                            hosts.add(rs.getString(1));
                        }
                    }
                    for (String h : hosts) {
                        st.executeUpdate("ALTER USER '" + usuario.replace("'", "''") + "'@'" + h.replace("'", "''")
                                + "' IDENTIFIED BY '" + nuevaClave + "'");
                    }
                    st.executeUpdate("FLUSH PRIVILEGES");
                }
                return;
            } catch (SQLException e) {
                if (System.currentTimeMillis() >= limite) {
                    throw new IllegalStateException("No se pudo conectar a la base de datos en "
                            + servidor.substring("jdbc:mysql://".length(), servidor.length() - 1)
                            + " con el usuario " + usuario + ": " + e.getMessage(), e);
                }
                Thread.sleep(2000);
            }
        }
    }

    static String claveAleatoria() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        java.security.SecureRandom azar = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            sb.append(letras.charAt(azar.nextInt(letras.length())));
        }
        return sb.toString();
    }

    private static String clave(Map<String, String> a) throws Exception {
        if (a.containsKey("clave-archivo")) {
            byte[] b = Files.readAllBytes(new File(a.get("clave-archivo")).toPath());
            return new String(b, StandardCharsets.UTF_8).trim();
        }
        return a.containsKey("clave") ? a.get("clave") : "";
    }

    private static Map<String, String> argumentos(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String clave = args[i].substring(2);
                String valor = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "si";
                m.put(clave, valor);
            }
        }
        return m;
    }

    private static String requerido(Map<String, String> a, String clave) {
        String v = a.get(clave);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("Falta --" + clave);
        }
        return v.trim();
    }

    private static void escribir(String ruta, String texto) {
        if (ruta == null) {
            return;
        }
        try (PrintWriter w = new PrintWriter(ruta, StandardCharsets.UTF_8.name())) {
            w.print(texto);
        } catch (Exception e) {
            // Sin archivo de resultado el instalador solo ve el codigo de salida.
        }
    }
}
