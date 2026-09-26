package com.recouvtech.recouvback.security;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Lecture des cles RSA de signature des jetons.
 *
 * Une cle peut etre fournie en PEM complet (avec ses lignes BEGIN/END, retours a la ligne ou
 * sequences "\n" litterales) ou en base64 brut d'une seule ligne : c'est plus simple a placer dans
 * une variable d'environnement.
 */
public final class PemKeys {

    /** En dessous, une cle RSA se factorise : refuser plutot qu'accepter une cle faible. */
    private static final int MIN_RSA_BITS = 2048;

    private PemKeys() {
    }

    public static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = Base64.getMimeDecoder().decode(stripPem(pem));
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey rsa) || rsa.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalArgumentException("La cle privee JWT doit etre une cle RSA d'au moins " + MIN_RSA_BITS + " bits");
            }
            return key;
        } catch (InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "JWT_PRIVATE_KEY illisible : attendu une cle RSA PKCS#8 (voir scripts/generate-jwt-keys.sh)", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** La cle publique se deduit de la cle privee RSA : une seule variable a fournir au backend. */
    public static PublicKey derivePublicKey(PrivateKey privateKey) {
        try {
            RSAPrivateCrtKey rsa = (RSAPrivateCrtKey) privateKey;
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Paire ephemere : reservee aux tests et au developpement, jamais a la production (voir JwtUtils). */
    public static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Corps base64 d'une cle : retire les lignes BEGIN/END, les espaces et les "\n" litteraux. */
    static String stripPem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("cle vide");
        }
        return pem.replace("\\n", "\n")
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
    }

    /** Pour les tests : expose l'exposant public d'une cle deja parsee. */
    static BigInteger publicExponent(PrivateKey key) {
        return ((RSAPrivateCrtKey) key).getPublicExponent();
    }
}
