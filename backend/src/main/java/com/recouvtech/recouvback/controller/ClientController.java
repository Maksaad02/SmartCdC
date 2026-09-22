package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.dto.ClientDTO.ClientRequestDTO;
import com.recouvtech.recouvback.dto.ClientDTO.ClientResponseDTO;
import com.recouvtech.recouvback.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    public ClientResponseDTO createClient(@RequestBody ClientRequestDTO dto) {
        return clientService.createClient(dto);
    }

    @GetMapping
    public List<ClientResponseDTO> getAllClients() {
        return clientService.getAllClients();
    }

    @GetMapping("/{id}")
    public ClientResponseDTO getClientById(@PathVariable Long id) {
        return clientService.getClientById(id);
    }

    @PutMapping("/{id}")
    public ClientResponseDTO updateClient(@PathVariable Long id, @RequestBody ClientRequestDTO dto) {
        return clientService.updateClient(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
    }
}
