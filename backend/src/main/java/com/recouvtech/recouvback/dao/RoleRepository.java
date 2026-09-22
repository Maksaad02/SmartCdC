package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByNom(com.recouvtech.recouvback.entity.enums.RoleAgent nom);
}
