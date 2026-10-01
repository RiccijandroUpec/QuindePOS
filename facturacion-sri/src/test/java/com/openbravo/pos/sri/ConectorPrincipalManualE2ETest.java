package com.openbravo.pos.sri;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.openbravo.pos.sri.dominio.Ambiente;
import com.openbravo.pos.sri.dominio.DatosEmisor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Prueba manual, deliberadamente deshabilitada por defecto (@Disabled):
 * ejercita ConectorPrincipal.procesarTicket() de punta a punta contra la
 * base MySQL real de ECOPos y el servidor de pruebas real del SRI
 * (celcer.sri.gob.ec). Se deja en el repo como documentacion de como
 * correr esta verificacion manualmente, pero NO debe correr en CI (toca
 * un servicio externo real y usa datos de prueba insertados a mano).
 *
 * Para correrla: quitar temporalmente @Disabled, insertar en la base
 * "ecopos" un ticket cerrado real (TICKETS/RECEIPTS/TICKETLINES/PAYMENTS,
 * mas una CLOSEDCASH aislada de prueba - nunca la caja real abierta) con el
 * id que apunte TICKET_ID_DE_PRUEBA, y ejecutar
 * `mvn -o test -Dtest=ConectorPrincipalManualE2ETest`. El test NO borra los
 * datos que consulta al terminar - hazlo tu mismo despues, igual que se hizo
 * la ultima vez que se corrio esta prueba (ver project_ecopos_sri_connector.md).
 */
class ConectorPrincipalManualE2ETest {

    private static final String TICKET_ID_DE_PRUEBA = "56ccbdf3-7469-4230-9532-fe6f507ebb2c";

    @Test
    @Disabled("prueba manual contra servicios reales, no para CI")
    void procesaElTicketDePruebaDePuntaAPunta(@TempDir Path tempDir) throws Exception {
        Path certificadoP12 = tempDir.resolve("certificado-prueba.p12");
        char[] clave = "clave-de-prueba".toCharArray();
        String keytool = System.getProperty("java.home") + "/bin/keytool";
        Process proceso = new ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", "prueba-sri",
                "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=ALMACENES DE PRUEBA S.A., O=Prueba, C=EC",
                "-validity", "365",
                "-storetype", "PKCS12",
                "-keystore", certificadoP12.toString(),
                "-storepass", new String(clave),
                "-keypass", new String(clave))
                .redirectErrorStream(true)
                .start();
        proceso.getInputStream().readAllBytes();
        proceso.waitFor();

        DatosEmisor emisor = new DatosEmisor(
                "1790012345001", "ALMACENES DE PRUEBA S.A.", "PRUEBA SRI",
                "AV. PRUEBA 123, QUITO", "AV. PRUEBA 123, QUITO",
                null, false, "001", "001", Ambiente.PRUEBAS,
                certificadoP12.toString(), clave);

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setDatabaseName("ecopos");
        dataSource.setUser("root");
        dataSource.setPassword("");

        ConectorPrincipal conector = new ConectorPrincipal(emisor, dataSource);
        conector.procesarTicket(TICKET_ID_DE_PRUEBA);

        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/ecopos", "root", "");
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT estado, clave_acceso, secuencial, mensaje_error FROM ecopos_sri_comprobantes " +
                     "WHERE ticket_id = '" + TICKET_ID_DE_PRUEBA + "'")) {
            if (rs.next()) {
                System.out.println("estado=" + rs.getString("estado")
                        + " claveAcceso=" + rs.getString("clave_acceso")
                        + " secuencial=" + rs.getString("secuencial")
                        + " mensajeError=" + rs.getString("mensaje_error"));
            }
        }
    }
}
