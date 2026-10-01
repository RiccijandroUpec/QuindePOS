package com.openbravo.pos.sri.correo;

import com.openbravo.pos.sri.dominio.ConfiguracionCorreo;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.util.Properties;

/**
 * Envia el XML y/o el RIDE (PDF) de un comprobante al correo del comprador -
 * la normativa (Resolucion NAC-DGERCGC12-00105) obliga al emisor a
 * entregarselos si el comprador lo pide. Un adjunto puede omitirse pasando
 * {@code null} (por ejemplo, si el usuario solo quiere reenviar el XML).
 */
public final class NotificadorCorreo {

    private final ConfiguracionCorreo configuracion;

    public NotificadorCorreo(ConfiguracionCorreo configuracion) {
        this.configuracion = configuracion;
    }

    public void enviarComprobante(String destinatario, String asunto, String cuerpo,
                                   String nombreArchivoXml, byte[] xml,
                                   String nombreArchivoPdf, byte[] pdf) throws MessagingException {
        Transport.send(armar(crearSesion(), configuracion.getRemitente(), destinatario, asunto, cuerpo, null, null,
                nombreArchivoXml, xml, nombreArchivoPdf, pdf));
    }

    /**
     * Envia el comprobante con el mensaje con diseno (HTML) y su version en texto plano.
     *
     * @param logoPng logo del negocio para el encabezado del correo, o null
     */
    public void enviarComprobante(String destinatario, MensajeComprobante mensaje, byte[] xml, byte[] pdf,
                                  byte[] logoPng) throws MessagingException {
        Transport.send(armar(crearSesion(), configuracion.getRemitente(), destinatario, mensaje.asunto, mensaje.cuerpo,
                mensaje.html, logoPng, mensaje.archivoXml, xml, mensaje.archivoPdf, pdf));
    }

    /**
     * Arma el correo (sin enviarlo). Estructura:
     * multipart/mixed = [cuerpo, adjunto XML, adjunto PDF], donde el cuerpo es el texto
     * plano o, si hay HTML, un multipart/alternative [texto, HTML]; con logo, el HTML va
     * dentro de un multipart/related junto a la imagen (cid:logo-negocio).
     */
    static MimeMessage armar(Session sesion, String remitente, String destinatario, String asunto, String texto,
                             String html, byte[] logoPng, String nombreArchivoXml, byte[] xml,
                             String nombreArchivoPdf, byte[] pdf) throws MessagingException {
        MimeMessage mensaje = new MimeMessage(sesion);
        mensaje.setFrom(new InternetAddress(remitente));
        mensaje.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        mensaje.setSubject(asunto, "UTF-8");

        MimeMultipart mixto = new MimeMultipart("mixed");
        MimeBodyPart cuerpo = new MimeBodyPart();
        if (html == null) {
            cuerpo.setText(texto, "UTF-8");
        } else {
            MimeMultipart alternativas = new MimeMultipart("alternative");
            MimeBodyPart parteTexto = new MimeBodyPart();
            parteTexto.setText(texto, "UTF-8");
            alternativas.addBodyPart(parteTexto);

            MimeBodyPart parteHtml = new MimeBodyPart();
            parteHtml.setContent(html, "text/html; charset=UTF-8");
            if (logoPng != null) {
                MimeMultipart relacionado = new MimeMultipart("related");
                relacionado.addBodyPart(parteHtml);
                MimeBodyPart logo = new MimeBodyPart();
                logo.setDataHandler(new DataHandler(new ByteArrayDataSource(logoPng, "image/png")));
                logo.setContentID("<" + MensajeComprobante.LOGO_CID + ">");
                logo.setDisposition(MimeBodyPart.INLINE);
                logo.setFileName("logo.png");
                relacionado.addBodyPart(logo);
                MimeBodyPart contenedor = new MimeBodyPart();
                contenedor.setContent(relacionado);
                alternativas.addBodyPart(contenedor);
            } else {
                alternativas.addBodyPart(parteHtml);
            }
            cuerpo.setContent(alternativas);
        }
        mixto.addBodyPart(cuerpo);

        if (xml != null) {
            mixto.addBodyPart(adjunto(nombreArchivoXml, "application/xml", xml));
        }
        if (pdf != null) {
            mixto.addBodyPart(adjunto(nombreArchivoPdf, "application/pdf", pdf));
        }
        mensaje.setContent(mixto);
        mensaje.saveChanges();
        return mensaje;
    }

    /** Logo del negocio (config/logo.png, elegido en Facturacion electronica) o null si no hay. */
    public static byte[] logoDelNegocio() {
        try {
            java.nio.file.Path ruta = com.openbravo.pos.sri.config.RutasConector.resolver("config/logo.png");
            return java.nio.file.Files.isRegularFile(ruta) ? java.nio.file.Files.readAllBytes(ruta) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static MimeBodyPart adjunto(String nombreArchivo, String tipoContenido, byte[] contenido) throws MessagingException {
        MimeBodyPart parte = new MimeBodyPart();
        DataSource fuente = new ByteArrayDataSource(contenido, tipoContenido);
        parte.setDataHandler(new DataHandler(fuente));
        parte.setFileName(nombreArchivo);
        return parte;
    }

    private Session crearSesion() {
        Properties propiedades = new Properties();
        propiedades.put("mail.smtp.host", configuracion.getHost());
        propiedades.put("mail.smtp.port", String.valueOf(configuracion.getPuerto()));
        propiedades.put("mail.smtp.auth", "true");
        if (configuracion.isUsarTls()) {
            propiedades.put("mail.smtp.starttls.enable", "true");
        }

        return Session.getInstance(propiedades, new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(configuracion.getUsuario(), new String(configuracion.getClave()));
            }
        });
    }
}
