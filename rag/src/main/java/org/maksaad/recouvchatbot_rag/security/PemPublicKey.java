package org.maksaad.recouvchatbot_rag.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Lecture de la cle PUBLIQUE de verification des jetons (RSA, format X.509 / SubjectPublicKeyInfo).
 * Fournie en PEM complet (retours a la ligne ou "\n" litteraux) ou en base64 brut d'une ligne.
 */
final class PemPublicKey {

    private PemPublicKey() {
    }

    static PublicKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(
                    "JWT_PUBLIC_KEY (app.jwt.public-key) est obligatoire : la cle PUBLIQUE fournie par scripts/generate-jwt-keys.sh");
        }
        try {
            String body = pem.replace("\\n", "\n")
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s+", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("JWT_PUBLIC_KEY illisible : attendu une cle publique RSA (X.509)", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
