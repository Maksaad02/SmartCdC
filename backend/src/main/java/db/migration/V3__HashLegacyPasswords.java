package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hache les mots de passe enregistres en clair.
 *
 * Avant la correction de UtilisateurService, tout compte cree ou modifie via
 * /api/utilisateurs etait stocke tel que saisi. Ces comptes ne pouvaient pas
 * se connecter (BCrypt compare un hash) et leur mot de passe etait lisible en
 * base. Cette migration les convertit sur place ; les valeurs deja hachees
 * (prefixe $2a$, $2b$ ou $2y$) ne sont pas touchees, elle est donc rejouable.
 */
public class V3__HashLegacyPasswords extends BaseJavaMigration {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    @Override
    public void migrate(Context context) throws SQLException {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        List<Object[]> aConvertir = new ArrayList<>();

        try (Statement select = context.getConnection().createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT id_agent_recouv, mot_de_passe FROM utilisateur")) {
            while (rs.next()) {
                String valeur = rs.getString("mot_de_passe");
                if (valeur != null && !BCRYPT.matcher(valeur).matches()) {
                    aConvertir.add(new Object[]{rs.getLong("id_agent_recouv"), valeur});
                }
            }
        }

        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE utilisateur SET mot_de_passe = ? WHERE id_agent_recouv = ?")) {
            for (Object[] ligne : aConvertir) {
                update.setString(1, encoder.encode((String) ligne[1]));
                update.setLong(2, (Long) ligne[0]);
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
