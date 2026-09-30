package com.openbravo.pos.sri.config;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * Datos de conexion a la MISMA base de datos MySQL/MariaDB de ECOPos
 * (host/puerto/nombre/usuario/clave), leidos de un {@code .properties}
 * separado de {@code datos-emisor.properties} porque son datos de
 * infraestructura, no del emisor SRI. Si el archivo no existe, se usan los
 * valores por defecto de una instalacion XAMPP tipica (localhost/ecopos/root).
 *
 * Compartido por {@code ConectorPrincipal} y cualquier pantalla que necesite
 * leer la misma base (ej. {@code HistorialFrame}) - antes vivia como un
 * metodo privado solo en ConectorPrincipal.
 *
 * La clave se guarda cifrada (clave {@code claveCifrada}, mismo mecanismo
 * {@link ClaveCifrador} AES-GCM que usan {@code datos-emisor.properties} y
 * {@code correo.properties}) - nunca en texto plano. Si el archivo trae la
 * clave vieja en texto plano ({@code clave=...}, formato usado antes de que
 * esta clase soportara cifrado), se migra automaticamente a
 * {@code claveCifrada} y se reescribe el archivo sin la version en claro, la
 * primera vez que se carga.
 */
public final class ConexionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConexionLoader.class);

    /**
     * Modo fusionado (mismo proceso que ECOPos): el puente fija aqui un
     * DataSource construido con las MISMAS credenciales de ECOPos, y desde
     * entonces {@link #cargar(Path)} lo devuelve siempre, ignorando
     * {@code conexion.properties} - una sola fuente de verdad, sin un
     * segundo archivo de conexion que el usuario tenga que mantener. Null =
     * modo standalone, comportamiento de siempre.
     */
    private static volatile DataSource dataSourceFusionado;

    private ConexionLoader() {
    }

    public static void establecerDataSourceFusionado(DataSource dataSource) {
        dataSourceFusionado = dataSource;
    }

    /** Construye un DataSource MySQL (con el driver empaquetado en este jar) a partir de una URL JDBC ya armada. */
    public static DataSource desdeUrl(String urlJdbc, String usuario, String clave) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(urlJdbc);
        dataSource.setUser(usuario);
        dataSource.setPassword(clave == null ? "" : clave);
        return dataSource;
    }

    public static DataSource cargar(Path archivoConexion) throws IOException {
        DataSource fusionado = dataSourceFusionado;
        if (fusionado != null) {
            return fusionado;
        }

        Properties propiedades = new Properties();
        if (Files.exists(archivoConexion)) {
            try (InputStream entrada = Files.newInputStream(archivoConexion)) {
                propiedades.load(entrada);
            }
        } else {
            LOG.warn("No existe {}, se usan valores por defecto de conexion (localhost/ecopos/root)", archivoConexion);
        }

        String clave = resolverClave(propiedades, archivoConexion);

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setServerName(propiedades.getProperty("host", "localhost"));
        dataSource.setPort(Integer.parseInt(propiedades.getProperty("puerto", "3306")));
        dataSource.setDatabaseName(propiedades.getProperty("baseDatos", "ecopos"));
        dataSource.setUser(propiedades.getProperty("usuario", "root"));
        dataSource.setPassword(clave);
        return dataSource;
    }

    private static String resolverClave(Properties propiedades, Path archivoConexion) {
        String claveCifrada = propiedades.getProperty("claveCifrada");
        if (claveCifrada != null && !claveCifrada.isBlank()) {
            return new String(ClaveCifrador.descifrar(claveCifrada));
        }

        String claveEnClaro = propiedades.getProperty("clave", "");
        if (!claveEnClaro.isEmpty()) {
            migrarClaveATextoCifrado(archivoConexion, propiedades, claveEnClaro);
        }
        return claveEnClaro;
    }

    /**
     * Migra una clave en texto plano (formato usado antes de que esta clase
     * cifrara la clave) al formato cifrado, reescribiendo el archivo sin la
     * version en claro. Si la migracion falla por cualquier motivo (archivo
     * de solo lectura, etc.) no interrumpe la conexion - se sigue usando la
     * clave en claro que ya se leyo, solo no queda protegida en disco.
     */
    private static void migrarClaveATextoCifrado(Path archivoConexion, Properties propiedades, String claveEnClaro) {
        try {
            propiedades.setProperty("claveCifrada", ClaveCifrador.cifrar(claveEnClaro.toCharArray()));
            propiedades.remove("clave");
            try (OutputStream salida = Files.newOutputStream(archivoConexion)) {
                propiedades.store(salida, "Configuracion de conexion de ecopos-sri-connector - no editar claveCifrada a mano");
            }
            LOG.info("Clave de conexion en texto plano migrada a cifrada en {}", archivoConexion);
        } catch (Exception e) {
            LOG.warn("No se pudo migrar la clave de conexion a formato cifrado en {} (se sigue usando en texto plano)", archivoConexion, e);
        }
    }
}
