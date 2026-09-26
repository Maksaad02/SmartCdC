package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.spec.ClientSpecs;
import com.recouvtech.recouvback.web.PageRequests;
import com.recouvtech.recouvback.dto.ClientDTO.ClientRequestDTO;
import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.mapper.ClientMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;

import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Lecture seule par defaut : les associations (agent) sont chargees paresseusement
 * et le mapping en DTO doit se faire dans la transaction. Les ecritures la
 * redeclarent explicitement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private static final Set<String> SORTABLE = Set.of("id", "raisonSociale", "email", "telephone");
    /** Nombre maximal de resultats renvoyes a l'API chatbot (bornes le contexte envoye au LLM). */
    private static final int SEARCH_LIMIT = 20;

    private final ClientRepository clientRepository;
    private final AgentResolver agentResolver;
    private final DepartementScope departementScope;
    private final CurrentUser currentUser;

    @Transactional
    public ClientResponseDTO createClient(ClientRequestDTO dto) {
        // Le departement est celui de l'appelant (choisi par un ADMIN) ; le responsable de dossier est
        // l'appelant, ou un agent DE CE DEPARTEMENT designe par un ADMIN ou un MANAGER. Un AGENT ne peut
        // creer que pour lui-meme (sinon il pouvait attribuer a autrui via dto.agentName).
        Departement departement = departementScope.pourCreation(dto.getDepartementId());
        Utilisateur agent = agentResolver.resolve(dto.getAgentName(), departement.getId());
        Client client = ClientMapper.fromRequestDto(dto, agent, departement);
        Client saved = clientRepository.save(client);
        return ClientMapper.toDto(saved);
    }

    /**
     * Liste paginee. Le filtrage par portefeuille et la recherche sont appliques
     * en SQL : auparavant toute la table etait chargee puis filtree en memoire.
     */
    public Page<ClientResponseDTO> list(String q, Pageable pageable) {
        return clientRepository
                .findAll(scope(q), PageRequests.sanitize(pageable, SORTABLE, Sort.by("raisonSociale")))
                .map(ClientMapper::toDto);
    }

    /**
     * Le cloisonnement par departement est applique par le filtre Hibernate (MANAGER, AGENT) ; ici
     * ne reste que le portefeuille personnel d'un AGENT, et la recherche.
     */
    private Specification<Client> scope(String q) {
        Specification<Client> spec = Specification.where(null);
        if (currentUser.isAgent()) {
            spec = spec.and(ClientSpecs.ownedBy(currentUser.email()));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(ClientSpecs.matches(q.trim()));
        }
        return spec;
    }

    public ClientResponseDTO getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        assertCanAccess(client);
        return ClientMapper.toDto(client);
    }

    @Transactional
    public ClientResponseDTO updateClient(Long id, ClientRequestDTO dto) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        assertCanAccess(client);
        // Le departement d'un client ne change pas : ses creances, reglements et relances le portent aussi
        // (cles etrangeres composites). Le deplacer serait refuse en base ; on le refuse ici, clairement.
        if (dto.getDepartementId() != null && !dto.getDepartementId().equals(client.getDepartement().getId())) {
            throw new IllegalArgumentException("Le département d'un client ne peut pas être modifié");
        }
        Utilisateur agent = null;
        if (dto.getAgentName() != null) {
            agent = agentResolver.resolve(dto.getAgentName(), client.getDepartement().getId());
        }
        ClientMapper.updateFromRequestDto(client, dto, agent);
        return ClientMapper.toDto(clientRepository.save(client));
    }

    @Transactional
    public void deleteClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        assertCanAccess(client);
        clientRepository.delete(client);
    }

    private void assertCanAccess(Client client) {
        String ownerEmail = client.getAgentRecouv() != null ? client.getAgentRecouv().getEmail() : null;
        if (!currentUser.canAccess(client.getDepartement().getId(), ownerEmail)) {
            throw new AccessDeniedException("Ce client n'appartient pas a votre perimetre");
        }
    }

    /**
     * Search clients by keyword (for External Chatbot API)
     * 
     * @param query Search term (raison sociale, ICE, or telephone)
     * @return List of matching clients
     */
    public List<ClientResponseDTO> searchClients(String query) {
        // Cloisonne comme le reste : la recherche du chatbot ne doit pas exposer
        // le portefeuille des autres agents.
        // Recherche, portefeuille et limite appliques en SQL : la limite porte sur
        // les clients visibles de l'appelant, pas sur des lignes ensuite ecartees.
        return clientRepository
                .findAll(scope(query), PageRequest.of(0, SEARCH_LIMIT, Sort.by("raisonSociale")))
                .map(ClientMapper::toDto)
                .getContent();
    }
}
