package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V3HashLegacyPasswordsTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Test
    void hacheLesMotsDePasseEnClairEtLaisseLesHashInchanges() throws Exception {
        String dejaHache = ENCODER.encode("MotDePasseDejaHache!");

        try (Connection cx = DriverManager.getConnection("jdbc:h2:mem:v3test;DB_CLOSE_DELAY=-1")) {
            try (Statement st = cx.createStatement()) {
                st.execute("CREATE TABLE utilisateur (id_agent_recouv BIGINT PRIMARY KEY, mot_de_passe VARCHAR(255))");
                st.execute("INSERT INTO utilisateur VALUES (1, 'en-clair-123')");
                st.execute("INSERT INTO utilisateur VALUES (2, '" + dejaHache + "')");
            }

            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(cx);

            new V3__HashLegacyPasswords().migrate(context);

            try (Statement st = cx.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id_agent_recouv, mot_de_passe FROM utilisateur ORDER BY 1")) {
                assertTrue(rs.next());
                String converti = rs.getString(2);
                assertTrue(converti.startsWith("$2"), "le mot de passe en clair doit etre hache");
                assertTrue(ENCODER.matches("en-clair-123", converti), "l'ancien mot de passe doit toujours fonctionner");

                assertTrue(rs.next());
                assertEquals(dejaHache, rs.getString(2), "un hash existant ne doit pas etre re-hache");
            }

            // Rejouable : un second passage ne modifie plus rien.
            String avant;
            try (Statement st = cx.createStatement();
                 ResultSet rs = st.executeQuery("SELECT mot_de_passe FROM utilisateur WHERE id_agent_recouv = 1")) {
                rs.next();
                avant = rs.getString(1);
            }
            new V3__HashLegacyPasswords().migrate(context);
            try (Statement st = cx.createStatement();
                 ResultSet rs = st.executeQuery("SELECT mot_de_passe FROM utilisateur WHERE id_agent_recouv = 1")) {
                rs.next();
                assertEquals(avant, rs.getString(1));
            }
        }
    }
}
