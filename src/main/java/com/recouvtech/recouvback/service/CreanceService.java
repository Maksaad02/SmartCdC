package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.CreanceRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceRequestDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.mapper.CreanceMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreanceService {

    private final CreanceRepository creanceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final PenaliteService penaliteService;
    private final RelanceAutomatiqueService relanceAutomatiqueService;
    private final CurrentUser currentUser;

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
            throw new RuntimeException("Une créance avec ce numéro de facture existe déjà : " + dto.getNumFacture());
        }

        Utilisateur agent = resolveOwner(dto.getAgentName());

        Client client = clientRepository.findByRaisonSociale(dto.getClientName());
        if (client == null) {
            throw new RuntimeException("Client not found with name: " + dto.getClientName());
        }

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
    public List<CreanceResponseDTO> getAllCreances() {
        List<Creance> creances = currentUser.isAdmin()
                ? creanceRepository.findAll()
                : creanceRepository.findByAgentRecouv_Email(currentUser.email());

        return creances.stream()
                .map(this::withPenalitesCalculees)
                .map(CreanceMapper::toDto)
                .collect(Collectors.toList());
    }

    public CreanceResponseDTO getByNumFacture(String numFacture) {
        Creance creance = creanceRepository.findByNumFacture(numFacture);
        if (creance == null) {
            throw new RuntimeException("Créance non trouvée pour la facture : " + numFacture);
        }
        assertCanAccess(creance);
        return CreanceMapper.toDto(withPenalitesCalculees(creance));
    }

    @Transactional
    public CreanceResponseDTO updateCreance(String numFacture, CreanceRequestDTO dto) {
        Creance creance = creanceRepository.findByNumFacture(numFacture);
        if (creance == null) {
            throw new RuntimeException("Créance non trouvée pour : " + numFacture);
        }
        assertCanAccess(creance);

        Utilisateur agent = dto.getAgentName() != null ? resolveOwner(dto.getAgentName()) : creance.getAgentRecouv();
        Client client = clientRepository.findByRaisonSociale(dto.getClientName());
        if (client == null) {
            throw new RuntimeException("Client not found with name: " + dto.getClientName());
        }

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
            throw new RuntimeException("Créance non trouvée pour : " + numFacture);
        }
        assertCanAccess(creance);
        creance.setSupprimee(true);
        creanceRepository.save(creance);
    }

    /** Recalcul persistant, reserve aux ADMIN (cf. CreanceController). */
    @Transactional
    public void recalculerToutesPenalites() {
        List<Creance> creances = creanceRepository.findAll();
        creances.forEach(creance -> {
            penaliteService.forcerRecalculPenalites(creance);
            creanceRepository.save(creance);
        });
    }

    /**
     * Creances d'un client donne (API chatbot).
     * Cloisonne : l'appelant ne voit que ce qui releve de son portefeuille.
     */
    public List<CreanceResponseDTO> getCreancesByClientId(Long clientId) {
        return creanceRepository.findByClientId(clientId).stream()
                .filter(this::canAccess)
                .map(this::withPenalitesCalculees)
                .map(CreanceMapper::toDto)
                .collect(Collectors.toList());
    }

    /** Impayes (API chatbot), cloisonnes au portefeuille de l'appelant. */
    public List<CreanceResponseDTO> getAllUnpaidCreances() {
        return creanceRepository.findAllUnpaid().stream()
                .filter(this::canAccess)
                .map(this::withPenalitesCalculees)
                .map(CreanceMapper::toDto)
                .collect(Collectors.toList());
    }

    private Creance withPenalitesCalculees(Creance creance) {
        penaliteService.mettreAJourPenalites(creance);
        return creance;
    }

    private boolean canAccess(Creance creance) {
        return currentUser.canAccess(
                creance.getAgentRecouv() != null ? creance.getAgentRecouv().getEmail() : null);
    }

    private void assertCanAccess(Creance creance) {
        if (!canAccess(creance)) {
            throw new AccessDeniedException("Cette créance n'appartient pas à votre portefeuille");
        }
    }

    private Utilisateur resolveOwner(String requestedAgentName) {
        if (currentUser.isAdmin() && requestedAgentName != null && !requestedAgentName.isBlank()) {
            Utilisateur agent = utilisateurRepository.findByNom(requestedAgentName);
            if (agent == null) {
                throw new RuntimeException("Agent not found with name: " + requestedAgentName);
            }
            return agent;
        }
        return utilisateurRepository.findByEmail(currentUser.email())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur courant introuvable"));
    }
}
