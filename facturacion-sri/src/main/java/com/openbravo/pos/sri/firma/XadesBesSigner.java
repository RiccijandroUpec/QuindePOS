package com.openbravo.pos.sri.firma;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.DataObjectReference;
import xades4j.production.SignatureAlgorithms;
import xades4j.production.SignedDataObjects;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.properties.DataObjectDesc;
import xades4j.providers.KeyingDataProvider;
import xades4j.providers.impl.FileSystemKeyStoreKeyingDataProvider;
import xades4j.providers.impl.KeyStoreKeyingDataProvider;
import xades4j.utils.XadesProfileResolutionException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Firma XAdES-BES sobre el XML del comprobante (el que produce
 * {@code FacturaXmlWriter}), usando un certificado .p12 del usuario. La
 * firma se inserta "enveloped" (como nodo hijo del elemento raiz
 * {@code <factura id="comprobante">}) referenciando ese mismo elemento por
 * su {@code id}, tal como muestra el Anexo 14 de la ficha tecnica.
 *
 * Cada instancia queda ligada a un certificado/clave especificos (los de
 * {@code DatosEmisor}) - crear una nueva instancia si esos datos cambian.
 */
public final class XadesBesSigner {

    private static final String URI_ELEMENTO_A_FIRMAR = "#comprobante";

    // La ficha tecnica del SRI (seccion 6.8 y Anexo 14) exige explicitamente
    // RSA-SHA1, no el SHA-256 que xades4j usa por defecto.
    private static final String ALGORITMO_FIRMA_RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";
    private static final String ALGORITMO_DIGEST_SHA1 = "http://www.w3.org/2000/09/xmldsig#sha1";

    private final XadesSigner signer;

    public XadesBesSigner(String rutaCertificadoP12, char[] claveCertificado) {
        KeyingDataProvider keyingDataProvider = FileSystemKeyStoreKeyingDataProvider
                .builder("PKCS12", rutaCertificadoP12, KeyStoreKeyingDataProvider.SigningCertificateSelector.single())
                .storePassword(() -> claveCertificado)
                .entryPassword((alias, certificado) -> claveCertificado)
                .build();

        SignatureAlgorithms algoritmosSri = new SignatureAlgorithms()
                .withSignatureAlgorithm("RSA", ALGORITMO_FIRMA_RSA_SHA1)
                .withDigestAlgorithmForDataObjectReferences(ALGORITMO_DIGEST_SHA1)
                .withDigestAlgorithmForReferenceProperties(ALGORITMO_DIGEST_SHA1);

        try {
            this.signer = new XadesBesSigningProfile(keyingDataProvider)
                    .withSignatureAlgorithms(algoritmosSri)
                    .newSigner();
        } catch (XadesProfileResolutionException e) {
            throw new IllegalStateException("No se pudo preparar el firmador XAdES-BES (revisa el certificado .p12)", e);
        }
    }

    /** Firma el XML (sin firmar) y devuelve el XML firmado como String. */
    public String firmar(String xmlSinFirmar) {
        try {
            Document documento = parsear(xmlSinFirmar);

            DataObjectDesc referenciaAlComprobante = new DataObjectReference(URI_ELEMENTO_A_FIRMAR)
                    .withTransform(new EnvelopedSignatureTransform());

            signer.sign(new SignedDataObjects(referenciaAlComprobante), documento.getDocumentElement());

            return serializar(documento);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el comprobante con XAdES-BES", e);
        }
    }

    private static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document documento = builder.parse(new InputSource(new StringReader(xml)));

        // Sin DTD/XSD durante el parseo, el DOM no sabe que "id" es un
        // atributo de tipo ID - sin esto, el resolver de XML-DSig no
        // encuentra el elemento al resolver la referencia "#comprobante".
        documento.getDocumentElement().setIdAttribute("id", true);

        return documento;
    }

    private static String serializar(Document documento) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(writer));
        return writer.toString();
    }
}
