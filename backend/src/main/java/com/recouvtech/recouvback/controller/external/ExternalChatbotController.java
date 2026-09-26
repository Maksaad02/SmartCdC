package com.recouvtech.recouvback.controller.external;

import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.dto.CreanceDTO.CreanceResponseDTO;
import com.recouvtech.recouvback.service.ClientService;
import com.recouvtech.recouvback.service.CreanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * External API Controller for Chatbot
 * Provides secure REST endpoints for the AI Chatbot to query client and debt
 * data
 * 
 * Authentication: Requires X-RECOUV-KEY header
 * Base URL: /api/external/chatbot
 */
@RestController
@RequestMapping("/api/external/chatbot")
@RequiredArgsConstructor
public class ExternalChatbotController {

    private final ClientService clientService;
    private final CreanceService creanceService;

    /**
     * Search clients by keyword (raison sociale, ICE, or telephone)
     * Critical endpoint: AI doesn't know client IDs, must search first
     * 
     * @param query Search term (company name, ICE number, or phone)
     * @return List of matching clients (empty list if no match)
     */
    @GetMapping("/clients/search")
    public ResponseEntity<List<ClientResponseDTO>> searchClients(
            @RequestParam String query) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<ClientResponseDTO> results = clientService.searchClients(query);
        return ResponseEntity.ok(results);
    }

    /**
     * Get client details by ID
     * 
     * @param clientId Client ID
     * @return Client details or 404 if not found
     */
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ClientResponseDTO> getClientById(
            @PathVariable Long clientId) {

        // Pas de try/catch generique : il transformait un refus d'acces (403) et une
        // panne (500) en "introuvable" (404), et le chatbot annoncait alors a l'agent
        // que le client n'existait pas. GlobalExceptionHandler renvoie 404, 403 ou 500.
        return ResponseEntity.ok(clientService.getClientById(clientId));
    }

    /**
     * Get all debts for a specific client
     * 
     * @param clientId Client ID
     * @return List of debts (invoices) with calculated penalties and late days
     */
    @GetMapping("/clients/{clientId}/creances")
    public ResponseEntity<List<CreanceResponseDTO>> getClientCreances(
            @PathVariable Long clientId) {

        // Verifie d'abord que le client existe et releve du portefeuille de l'appelant.
        clientService.getClientById(clientId);
        return ResponseEntity.ok(creanceService.getCreancesByClientId(clientId));
    }

    /**
     * Get all unpaid debts (all clients)
     * Optional: Filter by status
     * 
     * @return List of all unpaid debts
     */
    @GetMapping("/creances/impayees")
    public ResponseEntity<List<CreanceResponseDTO>> getUnpaidCreances(
            @RequestParam(required = false) Integer limit) {

        // Borne (50 par defaut, 200 max) : la reponse est injectee dans le contexte
        // du LLM ; les plus gros montants sont renvoyes en premier.
        List<CreanceResponseDTO> unpaidCreances = creanceService.getAllUnpaidCreances(limit);
        return ResponseEntity.ok(unpaidCreances);
    }
}
