package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.ReglementRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceStatsDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.ModePaiement;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comportement reel des listes sur une base (H2), pas sur des mocks : filtrage par
 * portefeuille et par departement en SQL, recherche, pagination et nombre de
 * requetes. Ce sont des proprietes de la requete generee, invisibles pour un
 * test unitaire qui remplacerait le repository.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListQueriesTest {

    @Autowired private ClientService clientService;
    @Autowired private CreanceService creanceService;
    @Autowired private ReglementService reglementService;
    @Autowired private UtilisateurService utilisateurService;

    @Autowired private DepartementRepository departementRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CreanceRepository creanceRepository;
    @Autowired private ReglementRepository reglementRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;

    private Departement deptA;
    private Departement deptB;
    private Utilisateur admin;
    private Utilisateur managerA;
    private Utilisateur agentA1;
    private Utilisateur agentA2;
    private Utilisateur managerB;

    @BeforeAll
    void donnees() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Role roleAdmin = role(RoleAgent.ADMIN);
        Role roleManager = role(RoleAgent.MANAGER);
        Role roleAgent = role(RoleAgent.AGENT);
        deptA = departementRepository.save(new Departement("Casablanca " + suffixe, "CA" + suffixe));
        deptB = departementRepository.save(new Departement("Rabat " + suffixe, "RB" + suffixe));
        admin = utilisateur("admin." + suffixe + "@test", roleAdmin, null);
        managerA = utilisateur("manager.a." + suffixe + "@test", roleManager, deptA);
        agentA1 = utilisateur("agent.a1." + suffixe + "@test", roleAgent, deptA);
        agentA2 = utilisateur("agent.a2." + suffixe + "@test", roleAgent, deptA);
        managerB = utilisateur("manager.b." + suffixe + "@test", roleManager, deptB);

        // Departement A : agent 1 a 3 clients (dont un au nom "100% Maroc"), agent 2 en a 1.
        // Aucune authentification pendant la preparation : donnees ecrites sans filtre de departement.
        tx.executeWithoutResult(s -> {
            client("100% Maroc SARL", agentA1, deptA);
            client("Alpha SARL", agentA1, deptA);
            client("Beta SARL", agentA1, deptA);
            Client autre = client("Gamma SARL", agentA2, deptA);

            // 60 creances (statuts varies) et 60 reglements, tous sur des clients de l'agent 1.
            Client c = clientRepository.findByRaisonSociale("Alpha SARL");
            for (int i = 0; i < 60; i++) {
                Creance creance = new Creance();
                creance.setNumFacture("A-" + suffixe + "-" + i);
                creance.setMontantFacture(new BigDecimal("1000.00"));
                creance.setEcheance(LocalDate.now().plusDays(30));
                creance.setAgentRecouv(agentA1);
                creance.setClient(c);
                creance.setDepartement(deptA);
                creance.setMontantEncaisse(i % 3 == 0 ? new BigDecimal("1000.00") : BigDecimal.ZERO);
                creanceRepository.save(creance);

                Reglement r = new Reglement();
                r.setMontant(new BigDecimal("10.00"));
                r.setDateReglement(LocalDate.now());
                r.setModePaiement(ModePaiement.VIREMENT);
                r.setStatut(StatutReglement.EFFECTUE);
                r.setCreance(creance);
                r.setDepartement(deptA);
                r.setAgentRecouv(agentA1);
                reglementRepository.save(r);
            }
            // Une creance de l'agent 2, invisible pour l'agent 1.
            Creance c2 = new Creance();
            c2.setNumFacture("B-" + suffixe);
            c2.setMontantFacture(new BigDecimal("500.00"));
            c2.setEcheance(LocalDate.now().plusDays(30));
            c2.setAgentRecouv(agentA2);
            c2.setClient(autre);
            c2.setDepartement(deptA);
            creanceRepository.save(c2);
        });

        // Departement B : un client que le departement A ne doit jamais voir.
        tx.executeWithoutResult(s -> client("Client de B", managerB, deptB));
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unAgentNeVoitQueSesClients() {
        connecte(agentA1);

        Page<ClientResponseDTO> page = clientService.list(null, PageRequest.of(0, 50));

        assertEquals(3, page.getTotalElements());
        assertTrue(page.getContent().stream().noneMatch(c -> c.getRaisonSociale().equals("Gamma SARL")),
                "le client d'un autre agent ne doit pas apparaitre");
    }

    @Test
    void unManagerVoitTousLesClientsDeSonDepartementEtRienDesAutres() {
        connecte(managerA);

        Page<ClientResponseDTO> page = clientService.list(null, PageRequest.of(0, 50));

        assertEquals(4, page.getTotalElements(), "le manager voit aussi les clients des agents de son departement");
        assertTrue(page.getContent().stream().noneMatch(c -> c.getRaisonSociale().equals("Client de B")),
                "un client d'un autre departement ne doit jamais apparaitre");
    }

    @Test
    void lManagerDuDepartementBNeVoitRienDuDepartementA() {
        connecte(managerB);

        Page<ClientResponseDTO> page = clientService.list(null, PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals("Client de B", page.getContent().get(0).getRaisonSociale());
    }

    @Test
    void lAdminVoitTousLesDepartements() {
        connecte(admin);

        Page<ClientResponseDTO> page = clientService.list(null, PageRequest.of(0, 50));

        assertEquals(5, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(c -> c.getRaisonSociale().equals("Client de B")));
    }

    @Test
    void laRecherchePermetDeFiltrerSurLaRaisonSociale() {
        connecte(managerA);

        Page<ClientResponseDTO> page = clientService.list("alph", PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals("Alpha SARL", page.getContent().get(0).getRaisonSociale());
    }

    /** "%" saisi par l'utilisateur est un caractere ordinaire, pas un joker LIKE. */
    @Test
    void lesJokersLikeSontEchappes() {
        connecte(managerA);

        Page<ClientResponseDTO> page = clientService.list("%", PageRequest.of(0, 50));

        assertEquals(1, page.getTotalElements());
        assertEquals("100% Maroc SARL", page.getContent().get(0).getRaisonSociale());
    }

    @Test
    void unTriSurUneProprieteNonAutoriseeEstIgnoreAuLieuDeFaireUneErreur() {
        connecte(managerA);

        Page<ClientResponseDTO> page = clientService.list(null,
                PageRequest.of(0, 50, Sort.by("agentRecouv.motDePasse")));

        assertEquals(4, page.getTotalElements());
    }

    @Test
    void lesReglementsSontCloissonesParPortefeuilleAgent() {
        connecte(agentA2);

        Page<ReglementResponseDTO> page = reglementService.list(null, null, null, PageRequest.of(0, 50));

        assertEquals(0, page.getTotalElements(), "l'agent 2 n'a aucun reglement dans son portefeuille");
    }

    /**
     * Le bug N+1 : chaque reglement declenchait des requetes pour sa creance, son
     * client et ses agents. Une page de 50 doit rester a quelques requetes, quel
     * que soit le nombre de lignes.
     */
    @Test
    void uneListeDe50ReglementsSeChargeEnPeuDeRequetes() {
        connecte(managerA);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        Page<ReglementResponseDTO> page = reglementService.list(null, null, null, PageRequest.of(0, 50));

        assertEquals(50, page.getContent().size());
        assertEquals(60, page.getTotalElements());
        assertNotNull(page.getContent().get(0).getClientName(), "le mapping doit avoir lu le client");
        long requetes = stats.getPrepareStatementCount();
        assertTrue(requetes <= 3, "50 reglements ont declenche " + requetes + " requetes SQL (N+1 ?)");
    }

    @Test
    void lesCreancesAvecFiltreDeStatutEtPagination() {
        connecte(managerA);

        Page<?> page = creanceService.list(null, StatutCreance.PAYEE, null, PageRequest.of(0, 10));

        assertEquals(20, page.getTotalElements(), "1 creance sur 3 est entierement encaissee");
        assertEquals(10, page.getContent().size());
    }

    @Test
    void lesStatistiquesSontCalculeesEnBaseParStatut() {
        connecte(managerA);

        CreanceStatsDTO stats = creanceService.stats();

        assertEquals(61, stats.totalCreances());
        assertEquals(20L, stats.parStatut().get(StatutCreance.PAYEE));
        assertEquals(0, new BigDecimal("60500.00").compareTo(stats.montantTotal()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(stats.montantEncaisse()));
    }

    @Test
    void lesStatistiquesDUnAgentNeComptentQueSonPortefeuille() {
        connecte(agentA2);

        CreanceStatsDTO stats = creanceService.stats();

        assertEquals(1, stats.totalCreances());
    }

    @Test
    void lesUtilisateursSeFiltrentParDepartementEtParRole() {
        connecte(admin);

        assertEquals(3, utilisateurService.list(null, null, deptA.getId(), PageRequest.of(0, 50)).getTotalElements(),
                "manager A + agent A1 + agent A2 : ni l'admin (sans departement) ni le manager B");
        assertEquals(2, utilisateurService.list(null, RoleAgent.AGENT, deptA.getId(), PageRequest.of(0, 50)).getTotalElements());
        assertEquals(1, utilisateurService.list(null, RoleAgent.MANAGER, deptA.getId(), PageRequest.of(0, 50)).getTotalElements());
        assertEquals(1, utilisateurService.list(null, RoleAgent.ADMIN, null, PageRequest.of(0, 50)).getTotalElements());
    }

    // ------------------------------------------------------------------ aides

    /** Reproduit ce que fait JwtAuthenticationFilter : le principal porte identite, role et departement. */
    private void connecte(Utilisateur u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private Role role(RoleAgent nom) {
        return roleRepository.findByNom(nom).orElseGet(() -> {
            Role r = new Role();
            r.setNom(nom);
            return roleRepository.save(r);
        });
    }

    private Utilisateur utilisateur(String email, Role role, Departement departement) {
        Utilisateur u = new Utilisateur();
        u.setNom(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setMotDePasse("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha");
        u.setRole(role);
        u.setDepartement(departement);
        return utilisateurRepository.save(u);
    }

    private Client client(String raisonSociale, Utilisateur agent, Departement departement) {
        Client c = new Client();
        c.setRaisonSociale(raisonSociale);
        c.setEmail("contact@" + raisonSociale.replaceAll("\\W", "").toLowerCase() + ".test");
        c.setTelephone("0522" + Math.abs(raisonSociale.hashCode() % 1000000));
        c.setRc("RC-" + raisonSociale);
        c.setAdresse("1 rue Test");
        c.setIce("ICE-" + raisonSociale);
        c.setIdentiteFiscale("IF-" + raisonSociale);
        c.setAgentRecouv(agent);
        c.setDepartement(departement);
        return clientRepository.save(c);
    }
}
