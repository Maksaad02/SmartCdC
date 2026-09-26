package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleInitializer {

    private final RoleRepository roleRepository;

    @PostConstruct
    public void initRoles() {
        // Idempotent par role : l'ajout d'un role (MANAGER) doit aussi s'appliquer a
        // une base existante, ou count() != 0.
        for (RoleAgent nom : RoleAgent.values()) {
            if (roleRepository.findByNom(nom).isEmpty()) {
                Role role = new Role();
                role.setNom(nom);
                roleRepository.save(role);
            }
        }
    }
}
