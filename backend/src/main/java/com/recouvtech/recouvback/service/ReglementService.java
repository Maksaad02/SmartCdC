package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.ReglementRepository;
import com.recouvtech.recouvback.dao.spec.ReglementSpecs;
import com.recouvtech.recouvback.web.PageRequests;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import com.recouvtech.recouvback.mapper.ReglementMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

/** Lecture seule par defaut (associations paresseuses) ; les ecritures sont @Transactional. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReglementService {

    private static final Set<String> SORTABLE = Set.of("id", "dateReglement", "montant", "statut");

    private final ReglementRepository reglementRepository;
    private final AgentResolver agentResolver;
    private final CreanceRepository creanceRepository;
    private final CurrentUser currentUser;

    @Transactional
    public ReglementResponseDTO create(ReglementRequestDTO dto) {
        Creance creance = creanceRepository.findByNumFacture(dto.getNumFacture());
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance not found with numFacture: " + dto.getNumFacture());
        }
        // Sans ce controle, tout agent pouvait enregistrer un paiement sur la
        // creance d'un autre agent, ce qui modifie son montant encaisse et peut
        // la faire passer a PAYEE.
        assertCanAccessCreance(creance);

        Utilisateur agent = agentResolver.resolve(dto.getAgentName(), creance.getDepartement().getId());

        Reglement r = ReglementMapper.fromRequestDto(dto, creance, agent);
        r.setStatut(dto.getStatut() != null ? dto.getStatut() : StatutReglement.NON_EFFECTUE);

        // Sauvegarde du règlement
        Reglement saved = reglementRepository.save(r);

        // Mise à jour du montant encaissé seulement si le statut est EFFECTUE
        if (r.getStatut() == StatutReglement.EFFECTUE) {
            updateCreanceMontantEncaisse(creance);
        }

        return ReglementMapper.toDto(saved);
    }

    /**
     * Liste paginee. Departement : filtre Hibernate (MANAGER, AGENT). Portefeuille personnel d'un AGENT
     * (via la creance rattachee) : Specification, en SQL, au lieu de filtrer en Java.
     */
    public Page<ReglementResponseDTO> list(String q, StatutReglement statut, String numFacture, Pageable pageable) {
        Specification<Reglement> spec = Specification.where(null);
        if (currentUser.isAgent()) {
            spec = spec.and(ReglementSpecs.ownedBy(currentUser.email()));
        }
        if (statut != null) {
            spec = spec.and(ReglementSpecs.hasStatut(statut));
        }
        if (numFacture != null && !numFacture.isBlank()) {
            spec = spec.and(ReglementSpecs.forFacture(numFacture.trim()));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(ReglementSpecs.matches(q.trim()));
        }
        return reglementRepository
                .findAll(spec, PageRequests.sanitize(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "dateReglement", "id")))
                .map(ReglementMapper::toDto);
    }

    private boolean canAccess(Reglement r) {
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

    private void assertCanAccess(Reglement r) {
        if (!canAccess(r)) {
            throw new AccessDeniedException("Ce règlement n'appartient pas à votre périmètre");
        }
    }

    public ReglementResponseDTO getById(Long id) {
        Reglement r = reglementRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Reglement not found with id: " + id));
        assertCanAccess(r);
        return ReglementMapper.toDto(r);
    }

    @Transactional
    public ReglementResponseDTO update(Long id, ReglementRequestDTO dto) {
        Reglement r = reglementRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Reglement not found with id: " + id));
        assertCanAccess(r);

        boolean wasEffectue = r.getStatut() == StatutReglement.EFFECTUE;
        StatutReglement newStatut = dto.getStatut() != null ? dto.getStatut() : r.getStatut();
        boolean willBeEffectue = newStatut == StatutReglement.EFFECTUE;

        Creance creance = creanceRepository.findByNumFacture(dto.getNumFacture());
        if (creance == null) {
            throw new RessourceIntrouvableException("Créance not found with numFacture: " + dto.getNumFacture());
        }
        // Le reglement existant est deja controle plus haut ; la NOUVELLE creance
        // doit l'etre aussi, sinon on peut deplacer un paiement vers le
        // portefeuille d'un autre agent en changeant simplement numFacture.
        assertCanAccessCreance(creance);
        // Un reglement reste dans le departement de sa creance (cle etrangere composite en base) :
        // deplacer un paiement vers la creance d'un AUTRE departement est refuse, clairement.
        if (!creance.getDepartement().getId().equals(r.getDepartement().getId())) {
            throw new IllegalArgumentException("Le règlement ne peut pas être déplacé vers une créance d'un autre département");
        }

        Utilisateur agent = agentResolver.resolve(dto.getAgentName(), creance.getDepartement().getId());

        // Creance d'origine capturee AVANT le remapping : si le reglement change
        // de facture, l'ancienne doit etre recalculee elle aussi, sinon le montant
        // reste compte des deux cotes.
        Creance ancienneCreance = r.getCreance();

        ReglementMapper.updateFromRequestDto(r, dto, creance, agent);
        r.setStatut(newStatut);

        Reglement saved = reglementRepository.save(r);

        boolean changementDeCreance = ancienneCreance != null
                && !ancienneCreance.getId().equals(creance.getId());

        if (wasEffectue != willBeEffectue || changementDeCreance) {
            updateCreanceMontantEncaisse(creance);
            if (changementDeCreance) {
                updateCreanceMontantEncaisse(ancienneCreance);
            }
        }

        return ReglementMapper.toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Reglement reglement = reglementRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Reglement not found with id: " + id));
        assertCanAccess(reglement);
        Creance creance = reglement.getCreance();
        
        reglementRepository.deleteById(id);
        
        // Recalculate montantEncaisse only if the deleted payment was EFFECTUE
        if (reglement.getStatut() == StatutReglement.EFFECTUE) {
            updateCreanceMontantEncaisse(creance);
        }
    }

    @Transactional
    public ReglementResponseDTO updateStatus(Long id, StatutReglement newStatus) {
        Reglement reglement = reglementRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Reglement not found with id: " + id));
        assertCanAccess(reglement);

        // Le statut precedent doit etre lu AVANT la mutation : le test d'origine
        // comparait reglement.getStatut() apres setStatut(), donc toujours au
        // nouveau statut. Repasser EFFECTUE -> NON_EFFECTUE ne declenchait alors
        // aucun recalcul et un paiement annule restait compte comme encaisse.
        StatutReglement ancienStatut = reglement.getStatut();
        reglement.setStatut(newStatus);

        Reglement saved = reglementRepository.save(reglement);

        boolean bascule = ancienStatut != newStatus
                && (ancienStatut == StatutReglement.EFFECTUE || newStatus == StatutReglement.EFFECTUE);
        if (bascule) {
            updateCreanceMontantEncaisse(reglement.getCreance());
        }
        
        return ReglementMapper.toDto(saved);
    }

    private void updateCreanceMontantEncaisse(Creance creance) {
        if (creance == null) {
            return;
        }
        BigDecimal totalEncaisse = reglementRepository.findByCreance(creance).stream()
                .filter(r -> r.getStatut() == StatutReglement.EFFECTUE)
                .map(Reglement::getMontant)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        creance.setMontantEncaisse(totalEncaisse);
        creanceRepository.save(creance);
    }
}