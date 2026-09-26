package com.recouvtech.recouvback.configuration;

import com.recouvtech.recouvback.security.PemKeys;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static JwtUtils ephemeral(Duration ttl) {
        return new JwtUtils("", true, ttl, "smartcdc");
    }

    @Test
    void unJetonEmisEstVerifieEtPorteSesRevendications() {
        JwtUtils jwt = ephemeral(Duration.ofMinutes(15));

        var claims = jwt.parse(jwt.generateToken("agent@cabinet.test"));

        assertEquals("agent@cabinet.test", claims.getSubject());
        assertEquals("smartcdc", claims.getIssuer());
        assertEquals(Set.of(JwtUtils.AUDIENCE_API, JwtUtils.AUDIENCE_CHAT), claims.getAudience());
        assertTrue(claims.getId() != null && !claims.getId().isBlank(), "jti present");
        assertEquals(15 * 60, (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000);
    }

    @Test
    void chaqueJetonALeSonPropreIdentifiant() {
        JwtUtils jwt = ephemeral(Duration.ofMinutes(15));

        assertNotEquals(jwt.parse(jwt.generateToken("a@x")).getId(), jwt.parse(jwt.generateToken("a@x")).getId());
    }

    @Test
    void unJetonExpireEstRefuse() {
        JwtUtils jwt = ephemeral(Duration.ofSeconds(-5));

        String token = jwt.generateToken("a@x");

        assertThrows(ExpiredJwtException.class, () -> jwt.parse(token));
    }

    @Test
    void unJetonSigneParUneAutreCleEstRefuse() {
        JwtUtils emetteur = ephemeral(Duration.ofMinutes(15));
        JwtUtils autre = ephemeral(Duration.ofMinutes(15)); // autre paire de cles

        assertThrows(JwtException.class, () -> autre.parse(emetteur.generateToken("a@x")));
    }

    /**
     * Attaque de confusion d'algorithme : forger un jeton HS256 en utilisant la cle PUBLIQUE (connue
     * de tous) comme secret HMAC. verifyWith(PublicKey) doit le refuser.
     */
    @Test
    void unJetonHs256SigneAvecLaClePubliqueEstRefuse() {
        JwtUtils jwt = ephemeral(Duration.ofMinutes(15));
        SecretKey hmacAvecClePublique = Keys.hmacShaKeyFor(jwt.publicKey().getEncoded());
        String forge = Jwts.builder().subject("admin@x").issuer("smartcdc")
                .audience().add(JwtUtils.AUDIENCE_API).and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(hmacAvecClePublique, Jwts.SIG.HS256).compact();

        assertThrows(JwtException.class, () -> jwt.parse(forge));
    }

    @Test
    void unJetonSansSignatureEstRefuse() {
        JwtUtils jwt = ephemeral(Duration.ofMinutes(15));
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"admin@x\",\"iss\":\"smartcdc\",\"aud\":[\"smartcdc-api\"],\"exp\":" + (System.currentTimeMillis() / 1000 + 600) + "}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> jwt.parse(header + "." + payload + "."));
    }

    @Test
    void unJetonDUnAutreEmetteurEstRefuse() {
        KeyPair pair = PemKeys.generateEphemeral();
        String pem = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        JwtUtils api = new JwtUtils(pem, false, Duration.ofMinutes(15), "smartcdc");
        JwtUtils autreEmetteur = new JwtUtils(pem, false, Duration.ofMinutes(15), "un-autre-systeme");

        assertThrows(JwtException.class, () -> api.parse(autreEmetteur.generateToken("a@x")));
    }

    @Test
    void unJetonDestineAuSeulChatbotNeDonnePasAccesALApi() {
        JwtUtils jwt = ephemeral(Duration.ofMinutes(15));
        // Meme cle, meme emetteur, mais aud = chatbot uniquement.
        var pair = new Object() { KeyPair kp = PemKeys.generateEphemeral(); };
        JwtUtils api = new JwtUtils(Base64.getEncoder().encodeToString(pair.kp.getPrivate().getEncoded()),
                false, Duration.ofMinutes(15), "smartcdc");
        String chatOnly = Jwts.builder().subject("a@x").issuer("smartcdc")
                .audience().add(JwtUtils.AUDIENCE_CHAT).and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(pair.kp.getPrivate(), Jwts.SIG.RS256).compact();

        assertThrows(JwtException.class, () -> api.parse(chatOnly));
        assertTrue(jwt.accessTtlSeconds() > 0);
    }

    // ------------------------------------------------------------ configuration des cles

    @Test
    void sansCleEnProductionLeDemarrageEchoue() {
        assertThrows(IllegalStateException.class, () -> new JwtUtils("", false, Duration.ofMinutes(15), "smartcdc"));
    }

    @Test
    void uneCleIllisibleEstRefusee() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtils("ceci-nest-pas-une-cle", false, Duration.ofMinutes(15), "smartcdc"));
    }

    @Test
    void laClePeutEtreFournieEnPemCompletOuEnBase64BrutSurUneLigne() {
        KeyPair pair = PemKeys.generateEphemeral();
        String base64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pemLignes = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        // Variable d'environnement : les retours a la ligne arrivent souvent sous forme de "\n" litteraux.
        String pemEchappe = pemLignes.replace("\n", "\\n");

        String jeton = new JwtUtils(base64, false, Duration.ofMinutes(15), "smartcdc").generateToken("a@x");

        assertEquals("a@x", new JwtUtils(pemLignes, false, Duration.ofMinutes(15), "smartcdc").parse(jeton).getSubject());
        assertEquals("a@x", new JwtUtils(pemEchappe, false, Duration.ofMinutes(15), "smartcdc").parse(jeton).getSubject());
    }
}
