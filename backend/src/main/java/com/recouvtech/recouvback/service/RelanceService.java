package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.RelanceRepository;
import com.recouvtech.recouvback.dao.spec.RelanceSpecs;
import com.recouvtech.recouvback.dto.RelanceDTO.RelanceRequestDTO;
import com.recouvtech.recouvback.dto.RelanceDTO.RelanceResponseDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Relance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.mapper.RelanceMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import com.recouvtech.recouvback.web.PageRequests;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Lecture seule par defaut (associations paresseuses) ; les ecritures sont @Transactional. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RelanceService {

    private static final Set<String> SORTABLE = Set.of("id", "dateRelance", "statutRelance", "typeRelance");
    /** Bornes des listes non paginees "en attente" (file de travail d'un agent). */
    private static final int FILE_ATTENTE_MAX = 200;

    private final RelanceRepository relanceRepository;
    private final CreanceRepository creanceRepository;
    private final AgentResolver agentResolver;
    private final RelanceEnvoiService envoiService;
    private final EmailService emailService;
    private final CurrentUser currentUser;

    @Transactional
    public RelanceResponseDTO create(RelanceRequestDTO dto) {
        Creance creance = creanceRepository.findByNumFacture(dto.getNumFacture());
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance not found with numFacture: " + dto.getNumFacture());
        }
        assertCanAccessCreance(creance);
        Utilisateur agent = agentResolver.resolve(dto.getAgentName(), creance.getDepartement().getId());
        Relance r = RelanceMapper.fromRequestDto(dto, creance, agent);
        return RelanceMapper.toDto(relanceRepository.save(r));
    }

    /**
     * Liste paginee. Departement : filtre Hibernate (MANAGER, AGENT). Portefeuille personnel d'un AGENT
     * (via la creance rattachee) : Specification, en SQL.
     */
    public Page<RelanceResponseDTO> list(String q, StatutRelance statut, String numFacture,
                                         LocalDate date, Pageable pageable) {
        return relanceRepository
                .findAll(filtre(q, statut, numFacture, date),
                        PageRequests.sanitize(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "dateRelance", "id")))
                .map(RelanceMapper::toDto);
    }

    private Specification<Relance> filtre(String q, StatutRelance statut, String numFacture, LocalDate date) {
        Specification<Relance> spec = Specification.where(null);
        if (currentUser.isAgent()) {
            spec = spec.and(RelanceSpecs.ownedBy(currentUser.email()));
        }
        if (statut != null) {
            spec = spec.and(RelanceSpecs.hasStatut(statut));
        }
        if (numFacture != null && !numFacture.isBlank()) {
            spec = spec.and(RelanceSpecs.forFacture(numFacture.trim()));
        }
        if (date != null) {
            spec = spec.and(RelanceSpecs.onDate(date));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(RelanceSpecs.matches(q.trim()));
        }
        return spec;
    }

    public RelanceResponseDTO getById(Long id) {
        Relance r = relanceRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Not found"));
        assertCanAccess(r);
        return RelanceMapper.toDto(r);
    }

    private boolean canAccess(Relance r) {
        Creance c = r.getCreance();
        String ownerEmail = (c != null && c.getAgentRecouv() != null) ? c.getAgentRecouv().getEmail() : null;
        return currentUser.canAccess(r.getDepartement().getId(), ownerEmail);
    }

    private void assertCanAccessCreance(Creance c) {
        String ownerEmail = (c.getAgentRecouv() != null) ? c.getAgentRecouv().getEmail() : null;
        if (!currentUser.canAccess(c.getDepartement().getId(), ownerEmail)) {
            throw new AccessDeniedException("Cette créance n'appartient pas à votre périmètre");
        }
    }

    private void assertCanAccess(Relance r) {
        if (!canAccess(r)) {
            throw new AccessDeniedException("Cette relance n'appartient pas à votre périmètre");
        }
    }

    @Transactional
    public RelanceResponseDTO update(Long id, RelanceRequestDTO dto) {
        Relance r = relanceRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Not found"));
        assertCanAccess(r);
        Creance creance = creanceRepository.findByNumFacture(dto.getNumFacture());
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance not found with numFacture: " + dto.getNumFacture());
        }
        // La nouvelle creance doit relever du portefeuille de l'appelant, comme
        // la relance d'origine (controlee ci-dessus).
        assertCanAccessCreance(creance);
        if (!creance.getDepartement().getId().equals(r.getDepartement().getId())) {
            throw new IllegalArgumentException("La relance ne peut pas être déplacée vers une créance d'un autre département");
        }
        Utilisateur agent = agentResolver.resolve(dto.getAgentName(), creance.getDepartement().getId());
        RelanceMapper.updateFromRequestDto(r, dto, creance, agent);
        return RelanceMapper.toDto(relanceRepository.save(r));
    }

    @Transactional
    public void delete(Long id) {
        Relance r = relanceRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Not found"));
        assertCanAccess(r);
        relanceRepository.delete(r);
    }

    /**
     * Envoi manuel par un agent.
     *
     * Volontairement HORS transaction : l'aller-retour SMTP ne doit pas garder de
     * connexion MySQL, et un e-mail parti ne se reprend pas par un rollback. Les
     * etapes en base (preparer, noter le resultat) sont des transactions courtes
     * portees par RelanceEnvoiService.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean envoyerRelanceManuellement(Long relanceId, String agentName) {
        var preparation = envoiService.preparerEnvoiManuel(relanceId);
        if (preparation.refus() != null) {
            throw new IllegalArgumentException(preparation.refus());
        }
        var courriel = preparation.courriel();

        try {
            emailService.envoyer(courriel.destinataire(), courriel.sujet(), courriel.corps());
        } catch (Exception e) {
            log.error("Échec de l'envoi manuel de la relance {}", relanceId, e);
            envoiService.marquerEchec(relanceId, "Échec de l'envoi manuel : " + e.getMessage());
            return false;
        }

        envoiService.marquerEnvoyee(relanceId, StatutRelance.EFFECTUEE, agentName, "Envoi manuel par " + agentName);
        log.info("Relance {} envoyée manuellement", relanceId);
        return true;
    }

    /** File de travail : relances en attente, bornee (200 max). */
    public List<RelanceResponseDTO> getRelancesEnAttente() {
        return list(null, StatutRelance.EN_ATTENTE, null, null, PageRequest.of(0, FILE_ATTENTE_MAX)).getContent();
    }

    public List<RelanceResponseDTO> getRelancesEnAttenteByCreance(String numFacture) {
        return list(null, StatutRelance.EN_ATTENTE, numFacture, null, PageRequest.of(0, FILE_ATTENTE_MAX)).getContent();
    }

    /**
     * Annuler les relances planifiées si la créance est payée
     */
    @Transactional
    public void annulerRelancesPlanifiees(String numFacture) {
        List<Relance> relancesPlanifiees = relanceRepository
            .findByCreanceNumFactureAndStatutRelance(numFacture, StatutRelance.EN_ATTENTE);

        for (Relance relance : relancesPlanifiees) {
            relance.setStatutRelance(StatutRelance.ANNULEE);
            relance.setCommentaire("Créance payée - relance annulée");
            relanceRepository.save(relance);
        }
    }

    /**
     * Sauvegarder une relance
     */
    @Transactional
    public Relance save(Relance relance) {
        return relanceRepository.save(relance);
    }
}
