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
        Session sesion = crearSesion();

        MimeMessage mensaje = new MimeMessage(sesion);
        mensaje.setFrom(new InternetAddress(configuracion.getRemitente()));
        mensaje.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        mensaje.setSubject(asunto, "UTF-8");

        MimeMultipart multiparte = new MimeMultipart();

        MimeBodyPart parteTexto = new MimeBodyPart();
        parteTexto.setText(cuerpo, "UTF-8");
        multiparte.addBodyPart(parteTexto);

        if (xml != null) {
            multiparte.addBodyPart(adjunto(nombreArchivoXml, "application/xml", xml));
        }
        if (pdf != null) {
            multiparte.addBodyPart(adjunto(nombreArchivoPdf, "application/pdf", pdf));
        }

        mensaje.setContent(multiparte);
        Transport.send(mensaje);
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
