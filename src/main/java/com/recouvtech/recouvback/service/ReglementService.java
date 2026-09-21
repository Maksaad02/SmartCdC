package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dto.ReglementDTO.ReglementRequestDTO;
import com.recouvtech.recouvback.dto.ReglementDTO.ReglementResponseDTO;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Reglement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.ReglementRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.entity.enums.StatutReglement;
import com.recouvtech.recouvback.mapper.ReglementMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReglementService {

    private final ReglementRepository reglementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CreanceRepository creanceRepository;
    private final CurrentUser currentUser;

    @Transactional
    public ReglementResponseDTO create(ReglementRequestDTO dto) {
        Creance creance = creanceRepository.findByNumFacture(dto.getNumFacture());
        if (creance == null) {
            throw new RuntimeException("Créance not found with numFacture: " + dto.getNumFacture());
        }

        Utilisateur agent = utilisateurRepository.findByNom(dto.getAgentName());
        if (agent == null) {
            throw new RuntimeException("Agent not found with name: " + dto.getAgentName());
        }

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

    public List<ReglementResponseDTO> getAll() {
        // Cloisonne sur le portefeuille de l'appelant, via la creance rattachee.
        return reglementRepository.findAll().stream()
                .filter(this::canAccess)
                .map(ReglementMapper::toDto)
                .collect(Collectors.toList());
    }

    private boolean canAccess(Reglement r) {
        Creance c = r.getCreance();
        String ownerEmail = (c != null && c.getAgentRecouv() != null) ? c.getAgentRecouv().getEmail() : null;
        return currentUser.canAccess(ownerEmail);
    }

    private void assertCanAccess(Reglement r) {
        if (!canAccess(r)) {
            throw new AccessDeniedException("Ce règlement n'appartient pas à votre portefeuille");
        }
    }

    public ReglementResponseDTO getById(Long id) {
        Reglement r = reglementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reglement not found with id: " + id));
        assertCanAccess(r);
        return ReglementMapper.toDto(r);
    }

    @Transactional
    public ReglementResponseDTO update(Long id, ReglementRequestDTO dto) {
        Reglement r = reglementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reglement not found with id: " + id));
        assertCanAccess(r);

        boolean wasEffectue = r.getStatut() == StatutReglement.EFFECTUE;
        StatutReglement newStatut = dto.getStatut() != null ? dto.getStatut() : r.getStatut();
        boolean willBeEffectue = newStatut == StatutReglement.EFFECTUE;

        Creance creance = creanceRepository.findByNumFacture(dto.getNumFacture());
        if (creance == null) {
            throw new RuntimeException("Créance not found with numFacture: " + dto.getNumFacture());
        }
        
        Utilisateur agent = utilisateurRepository.findByNom(dto.getAgentName());
        if (agent == null) {
            throw new RuntimeException("Agent not found with name: " + dto.getAgentName());
        }

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
                .orElseThrow(() -> new RuntimeException("Reglement not found with id: " + id));
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
                .orElseThrow(() -> new RuntimeException("Reglement not found with id: " + id));
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