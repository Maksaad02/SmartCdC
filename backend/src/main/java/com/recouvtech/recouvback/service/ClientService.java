package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.ClientRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.ClientDTO.ClientRequestDTO;
import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.entity.Client;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.mapper.ClientMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;

import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUser currentUser;

    public ClientResponseDTO createClient(ClientRequestDTO dto) {
        // Le proprietaire est l'appelant. Un ADMIN peut affecter le client a un
        // autre agent ; un AGENT ne peut creer que pour lui-meme (sinon n'importe
        // qui pouvait s'attribuer ou attribuer a autrui via dto.agentName).
        Utilisateur agent = resolveOwner(dto.getAgentName());
        Client client = ClientMapper.fromRequestDto(dto, agent);
        Client saved = clientRepository.save(client);
        return ClientMapper.toDto(saved);
    }

    public List<ClientResponseDTO> getAllClients() {
        List<Client> clients = currentUser.isAdmin()
                ? clientRepository.findAll()
                : clientRepository.findByAgentRecouv_Email(currentUser.email());
        return clients.stream()
                .map(ClientMapper::toDto)
                .collect(Collectors.toList());
    }

    public ClientResponseDTO getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        assertCanAccess(client);
        return ClientMapper.toDto(client);
    }

    public ClientResponseDTO updateClient(Long id, ClientRequestDTO dto) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        assertCanAccess(client);
        Utilisateur agent = null;
        if (dto.getAgentName() != null) {
            agent = resolveOwner(dto.getAgentName());
        }
        ClientMapper.updateFromRequestDto(client, dto, agent);
        return ClientMapper.toDto(clientRepository.save(client));
    }

    public void deleteClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable"));
        assertCanAccess(client);
        clientRepository.delete(client);
    }

    private void assertCanAccess(Client client) {
        String ownerEmail = client.getAgentRecouv() != null ? client.getAgentRecouv().getEmail() : null;
        if (!currentUser.canAccess(ownerEmail)) {
            throw new AccessDeniedException("Ce client n'appartient pas a votre portefeuille");
        }
    }

    /**
     * Resout l'agent proprietaire : un ADMIN peut designer un agent par son nom,
     * un AGENT est toujours force a lui-meme.
     */
    private Utilisateur resolveOwner(String requestedAgentName) {
        if (currentUser.isAdmin() && requestedAgentName != null && !requestedAgentName.isBlank()) {
            // Resolution limitee a l'organisation de l'appelant : sinon un admin
            // pouvait designer par son nom un agent d'une autre organisation.
            Utilisateur agent = utilisateurRepository.findByNomAndOrganisation_Id(
                    requestedAgentName, currentUser.organisationId());
            if (agent == null) {
                throw new IllegalArgumentException("Aucun agent trouvé avec ce nom : " + requestedAgentName);
            }
            return agent;
        }
        return utilisateurRepository.findByEmail(currentUser.email())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur courant introuvable"));
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
        return clientRepository.searchByKeyword(query).stream()
                .filter(c -> currentUser.canAccess(
                        c.getAgentRecouv() != null ? c.getAgentRecouv().getEmail() : null))
                .map(ClientMapper::toDto)
                .collect(Collectors.toList());
    }
}
