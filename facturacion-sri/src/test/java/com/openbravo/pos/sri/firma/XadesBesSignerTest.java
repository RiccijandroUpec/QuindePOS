package com.openbravo.pos.sri.firma;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Usa un certificado .p12 autofirmado, generado con el {@code keytool} del
 * propio JDK en un directorio temporal, para probar la firma real sin
 * depender de un certificado de un usuario. No valida que el certificado
 * sea de una entidad certificadora acreditada por el SRI (eso lo rechazaria
 * el SRI en un envio real) - solo que {@link XadesBesSigner} produce una
 * firma XAdES-BES bien formada sobre el XML del comprobante.
 */
class XadesBesSignerTest {

    private static Path certificadoP12;
    private static final char[] CLAVE = "clave-de-prueba".toCharArray();

    @BeforeAll
    static void generarCertificadoDePrueba(@TempDir Path tempDir) throws IOException, InterruptedException {
        certificadoP12 = tempDir.resolve("certificado-prueba.p12");
        String keytool = System.getProperty("java.home") + "/bin/keytool";

        Process proceso = new ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", "prueba-sri",
                "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=ALMACENES DE PRUEBA S.A., O=Prueba, C=EC",
                "-validity", "365",
                "-storetype", "PKCS12",
                "-keystore", certificadoP12.toString(),
                "-storepass", new String(CLAVE),
                "-keypass", new String(CLAVE))
                .redirectErrorStream(true)
                .start();
        String salida = new String(proceso.getInputStream().readAllBytes());
        int codigo = proceso.waitFor();
        assertEquals(0, codigo, "keytool debe generar el certificado de prueba sin error:\n" + salida);
    }

    private static final String XML_DE_PRUEBA =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<factura id=\"comprobante\" version=\"2.1.0\">" +
            "<infoTributaria><ambiente>1</ambiente><ruc>1790012345001</ruc></infoTributaria>" +
            "</factura>";

    @Test
    void firmaElXmlYProduceUnNodoSignature() {
        XadesBesSigner firmador = new XadesBesSigner(certificadoP12.toString(), CLAVE);

        String xmlFirmado = firmador.firmar(XML_DE_PRUEBA);

        assertTrue(xmlFirmado.contains("<ds:Signature") || xmlFirmado.contains("Signature xmlns"),
                "el XML firmado debe contener el nodo de firma XML-DSig:\n" + xmlFirmado);
        assertTrue(xmlFirmado.contains("SignedInfo"), "debe incluir SignedInfo");
        assertTrue(xmlFirmado.contains("SignatureValue"), "debe incluir el valor de la firma");
        assertTrue(xmlFirmado.contains("X509Certificate"), "debe incluir el certificado usado para firmar");
        assertTrue(xmlFirmado.contains("QualifyingProperties"), "debe incluir las propiedades XAdES (SigningTime, etc.)");
        assertTrue(xmlFirmado.contains("<ruc>1790012345001</ruc>"), "el contenido original del comprobante debe seguir presente");
    }

    @Test
    void usaRsaSha1ComoExigeLaFichaTecnica() {
        // Seccion 6.8 y Anexo 14 de la ficha tecnica del SRI exigen RSA-SHA1,
        // no el SHA-256 que xades4j usa por defecto.
        XadesBesSigner firmador = new XadesBesSigner(certificadoP12.toString(), CLAVE);

        String xmlFirmado = firmador.firmar(XML_DE_PRUEBA);

        assertTrue(xmlFirmado.contains("Algorithm=\"http://www.w3.org/2000/09/xmldsig#rsa-sha1\""),
                "debe firmar con RSA-SHA1:\n" + xmlFirmado);
        assertTrue(xmlFirmado.contains("Algorithm=\"http://www.w3.org/2000/09/xmldsig#sha1\""),
                "los digest deben usar SHA1:\n" + xmlFirmado);
    }

    @Test
    void laReferenciaApuntaAlElementoComprobante() {
        XadesBesSigner firmador = new XadesBesSigner(certificadoP12.toString(), CLAVE);

        String xmlFirmado = firmador.firmar(XML_DE_PRUEBA);

        assertTrue(xmlFirmado.contains("URI=\"#comprobante\""),
                "la referencia de la firma debe apuntar al elemento raiz por su id, como exige el Anexo 14 de la ficha tecnica");
    }
}
