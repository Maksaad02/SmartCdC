package com.recouvtech.recouvback.security;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.ReglementRepository;
import com.recouvtech.recouvback.dao.RelanceRepository;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.ClientDTO.ClientRequestDTO;
import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.DashboardDepartementsDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.ModePaiement;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.service.ClientService;
import com.recouvtech.recouvback.service.CreanceService;
import com.recouvtech.recouvback.service.EmailService;
import com.recouvtech.recouvback.service.PenalitesScheduler;
import com.recouvtech.recouvback.service.ReglementService;
import com.recouvtech.recouvback.service.RelanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LE test qui compte le plus du cloisonnement par departement.
 *
 * Il tourne sur une vraie base (H2), avec un vrai gestionnaire de transactions (donc le vrai filtre
 * Hibernate), et une identite posee comme le fait JwtAuthenticationFilter. Il prouve :
 *  - un MANAGER ne voit rien d'un autre departement, par AUCUNE voie : liste, comptage, chargement par id,
 *    recherche par numero, agregats, impayes du chatbot ;
 *  - l'ADMIN voit tous les departements ; un AGENT ne voit que son portefeuille, dans son departement ;
 *  - les taches systeme (sans utilisateur) travaillent sur toute l'entreprise ;
 *  - le departement d'une ressource est impose ou herite, jamais fourni par la requete.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DepartementIsolationTest {

    @Autowired private ClientService clientService;
    @Autowired private CreanceService creanceService;
    @Autowired private ReglementService reglementService;
    @Autowired private RelanceService relanceService;
    @Autowired private PenalitesScheduler penalitesScheduler;

    @Autowired private DepartementRepository departementRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CreanceRepository creanceRepository;
    @Autowired private ReglementRepository reglementRepository;
    @Autowired private RelanceRepository relanceRepository;

    /** Evite un vrai envoi SMTP quand une creation de creance declenche la relance immediate. */
    @MockitoBean private EmailService emailService;

    private String sfx;
    private Departement deptA;
    private Departement deptB;
    /** Departement reserve aux tests de creation : ils ne polluent pas les comptages de A et B. */
    private Departement deptC;
    private Utilisateur admin;
    private Utilisateur managerA;
    private Utilisateur agentA1;
    private Utilisateur agentA2;
    private Utilisateur managerB;
    private Utilisateur managerC;

    private Client clientA1;
    private Client clientA2;
    private Client clientB;
    private Creance creanceA1;
    private Creance creanceB;
    private Reglement reglementA1;
    private Reglement reglementB;
    private Relance relanceA1;
    private Relance relanceB;

    @BeforeAll
    void donnees() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        Role roleAdmin = role(RoleAgent.ADMIN);
        Role roleManager = role(RoleAgent.MANAGER);
        Role roleAgent = role(RoleAgent.AGENT);

        deptA = departementRepository.save(new Departement("Casablanca " + sfx, "CA" + sfx));
        deptB = departementRepository.save(new Departement("Rabat " + sfx, "RB" + sfx));
        deptC = departementRepository.save(new Departement("Fes " + sfx, "FS" + sfx));
        admin = utilisateur("admin", roleAdmin, null);
        managerA = utilisateur("manager.a", roleManager, deptA);
        agentA1 = utilisateur("agent.a1", roleAgent, deptA);
        agentA2 = utilisateur("agent.a2", roleAgent, deptA);
        managerB = utilisateur("manager.b", roleManager, deptB);
        managerC = utilisateur("manager.c", roleManager, deptC);

        // Aucune identite pendant la preparation : ecriture sans filtre.
        clientA1 = client("Client A1", agentA1, deptA);
        clientA2 = client("Client A2", agentA2, deptA);
        clientB = client("Client B", managerB, deptB);
        creanceA1 = creance("FA-1-" + sfx, clientA1, agentA1, deptA, "1000.00");
        creance("FA-2-" + sfx, clientA2, agentA2, deptA, "500.00");
        creanceB = creance("FB-1-" + sfx, clientB, managerB, deptB, "2000.00");
        reglementA1 = reglement(creanceA1, agentA1, deptA);
        reglementB = reglement(creanceB, managerB, deptB);
        relanceA1 = relance(creanceA1, agentA1, deptA);
        relanceB = relance(creanceB, managerB, deptB);
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- MANAGER : listes et comptages

    @Test
    void unManagerNeVoitAucunClientDUnAutreDepartementParLesRepositoriesNonPlusQueParLesServices() {
        connecte(managerA);

        // Repository brut, sans specification : c'est le filtre Hibernate qui cloisonne.
        List<Client> tous = clientRepository.findAll();
        assertTrue(tous.stream().allMatch(c -> c.getDepartement().getId().equals(deptA.getId())),
                "findAll() a renvoye un client d'un autre departement");
        assertFalse(tous.stream().anyMatch(c -> c.getId().equals(clientB.getId())));
        assertEquals(2, tous.stream().filter(c -> c.getRaisonSociale().endsWith(sfx)).count());

        assertEquals(2, clientService.list(sfx, PageRequest.of(0, 50)).getTotalElements());
        assertEquals(clientRepository.findAll().size(), clientRepository.count(),
                "le comptage (count) doit etre filtre comme la liste");
    }

    @Test
    void unManagerNeVoitQueLesCreancesReglementsEtRelancesDeSonDepartement() {
        connecte(managerA);

        assertTrue(creanceRepository.findAll().stream().noneMatch(c -> c.getId().equals(creanceB.getId())));
        assertTrue(reglementRepository.findAll().stream().noneMatch(r -> r.getId().equals(reglementB.getId())));
        assertTrue(relanceRepository.findAll().stream().noneMatch(r -> r.getId().equals(relanceB.getId())));
        assertEquals(2, creanceService.list(sfx, null, null, PageRequest.of(0, 50)).getTotalElements());
        assertEquals(1, reglementService.list(sfx, null, null, PageRequest.of(0, 50)).getTotalElements(),
                "un seul reglement dans le departement A");
    }

    // ---------------------------------------------------------------- MANAGER : chargement par id (em.find)

    /**
     * Le cas piege : le filtre Hibernate ne s'applique pas a em.find. C'est pour cela que les
     * repositories redefinissent findById en JPQL. Sans cela, deviner un id suffirait a lire un autre
     * departement.
     */
    @Test
    void unManagerNePeutPasChargerParIdUneRessourceDUnAutreDepartement() {
        connecte(managerA);

        assertTrue(clientRepository.findById(clientB.getId()).isEmpty());
        assertTrue(creanceRepository.findById(creanceB.getId()).isEmpty());
        assertTrue(reglementRepository.findById(reglementB.getId()).isEmpty());
        assertTrue(relanceRepository.findById(relanceB.getId()).isEmpty());
        assertNull(creanceRepository.findByNumFacture(creanceB.getNumFacture()));

        // ... mais pas de faux positif : son propre departement reste accessible.
        assertTrue(clientRepository.findById(clientA1.getId()).isPresent());
        assertTrue(creanceRepository.findById(creanceA1.getId()).isPresent());
        assertTrue(reglementRepository.findById(reglementA1.getId()).isPresent());
        assertTrue(relanceRepository.findById(relanceA1.getId()).isPresent());
    }

    @Test
    void lesServicesRepondentIntrouvablePourUneRessourceDUnAutreDepartement() {
        connecte(managerA);
        ClientRequestDTO dto = new ClientRequestDTO();
        dto.setRaisonSociale("Pirate");
        dto.setEmail("p@x.test");
        dto.setTelephone("0600000000");
        dto.setAdresse("x");

        assertThrows(RessourceIntrouvableException.class, () -> clientService.getClientById(clientB.getId()));
        assertThrows(RessourceIntrouvableException.class, () -> clientService.updateClient(clientB.getId(), dto));
        assertThrows(RessourceIntrouvableException.class, () -> clientService.deleteClient(clientB.getId()));
        assertThrows(RessourceIntrouvableException.class, () -> creanceService.getByNumFacture(creanceB.getNumFacture()));
        assertThrows(RessourceIntrouvableException.class, () -> creanceService.deleteCreance(creanceB.getNumFacture()));
        assertThrows(RessourceIntrouvableException.class, () -> reglementService.getById(reglementB.getId()));
        assertThrows(RessourceIntrouvableException.class, () -> reglementService.delete(reglementB.getId()));
        assertThrows(RessourceIntrouvableException.class, () -> relanceService.getById(relanceB.getId()));
        assertThrows(RessourceIntrouvableException.class, () -> relanceService.delete(relanceB.getId()));
    }

    // ---------------------------------------------------------------- MANAGER : agregats et chatbot

    @Test
    void lesAgregatsEtLesImpayesDuChatbotSontCloissonnes() {
        connecte(managerA);

        assertEquals(2, creanceService.stats().totalCreances());
        assertEquals(new BigDecimal("1500.00"), creanceService.stats().montantTotal().setScale(2));

        List<CreanceResponseDTO> impayes = creanceService.getAllUnpaidCreances(200);
        assertTrue(impayes.stream().noneMatch(c -> c.getNumFacture().equals(creanceB.getNumFacture())),
                "les impayes servis au chatbot ne doivent pas exposer un autre departement");
        assertTrue(creanceService.getCreancesByClientId(clientB.getId()).isEmpty());
        assertTrue(clientService.searchClients("Client B").isEmpty());
    }

    @Test
    void unManagerNeVoitQuUnDepartementDansLeComparatifSiLeFiltreEtaitContourne() {
        // Le comparatif est reserve aux ADMIN (@PreAuthorize) ; ici on verifie en plus que meme appele
        // par un MANAGER, le filtre SQL limite l'agregat a son departement (defense en profondeur).
        connecte(managerA);

        DashboardDepartementsDTO dash = creanceService.statsParDepartement();

        var ligneB = dash.departements().stream().filter(d -> d.departementId().equals(deptB.getId())).findFirst();
        assertTrue(ligneB.isEmpty() || ligneB.get().nbCreances() == 0,
                "les creances du departement B ne doivent pas etre comptees pour un MANAGER de A");
    }

    // ---------------------------------------------------------------- AGENT : portefeuille dans le departement

    @Test
    void unAgentNeVoitQueSonPortefeuilleDansSonDepartement() {
        connecte(agentA1);

        assertEquals(1, clientService.list(sfx, PageRequest.of(0, 50)).getTotalElements());
        assertEquals(1, creanceService.list(sfx, null, null, PageRequest.of(0, 50)).getTotalElements());
        assertEquals(1, creanceService.stats().totalCreances());
    }

    @Test
    void unAgentNAccedePasAuClientDUnCollegueDeSonDepartement() {
        connecte(agentA1);

        // Meme departement (donc visible du filtre SQL) mais pas SON portefeuille : refus applicatif.
        assertThrows(AccessDeniedException.class, () -> clientService.getClientById(clientA2.getId()));
    }

    // ---------------------------------------------------------------- ADMIN et taches systeme

    @Test
    void unAdminVoitTousLesDepartements() {
        connecte(admin);

        assertEquals(3, clientService.list(sfx, PageRequest.of(0, 50)).getTotalElements());
        assertNotNull(clientService.getClientById(clientB.getId()));
        assertNotNull(creanceService.getByNumFacture(creanceB.getNumFacture()));
        assertNotNull(reglementService.getById(reglementB.getId()));
    }

    @Test
    void leComparatifDesDepartementsConsolideTout() {
        connecte(admin);

        DashboardDepartementsDTO dash = creanceService.statsParDepartement();

        var a = dash.departements().stream().filter(d -> d.departementId().equals(deptA.getId())).findFirst().orElseThrow();
        var b = dash.departements().stream().filter(d -> d.departementId().equals(deptB.getId())).findFirst().orElseThrow();
        assertEquals(2, a.nbCreances());
        assertEquals(1, b.nbCreances());
        assertEquals(0, new BigDecimal("1500.00").compareTo(a.montantFacture()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(b.montantFacture()));
        assertTrue(dash.global().nbCreances() >= 3);
        long somme = dash.departements().stream().mapToLong(d -> d.nbCreances()).sum();
        assertEquals(somme, dash.global().nbCreances(), "le global est la somme des departements");
    }

    /** Taches planifiees et ecouteurs asynchrones : pas d'utilisateur, donc aucun filtre. */
    @Test
    void sansUtilisateurLesTachesSystemeVoientToutesLesDonnees() {
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        assertTrue(clientRepository.findById(clientB.getId()).isPresent());
        assertTrue(clientRepository.findAll().stream().anyMatch(c -> c.getId().equals(clientA1.getId())));
        assertTrue(clientRepository.findAll().stream().anyMatch(c -> c.getId().equals(clientB.getId())));
        assertDoesNotThrow(() -> penalitesScheduler.recalculerChaqueNuit());
    }

    @Test
    void unMemeIdentifiantEstFiltreAChaqueRequeteSansFuiteEntreSessions() {
        // Alternance rapide des identites sur le meme thread : aucune ne doit heriter du filtre de l'autre.
        for (int i = 0; i < 3; i++) {
            connecte(managerA);
            assertTrue(clientRepository.findById(clientB.getId()).isEmpty());
            SecurityContextHolder.clearContext();
            assertTrue(clientRepository.findById(clientB.getId()).isPresent());
            connecte(admin);
            assertTrue(clientRepository.findById(clientB.getId()).isPresent());
            SecurityContextHolder.clearContext();
        }
    }

    // ---------------------------------------------------------------- creation : departement impose ou herite

    @Test
    void unManagerNePeutPasCreerUnClientDansUnAutreDepartement() {
        connecte(managerA);
        ClientRequestDTO dto = clientDto("Intrus " + sfx);
        dto.setDepartementId(deptB.getId());

        assertThrows(AccessDeniedException.class, () -> clientService.createClient(dto));
    }

    @Test
    void unClientCreeParUnManagerEstDansSonDepartementMemeSansLePreciser() {
        connecte(managerC);

        ClientResponseDTO cree = clientService.createClient(clientDto("Nouveau C " + sfx));

        assertEquals(deptC.getId(), cree.getDepartementId());
    }

    @Test
    void uneCreanceNePeutPasEtreCreeePourLeClientDUnAutreDepartement() {
        connecte(managerA);
        CreanceRequestDTO dto = creanceDto("FX-" + sfx, clientB.getRaisonSociale());

        // Le client du departement B est invisible : "introuvable", sans confirmer qu'il existe.
        assertThrows(RessourceIntrouvableException.class, () -> creanceService.createCreance(dto));
    }

    @Test
    void lAdminCreeDansUnDepartementEtToutLeHeriteEnCascade() {
        connecte(admin);
        ClientRequestDTO clientDto = clientDto("Client Admin C " + sfx);
        clientDto.setDepartementId(deptC.getId());
        ClientResponseDTO client = clientService.createClient(clientDto);
        assertEquals(deptC.getId(), client.getDepartementId());

        CreanceResponseDTO creance = creanceService.createCreance(creanceDto("FCASC-" + sfx, client.getRaisonSociale()));
        assertEquals(deptC.getId(), creance.getDepartementId(), "la creance herite du departement de son client");

        ReglementRequestDTO reglementDto = new ReglementRequestDTO();
        reglementDto.setNumFacture(creance.getNumFacture());
        reglementDto.setMontant(new BigDecimal("100.00"));
        reglementDto.setDateReglement(LocalDate.now());
        reglementDto.setModePaiement(ModePaiement.VIREMENT);
        reglementDto.setStatut(StatutReglement.EFFECTUE);
        reglementDto.setAgentName(admin.getNom());
        ReglementResponseDTO reglement = reglementService.create(reglementDto);
        assertNotNull(reglement.getId());
        Reglement enBase = reglementRepository.findById(reglement.getId()).orElseThrow();
        assertEquals(deptC.getId(), enBase.getDepartement().getId(), "le reglement herite du departement de sa creance");
        List<Relance> relances = relanceRepository.findByCreanceNumFacture(creance.getNumFacture());
        assertEquals(3, relances.size(), "trois relances automatiques par creance");
        assertTrue(relances.stream().allMatch(r -> r.getDepartement().getId().equals(deptC.getId())),
                "les relances automatiques heritent aussi du departement de la creance");

        // ... et le departement A n'en voit rien.
        connecte(managerA);
        assertThrows(RessourceIntrouvableException.class, () -> creanceService.getByNumFacture(creance.getNumFacture()));
    }

    @Test
    void unAdminDoitPreciserLeDepartementDUnNouveauClient() {
        connecte(admin);

        assertThrows(IllegalArgumentException.class, () -> clientService.createClient(clientDto("Sans dept " + sfx)));
    }

    // ------------------------------------------------------------------ aides

    /** Reproduit JwtAuthenticationFilter : le principal porte identite, role et departement. */
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

    private Utilisateur utilisateur(String nom, Role role, Departement departement) {
        Utilisateur u = new Utilisateur();
        u.setNom(nom + " " + sfx);
        u.setEmail(nom + "." + sfx + "@test");
        u.setMotDePasse("$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha");
        u.setRole(role);
        u.setDepartement(departement);
        return utilisateurRepository.save(u);
    }

    private Client client(String nom, Utilisateur agent, Departement departement) {
        Client c = new Client();
        c.setRaisonSociale(nom + " " + sfx);
        c.setEmail("contact@" + nom.replaceAll("\\W", "").toLowerCase() + ".test");
        c.setTelephone("0522000000");
        c.setAdresse("1 rue Test");
        c.setIce("ICE-" + nom + sfx);
        c.setAgentRecouv(agent);
        c.setDepartement(departement);
        return clientRepository.save(c);
    }

    private Creance creance(String num, Client client, Utilisateur agent, Departement departement, String montant) {
        Creance c = new Creance();
        c.setNumFacture(num);
        c.setMontantFacture(new BigDecimal(montant));
        c.setEcheance(LocalDate.now().plusDays(30));
        c.setClient(client);
        c.setAgentRecouv(agent);
        c.setDepartement(departement);
        c.setStatut(StatutCreance.IMPAYEE);
        return creanceRepository.save(c);
    }

    private Reglement reglement(Creance creance, Utilisateur agent, Departement departement) {
        Reglement r = new Reglement();
        r.setMontant(new BigDecimal("10.00"));
        r.setDateReglement(LocalDate.now());
        r.setModePaiement(ModePaiement.VIREMENT);
        r.setStatut(StatutReglement.NON_EFFECTUE);
        r.setReference("REF-" + creance.getNumFacture());
        r.setCreance(creance);
        r.setAgentRecouv(agent);
        r.setDepartement(departement);
        return reglementRepository.save(r);
    }

    private Relance relance(Creance creance, Utilisateur agent, Departement departement) {
        Relance r = new Relance();
        r.setCreance(creance);
        r.setAgentRecouv(agent);
        r.setDepartement(departement);
        r.setTypeRelance(TypeRelance.EMAIL);
        r.setStatutRelance(StatutRelance.EN_ATTENTE);
        r.setDateRelance(LocalDate.now());
        return relanceRepository.save(r);
    }

    private ClientRequestDTO clientDto(String nom) {
        ClientRequestDTO dto = new ClientRequestDTO();
        dto.setRaisonSociale(nom);
        dto.setEmail("contact@" + UUID.randomUUID().toString().substring(0, 6) + ".test");
        dto.setTelephone("0522000000");
        dto.setAdresse("1 rue Test");
        return dto;
    }

    private CreanceRequestDTO creanceDto(String num, String clientName) {
        CreanceRequestDTO dto = new CreanceRequestDTO();
        dto.setNumFacture(num);
        dto.setEcheance(LocalDate.now().plusDays(30));
        dto.setMontantFacture(new BigDecimal("1000.00"));
        dto.setClientName(clientName);
        return dto;
    }
}
