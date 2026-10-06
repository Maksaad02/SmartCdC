package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dao.spec.CreanceSpecs;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceStatsDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.DashboardDepartementsDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.DepartementStatsDTO;
import com.recouvtech.recouvback.entity.enums.StatutCreance;
import com.recouvtech.recouvback.web.PageRequests;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.mapper.CreanceMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Lecture seule par defaut (associations paresseuses) ; les ecritures sont @Transactional. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreanceService {

    private static final Set<String> SORTABLE =
            Set.of("id", "numFacture", "echeance", "montantFacture", "statut");
    /** Bornes des reponses destinees au chatbot : elles finissent dans le contexte du LLM. */
    private static final int CHATBOT_DEFAULT_LIMIT = 50;
    private static final int CHATBOT_MAX_LIMIT = 200;
    private static final int RECALCUL_BATCH_SIZE = 200;

    private final CreanceRepository creanceRepository;
    private final AgentResolver agentResolver;
    private final ClientRepository clientRepository;
    private final PenaliteService penaliteService;
    private final RelanceAutomatiqueService relanceAutomatiqueService;
    private final CurrentUser currentUser;
    private final DepartementRepository departementRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Transactionnel : la creance et ses trois relances doivent etre creees
     * ensemble. Auparavant, une echeance nulle faisait echouer la creation des
     * relances APRES l'enregistrement de la creance, laissant une creance
     * orpheline sans relance.
     */
    @Transactional
    public CreanceResponseDTO createCreance(CreanceRequestDTO dto) {

        if (dto.getEcheance() == null) {
            throw new IllegalArgumentException("L'échéance est obligatoire");
        }
        if (creanceRepository.existsByNumFacture(dto.getNumFacture())) {
            throw new IllegalArgumentException("Une créance avec ce numéro de facture existe déjà : " + dto.getNumFacture());
        }

        // Le client est cherche par une requete filtree : un MANAGER ou un AGENT ne trouve que les
        // clients de son departement. La creance prend le departement de son client (jamais de la requete).
        Client client = clientRepository.findByRaisonSociale(dto.getClientName());
        if (client == null) {
            throw new RessourceIntrouvableException("Client not found with name: " + dto.getClientName());
        }
        Utilisateur agent = agentResolver.resolve(dto.getAgentName(), client.getDepartement().getId());

        Creance creance = CreanceMapper.fromRequestDto(dto, agent, client);
        creance.setMontantEncaisse(java.math.BigDecimal.ZERO);

        penaliteService.mettreAJourPenalites(creance);

        creance = creanceRepository.save(creance);

        relanceAutomatiqueService.creerRelancesAutomatiques(creance, agent);

        return CreanceMapper.toDto(creance);
    }

    /**
     * Les penalites sont recalculees en memoire pour l'affichage mais NE SONT PAS
     * reecrites ici : une lecture ne doit pas declencher N UPDATE (cf. GET qui
     * reecrivait toute la table). La persistance se fait a l'ecriture ou via
     * recalculerToutesPenalites().
     */
    public Page<CreanceResponseDTO> list(String q, StatutCreance statut, Long clientId, Pageable pageable) {
        Specification<Creance> spec = Specification.where(null);
        // Departement : filtre Hibernate (MANAGER, AGENT). Reste le portefeuille personnel d'un AGENT.
        if (currentUser.isAgent()) {
            spec = spec.and(CreanceSpecs.ownedBy(currentUser.email()));
        }
        if (statut != null) {
            spec = spec.and(CreanceSpecs.hasStatut(statut));
        }
        if (clientId != null) {
            spec = spec.and(CreanceSpecs.forClient(clientId));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(CreanceSpecs.matches(q.trim()));
        }
        return creanceRepository
                .findAll(spec, PageRequests.sanitize(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "echeance", "id")))
                .map(this::withPenalitesCalculees)
                .map(CreanceMapper::toDto);
    }

    /**
     * Agregats du tableau de bord, calcules en base par statut : le navigateur
     * ne telecharge plus toutes les creances pour les compter.
     */
    public CreanceStatsDTO stats() {
        return stats(null);
    }

    /**
     * Meme agregat, restreint a un departement quand {@code departementId} est fourni : reserve aux
     * ADMIN (un MANAGER ou un AGENT est deja limite a son perimetre ; une demande hors perimetre est
     * refusee plutot qu'ignoree en silence).
     */
    public CreanceStatsDTO stats(Long departementId) {
        List<Object[]> lignes;
        if (departementId != null) {
            if (!currentUser.isAdmin()) {
                throw new AccessDeniedException("Seul un administrateur peut filtrer par département");
            }
            if (!departementRepository.existsById(departementId)) {
                throw new RessourceIntrouvableException("Département introuvable");
            }
            lignes = creanceRepository.statsByStatutForDepartement(departementId);
        } else {
            // ADMIN : toute l'entreprise ; MANAGER : son departement (filtre Hibernate) ; AGENT : son portefeuille.
            lignes = currentUser.isAgent()
                    ? creanceRepository.statsByStatutForAgent(currentUser.email())
                    : creanceRepository.statsByStatut();
        }

        Map<StatutCreance, Long> parStatut = new EnumMap<>(StatutCreance.class);
        for (StatutCreance s : StatutCreance.values()) {
            parStatut.put(s, 0L);
        }
        long total = 0;
        BigDecimal facture = BigDecimal.ZERO;
        BigDecimal encaisse = BigDecimal.ZERO;
        BigDecimal penalites = BigDecimal.ZERO;
        for (Object[] l : lignes) {
            StatutCreance statut = (StatutCreance) l[0];
            long nombre = ((Number) l[1]).longValue();
            if (statut != null) {
                parStatut.merge(statut, nombre, Long::sum);
            }
            total += nombre;
            facture = facture.add(new BigDecimal(l[2].toString()));
            encaisse = encaisse.add(new BigDecimal(l[3].toString()));
            penalites = penalites.add(new BigDecimal(l[4].toString()));
        }
        return new CreanceStatsDTO(total, facture, encaisse, penalites, tauxRecouvrement(facture, penalites, encaisse), parStatut);
    }

    /**
     * Comparatif des departements (ADMIN) : une requete agregee, plus la ligne globale, somme des
     * departements. Chaque departement, meme sans creance, apparait (taux 0).
     */
    public DashboardDepartementsDTO statsParDepartement() {
        List<DepartementStatsDTO> departements = new java.util.ArrayList<>();
        long nb = 0;
        long retard = 0;
        BigDecimal facture = BigDecimal.ZERO;
        BigDecimal penalites = BigDecimal.ZERO;
        BigDecimal encaisse = BigDecimal.ZERO;
        for (Object[] l : creanceRepository.statsByDepartement()) {
            long n = ((Number) l[2]).longValue();
            BigDecimal f = new BigDecimal(l[3].toString());
            BigDecimal p = new BigDecimal(l[4].toString());
            BigDecimal e = new BigDecimal(l[5].toString());
            long r = ((Number) l[6]).longValue();
            departements.add(ligneDepartement(((Number) l[0]).longValue(), (String) l[1], n, r, f, p, e));
            nb += n;
            retard += r;
            facture = facture.add(f);
            penalites = penalites.add(p);
            encaisse = encaisse.add(e);
        }
        return new DashboardDepartementsDTO(ligneDepartement(null, "Global", nb, retard, facture, penalites, encaisse), departements);
    }

    private static DepartementStatsDTO ligneDepartement(Long id, String nom, long nb, long retard,
                                                        BigDecimal facture, BigDecimal penalites, BigDecimal encaisse) {
        return new DepartementStatsDTO(id, nom, nb, retard, facture, penalites, encaisse,
                facture.add(penalites).subtract(encaisse), tauxRecouvrement(facture, penalites, encaisse));
    }

    public CreanceResponseDTO getByNumFacture(String numFacture) {
        Creance creance = creanceRepository.findByNumFacture(numFacture);
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance non trouvée pour la facture : " + numFacture);
        }
        assertCanAccess(creance);
        return CreanceMapper.toDto(withPenalitesCalculees(creance));
    }

    /** Entite de la creance, si elle existe et appartient au perimetre de l'appelant (sinon 404 / 403). */
    public Creance chargerAccessible(String numFacture) {
        Creance creance = creanceRepository.findByNumFacture(numFacture);
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance non trouvée pour la facture : " + numFacture);
        }
        assertCanAccess(creance);
        return creance;
    }

    @Transactional
    public CreanceResponseDTO updateCreance(String numFacture, CreanceRequestDTO dto) {
        Creance creance = creanceRepository.findByNumFacture(numFacture);
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance non trouvée pour : " + numFacture);
        }
        assertCanAccess(creance);

        Client client = clientRepository.findByRaisonSociale(dto.getClientName());
        if (client == null) {
            throw new RessourceIntrouvableException("Client not found with name: " + dto.getClientName());
        }
        // Une creance reste dans le departement de son client (cle etrangere composite en base).
        if (!client.getDepartement().getId().equals(creance.getDepartement().getId())) {
            throw new IllegalArgumentException(
                    "Le client appartient à un autre département : une créance ne peut pas y être rattachée");
        }
        Utilisateur agent = dto.getAgentName() != null
                ? agentResolver.resolve(dto.getAgentName(), creance.getDepartement().getId())
                : creance.getAgentRecouv();

        CreanceMapper.updateFromRequestDto(creance, dto, agent, client);
        penaliteService.mettreAJourPenalites(creance);

        return CreanceMapper.toDto(creanceRepository.save(creance));
    }

    /**
     * Suppression logique : la suppression physique effacait en cascade
     * (orphanRemoval) tout l'historique des reglements, c'est-a-dire des pieces
     * comptables non reconstituables.
     */
    @Transactional
    public void deleteCreance(String numFacture) {
        Creance creance = creanceRepository.findByNumFacture(numFacture);
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance non trouvée pour : " + numFacture);
        }
        assertCanAccess(creance);
        creance.setSupprimee(true);
        creanceRepository.save(creance);
    }

    /** Recalcul persistant, reserve aux ADMIN (cf. CreanceController). */
    @Transactional
    public void recalculerToutesPenalites() {
        // Par lots : charger toutes les creances d'un coup saturait la memoire des
        // que la base grossissait. Chaque lot est ecrit puis detache.
        int numeroPage = 0;
        Page<Creance> lot;
        do {
            lot = creanceRepository.findAll(PageRequest.of(numeroPage++, RECALCUL_BATCH_SIZE, Sort.by("id")));
            lot.forEach(penaliteService::forcerRecalculPenalites);
            creanceRepository.saveAll(lot.getContent());
            entityManager.flush();
            entityManager.clear();
        } while (lot.hasNext());
    }

    /**
     * Creances d'un client donne (API chatbot).
     * Cloisonne : l'appelant ne voit que ce qui releve de son portefeuille.
     */
    public List<CreanceResponseDTO> getCreancesByClientId(Long clientId) {
        PageRequest limite = PageRequest.of(0, CHATBOT_MAX_LIMIT, Sort.by(Sort.Direction.DESC, "echeance"));
        List<Creance> creances = currentUser.isAgent()
                ? creanceRepository.findByClientIdAndAgentRecouv_Email(clientId, currentUser.email(), limite)
                : creanceRepository.findByClientId(clientId, limite);
        return creances.stream()
                .map(this::withPenalitesCalculees)
                .map(CreanceMapper::toDto)
                .collect(Collectors.toList());
    }

    /** Impayes (API chatbot), cloisonnes au portefeuille de l'appelant. */
    public List<CreanceResponseDTO> getAllUnpaidCreances(Integer limit) {
        int borne = limit == null ? CHATBOT_DEFAULT_LIMIT : Math.min(Math.max(limit, 1), CHATBOT_MAX_LIMIT);
        // Les plus gros montants d'abord : c'est ce qu'un agent veut voir en premier.
        PageRequest page = PageRequest.of(0, borne, Sort.by(Sort.Direction.DESC, "montantFacture"));
        List<Creance> creances = currentUser.isAgent()
                ? creanceRepository.findUnpaidByAgent(currentUser.email(), page)
                : creanceRepository.findUnpaid(page);
        return creances.stream()
                .map(this::withPenalitesCalculees)
                .map(CreanceMapper::toDto)
                .collect(Collectors.toList());
    }

    private Creance withPenalitesCalculees(Creance creance) {
        penaliteService.mettreAJourPenalites(creance);
        return creance;
    }

    private boolean canAccess(Creance creance) {
        return currentUser.canAccess(creance.getDepartement().getId(),
                creance.getAgentRecouv() != null ? creance.getAgentRecouv().getEmail() : null);
    }

    private void assertCanAccess(Creance creance) {
        if (!canAccess(creance)) {
            throw new AccessDeniedException("Cette créance n'appartient pas à votre portefeuille");
        }
    }

    /**
     * Taux de recouvrement = encaisse / (facture + penalites), en pourcentage a deux decimales ;
     * 0 sans creance. Meme base que le statut PAYEE (dette totale = principal + penalites).
     */
    public static BigDecimal tauxRecouvrement(BigDecimal facture, BigDecimal penalites, BigDecimal encaisse) {
        BigDecimal du = facture.add(penalites);
        if (du.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return encaisse.multiply(BigDecimal.valueOf(100)).divide(du, 2, java.math.RoundingMode.HALF_UP);
    }
}
