package com.recouvtech.recouvback.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    private static void echecs(LoginRateLimiter limiter, int n, String ip, String user) {
        for (int i = 0; i < n; i++) {
            limiter.recordFailure(ip, user);
        }
    }

    @Test
    void lIdentifiantEstBloqueApresCinqEchecsParMinuteMemeDepuisDAutresAdresses() {
        LoginRateLimiter limiter = new LoginRateLimiter(1000, 5);

        for (int i = 0; i < 5; i++) {
            assertFalse(limiter.isBlocked("10.0.0." + i, "victime@cabinet.test"), "avant le 5e echec : autorise");
            limiter.recordFailure("10.0.0." + i, "victime@cabinet.test");
        }
        // Attaque distribuee : une autre adresse IP ne contourne pas la limite de l'identifiant.
        assertTrue(limiter.isBlocked("10.9.9.9", "victime@cabinet.test"));
    }

    /** Le point qui change tout : seuls les echecs comptent. Un compte partage ou un test automatise se connecte librement. */
    @Test
    void lesConnexionsReussiesNeConsommentAucunQuota() {
        LoginRateLimiter limiter = new LoginRateLimiter(1000, 5);

        for (int i = 0; i < 50; i++) {
            assertFalse(limiter.isBlocked("1.1.1.1", "admin@cabinet.test"));
            limiter.recordSuccess("admin@cabinet.test"); // pas d'echec enregistre
        }
    }

    @Test
    void uneConnexionReussieRemetLesEchecsDeLIdentifiantAZero() {
        LoginRateLimiter limiter = new LoginRateLimiter(1000, 5);
        echecs(limiter, 4, "1.1.1.1", "agent@cabinet.test");

        limiter.recordSuccess("agent@cabinet.test");
        echecs(limiter, 4, "1.1.1.1", "agent@cabinet.test");

        assertFalse(limiter.isBlocked("1.1.1.1", "agent@cabinet.test"), "4 + 4 echecs separes par un succes : jamais 5 de suite");
    }

    @Test
    void lIdentifiantEstNormalise() {
        LoginRateLimiter limiter = new LoginRateLimiter(1000, 2);

        limiter.recordFailure("1.1.1.1", "Admin@Cabinet.test");
        limiter.recordFailure("1.1.1.1", " admin@cabinet.test ");

        assertTrue(limiter.isBlocked("1.1.1.1", "ADMIN@CABINET.TEST"),
                "changer la casse ne doit pas donner un nouveau quota");
    }

    @Test
    void uneAdresseIpEstBloqueeMemeAvecDesIdentifiantsDifferents() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, 100);

        limiter.recordFailure("2.2.2.2", "a@x.test");
        limiter.recordFailure("2.2.2.2", "b@x.test");
        limiter.recordFailure("2.2.2.2", "c@x.test");

        assertTrue(limiter.isBlocked("2.2.2.2", "d@x.test"),
                "password spraying : une meme IP qui echoue sur beaucoup de comptes");
        assertFalse(limiter.isBlocked("3.3.3.3", "d@x.test"));
    }

    @Test
    void desIdentifiantsDifferentsNeSeGenentPas() {
        LoginRateLimiter limiter = new LoginRateLimiter(1000, 1);

        limiter.recordFailure("3.3.3.3", "a@x.test");

        assertTrue(limiter.isBlocked("3.3.3.3", "a@x.test"));
        assertFalse(limiter.isBlocked("3.3.3.3", "b@x.test"));
    }
}
