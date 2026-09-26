package org.maksaad.recouvchatbot_rag.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Empreinte courte d'une valeur pour les journaux d'audit. Permet de retrouver "la meme
 * question" ou "le meme client" d'un appel a l'autre sans ecrire de donnees de recouvrement
 * dans les journaux.
 */
public final class AuditHash {

    private AuditHash() {
    }

    public static String of(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
