package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.entity.Creance;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public Role postRole(Role role) {
        return roleRepository.save(role);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }


    public Role updateRole(Long id, Role roleUpdates) {
        Role existingRole = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));

        if (roleUpdates.getNom() != null) {
            existingRole.setNom(roleUpdates.getNom());
        }

        return roleRepository.save(existingRole);
    }


    public void deleteRole(long id) {
        if(!roleRepository.existsById(id)) {
            throw new EntityNotFoundException("Role not found");
        }
        roleRepository.deleteById(id);
    }



}
