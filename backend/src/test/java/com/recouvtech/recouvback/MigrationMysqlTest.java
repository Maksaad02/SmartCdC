package com.recouvtech.recouvback;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide les migrations Flyway sur un VRAI MySQL 8.4 (les autres tests tournent sur H2, ou ces
 * scripts, ecrits pour MySQL, ne s'executent pas).
 *
 * Le contexte demarre avec ddl-auto=validate : Hibernate verifie que chaque entite correspond
 * au schema produit par les migrations. Une colonne ajoutee a une entite sans migration, ou
 * l'inverse, fait donc echouer ce test au lieu de la production.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver"})
class MigrationMysqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("rec_ouv_db");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @Test
    void toutesLesMigrationsSAppliquentEtLeSchemaCorrespondAuxEntites() {
        // Si on est ici, le contexte a demarre : Flyway a migre ET Hibernate a valide le schema.
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success = 1 order by installed_rank", String.class);

        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7"), versions);
    }

    /**
     * Regression : le deuxieme client sans ICE echouait (chaine vide dupliquee dans une colonne UNIQUE).
     * Avec NULL, l'unicite ne joue que sur les valeurs saisies. Depuis la V7, elle est globale a
     * l'entreprise (plus par organisation).
     */
    @Test
    void plusieursClientsSansIceNeSeGenentPasMaisUnIceRenseigneResteUnique() {
        Long dep = departement("CLIENTS");
        String insert = "insert into client (raison_sociale, email, telephone, rc, adresse, ice, identite_fiscale, departement_id) "
                + "values (?, 'c@x.test', '0522', ?, '1 rue', ?, ?, ?)";

        jdbc.update(insert, "Sans ice 1", null, null, null, dep);
        jdbc.update(insert, "Sans ice 2", null, null, null, dep); // ne doit pas echouer

        jdbc.update(insert, "Avec ice 1", null, "ICE-42", null, dep);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update(insert, "Avec ice 2", null, "ICE-42", null, dep));
        // Meme dans un autre departement : un debiteur n'existe qu'une fois dans l'entreprise.
        Long autre = departement("AUTRE_ICE");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update(insert, "Avec ice 3", null, "ICE-42", null, autre));
    }

    /**
     * Le departement est porte par chaque table metier, et la coherence de cette denormalisation est
     * garantie EN BASE : une creance ne peut pas pointer le client d'un autre departement, un reglement
     * ou une relance ne peuvent pas quitter le departement de leur creance.
     */
    @Test
    void lesClesEtrangeresComposeesEmpechentUneIncoherenceDeDepartement() {
        Long x = departement("COHER_X");
        Long y = departement("COHER_Y");
        Long agent = utilisateur("coher@test", x);
        Long clientX = client("Coherent X", x);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> creance("F-COHER-1", clientX, y),
                "une creance du departement Y ne peut pas appartenir a un client du departement X");

        Long creanceX = creance("F-COHER-2", clientX, x);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into reglement (montant, creance_id, departement_id) values (10, ?, ?)", creanceX, y),
                "un reglement ne peut pas quitter le departement de sa creance");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into relance (creance_id, id_agent_recouv, departement_id) values (?, ?, ?)", creanceX, agent, y),
                "une relance ne peut pas quitter le departement de sa creance");

        // Les cas coherents passent.
        jdbc.update("insert into reglement (montant, creance_id, departement_id) values (10, ?, ?)", creanceX, x);
        jdbc.update("insert into relance (creance_id, id_agent_recouv, departement_id) values (?, ?, ?)", creanceX, agent, x);
    }

    @Test
    void unDepartementUtiliseNePeutPasEtreSupprime() {
        Long dep = departement("UTILISE");
        client("Client du departement utilise", dep);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("delete from departement where id = ?", dep));
    }

    @Test
    void leDepartementSiegeEtLesTroisRolesExistent() {
        assertEquals(1, jdbc.queryForObject("select count(*) from departement where code = 'SIEGE'", Integer.class));
        assertEquals(List.of("ADMIN", "MANAGER", "AGENT"),
                jdbc.queryForList("select nom from role order by field(nom, 'ADMIN', 'MANAGER', 'AGENT')", String.class));
    }

    private Long departement(String code) {
        jdbc.update("insert into departement (nom, code, actif, date_creation) values (?, ?, 1, now())", "Dept " + code, code);
        return jdbc.queryForObject("select id from departement where code = ?", Long.class, code);
    }

    private Long client(String raisonSociale, Long departementId) {
        jdbc.update("insert into client (raison_sociale, email, telephone, adresse, departement_id) "
                + "values (?, 'c@x.test', '0522', '1 rue', ?)", raisonSociale, departementId);
        return jdbc.queryForObject("select id from client where raison_sociale = ?", Long.class, raisonSociale);
    }

    private Long creance(String numFacture, Long clientId, Long departementId) {
        jdbc.update("insert into creance (num_facture, client_id, departement_id) values (?, ?, ?)",
                numFacture, clientId, departementId);
        return jdbc.queryForObject("select id from creance where num_facture = ?", Long.class, numFacture);
    }

    private Long utilisateur(String email, Long departementId) {
        Long role = jdbc.queryForObject("select id from role where nom = 'AGENT'", Long.class);
        jdbc.update("insert into utilisateur (email, mot_de_passe, nom, role_id, departement_id) values (?, 'x', 'Agent', ?, ?)",
                email, role, departementId);
        return jdbc.queryForObject("select id_agent_recouv from utilisateur where email = ?", Long.class, email);
    }

    @Test
    void lesIndexDeLaMigrationV4Existent() {
        List<String> index = jdbc.queryForList(
                "select distinct index_name from information_schema.statistics "
                        + "where table_schema = database() and index_name like 'idx\\_%'", String.class);

        assertTrue(index.containsAll(List.of(
                "idx_creance_dept_supprimee_statut", "idx_creance_echeance",
                "idx_relance_statut", "idx_relance_date", "idx_reglement_statut")), index.toString());
    }

    /**
     * Chemin de mise a jour reel : une base a la version 2 qui contient des mots de passe en
     * clair (l'ancien comportement) est migree vers la derniere version.
     */
    @Test
    void laMigrationV3HacheLesMotsDePasseEnClairSurUneVraieBase() {
        MySQLContainer<?> autre = new MySQLContainer<>("mysql:8.4").withDatabaseName("legacy");
        autre.start();
        try {
            Flyway v2 = Flyway.configure()
                    .dataSource(autre.getJdbcUrl(), autre.getUsername(), autre.getPassword())
                    .target("2").load();
            v2.migrate();

            JdbcTemplate legacy = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    autre.getJdbcUrl(), autre.getUsername(), autre.getPassword()));
            legacy.update("insert into organisation (nom, actif, date_creation) values ('Org', 1, now())");
            Long org = legacy.queryForObject("select id from organisation where nom = 'Org'", Long.class);
            // Les roles AGENT/ADMIN sont crees au runtime (RoleInitializer), pas par le SQL : on les simule.
            legacy.update("insert into role (nom) values ('AGENT'), ('ADMIN')");
            Long role = legacy.queryForObject("select id from role where nom = 'AGENT'", Long.class);
            Long superAdmin = legacy.queryForObject("select id from role where nom = 'SUPER_ADMIN'", Long.class);
            legacy.update("insert into utilisateur (email, mot_de_passe, nom, role_id, organisation_id) "
                    + "values ('ancien@test', 'mot-de-passe-en-clair', 'Ancien', ?, ?)", role, org);
            legacy.update("insert into utilisateur (email, mot_de_passe, nom, role_id, organisation_id) "
                    + "values ('plateforme@test', 'x', 'Plateforme', ?, ?)", superAdmin, org);
            legacy.update("insert into client (raison_sociale, email, telephone, adresse, rc, ice, identite_fiscale, organisation_id) "
                    + "values ('Debiteur', 'd@x.test', '0522', '1 rue', 'RC1', 'ICE1', 'IF1', ?)", org);
            Long clientId = legacy.queryForObject("select id from client where raison_sociale = 'Debiteur'", Long.class);
            legacy.update("insert into creance (num_facture, client_id, organisation_id) values ('F-LEGACY', ?, ?)", clientId, org);

            Flyway latest = Flyway.configure()
                    .dataSource(autre.getJdbcUrl(), autre.getUsername(), autre.getPassword()).load();
            latest.migrate();

            String stocke = legacy.queryForObject(
                    "select mot_de_passe from utilisateur where email = 'ancien@test'", String.class);
            assertTrue(stocke.startsWith("$2"), "le mot de passe doit etre hache : " + stocke);
            assertTrue(new BCryptPasswordEncoder().matches("mot-de-passe-en-clair", stocke),
                    "l'ancien mot de passe doit continuer a fonctionner");

            // V7 : tout est rattache au departement « Siege » ; le SUPER_ADMIN devient ADMIN, sans departement.
            Long siege = legacy.queryForObject("select id from departement where code = 'SIEGE'", Long.class);
            assertEquals(siege, legacy.queryForObject(
                    "select departement_id from utilisateur where email = 'ancien@test'", Long.class));
            assertEquals("ADMIN", legacy.queryForObject(
                    "select r.nom from utilisateur u join role r on r.id = u.role_id where u.email = 'plateforme@test'", String.class));
            assertEquals(null, legacy.queryForObject(
                    "select departement_id from utilisateur where email = 'plateforme@test'", Long.class));
            assertEquals(siege, legacy.queryForObject("select departement_id from client where raison_sociale = 'Debiteur'", Long.class));
            assertEquals(siege, legacy.queryForObject("select departement_id from creance where num_facture = 'F-LEGACY'", Long.class));
        } finally {
            autre.stop();
        }
    }
}
