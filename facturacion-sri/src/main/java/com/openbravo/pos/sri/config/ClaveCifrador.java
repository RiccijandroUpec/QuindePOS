package com.openbravo.pos.sri.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra/descifra la clave del certificado .p12 antes de guardarla en
 * {@code datos-emisor.properties}, para que no quede en texto plano.
 *
 * Es ofuscacion, no secreto criptografico real: la frase de paso vive en
 * este mismo codigo fuente, asi que cualquiera con acceso al jar puede
 * revertirla - el objetivo es que la clave no sea legible a simple vista
 * si alguien abre el archivo de configuracion, no resistir a un atacante
 * decidido que ya tiene el jar en la mano (mismo nivel de proteccion que
 * {@code AltEncrypter} en ECOPos para la contrasena de base de datos, pero
 * con AES/GCM en vez de DESEDE, que ya esta obsoleto).
 */
final class ClaveCifrador {

    private static final String FRASE_DE_PASO = "ecopos-sri-connector-v1";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private ClaveCifrador() {
    }

    static String cifrar(char[] claveEnClaro) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claveDerivada(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cifrado = cipher.doFinal(new String(claveEnClaro).getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la clave del certificado", e);
        }
    }

    static char[] descifrar(String claveCifrada) {
        try {
            byte[] datos = Base64.getDecoder().decode(claveCifrada);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cifrado = new byte[datos.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(datos, 0, iv, 0, iv.length);
            System.arraycopy(datos, iv.length, cifrado, 0, cifrado.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claveDerivada(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] claro = cipher.doFinal(cifrado);
            return new String(claro, StandardCharsets.UTF_8).toCharArray();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo descifrar la clave del certificado (archivo de configuracion corrupto o editado a mano)", e);
        }
    }

    private static SecretKeySpec claveDerivada() throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] clave = sha256.digest(FRASE_DE_PASO.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(clave, "AES");
    }
}
