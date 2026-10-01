package com.openbravo.pos.sri.ui;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Enumeration;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * Lee la firma electronica (.p12) para mostrar a quien pertenece y hasta
 * cuando es valida - lo que mas falla en la practica es una firma vencida o
 * una clave mal escrita, y es mejor saberlo al configurarla que cuando el
 * SRI rechaza la primera factura.
 */
public final class InfoCertificado {

    public final String titular;
    public final LocalDate validoHasta;

    private InfoCertificado(String titular, LocalDate validoHasta) {
        this.titular = titular;
        this.validoHasta = validoHasta;
    }

    public long diasRestantes() {
        return ChronoUnit.DAYS.between(LocalDate.now(), validoHasta);
    }

    /** Lanza una excepcion con un mensaje claro si el archivo no existe o la clave no abre el certificado. */
    public static InfoCertificado leer(Path archivo, char[] clave) throws Exception {
        if (archivo == null || !Files.isRegularFile(archivo)) {
            throw new IllegalArgumentException("No se encontró el archivo de la firma");
        }
        KeyStore almacen = KeyStore.getInstance("PKCS12");
        try (InputStream entrada = Files.newInputStream(archivo)) {
            almacen.load(entrada, clave);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("La clave de la firma no es correcta", e);
        }
        X509Certificate elegido = null;
        for (Enumeration<String> alias = almacen.aliases(); alias.hasMoreElements();) {
            String a = alias.nextElement();
            if (almacen.isKeyEntry(a) && almacen.getCertificate(a) instanceof X509Certificate) {
                X509Certificate c = (X509Certificate) almacen.getCertificate(a);
                // Varias entidades (BCE, Security Data) incluyen tambien certificados de la CA:
                // se usa el que tiene clave privada y uso de firma digital.
                boolean[] usos = c.getKeyUsage();
                if (elegido == null || (usos != null && usos.length > 0 && usos[0])) {
                    elegido = c;
                }
            }
        }
        if (elegido == null) {
            throw new IllegalArgumentException("El archivo no contiene una firma con clave privada");
        }
        return new InfoCertificado(nombreComun(elegido.getSubjectX500Principal().getName()),
                elegido.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private static String nombreComun(String dn) {
        try {
            for (Rdn rdn : new LdapName(dn).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (Exception ignorado) {
            // se devuelve el DN completo
        }
        return dn;
    }
}
