package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.DepartementRepository;
import com.recouvtech.recouvback.dto.DepartementDTO.DepartementRequestDTO;
import com.recouvtech.recouvback.dto.DepartementDTO.DepartementResponseDTO;
import com.recouvtech.recouvback.entity.Departement;
import com.recouvtech.recouvback.exception.RessourceIntrouvableException;
import com.recouvtech.recouvback.mapper.DepartementMapper;
import com.recouvtech.recouvback.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des departements. La creation, la modification et la suppression sont reservees aux ADMIN
 * (controle a l'entree du controleur) ; la lecture est ouverte a tout utilisateur authentifie, limitee
 * a son propre departement pour un MANAGER ou un AGENT (listes deroulantes, affichage).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartementService {

    private final DepartementRepository departementRepository;
    private final CurrentUser currentUser;

    public List<DepartementResponseDTO> list() {
        if (currentUser.isAdmin()) {
            return departementRepository.findAll().stream()
                    .sorted((a, b) -> a.getNom().compareToIgnoreCase(b.getNom()))
                    .map(DepartementMapper::toDto)
                    .toList();
        }
        Long mien = currentUser.departementId();
        if (mien == null) {
            return List.of();
        }
        return departementRepository.findById(mien).map(DepartementMapper::toDto).stream().toList();
    }

    @Transactional
    public DepartementResponseDTO create(DepartementRequestDTO dto) {
        String nom = dto.getNom().trim();
        String code = dto.getCode().trim();
        if (departementRepository.existsByNom(nom)) {
            throw new IllegalArgumentException("Un département porte déjà ce nom : " + nom);
        }
        if (departementRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Un département utilise déjà ce code : " + code);
        }
        Departement departement = new Departement(nom, code);
        departement.setActif(dto.getActif() == null || dto.getActif());
        return DepartementMapper.toDto(departementRepository.save(departement));
    }

    @Transactional
    public DepartementResponseDTO update(Long id, DepartementRequestDTO dto) {
        Departement departement = charger(id);
        String nom = dto.getNom().trim();
        String code = dto.getCode().trim();
        if (!nom.equals(departement.getNom()) && departementRepository.existsByNom(nom)) {
            throw new IllegalArgumentException("Un département porte déjà ce nom : " + nom);
        }
        if (!code.equals(departement.getCode()) && departementRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Un département utilise déjà ce code : " + code);
        }
        departement.setNom(nom);
        departement.setCode(code);
        if (dto.getActif() != null) {
            departement.setActif(dto.getActif());
        }
        return DepartementMapper.toDto(departementRepository.save(departement));
    }

    /**
     * Suppression possible seulement pour un departement vide : les cles etrangeres refusent (409)
     * de supprimer un departement qui porte des utilisateurs, clients ou creances. Pour retirer un
     * departement utilise, on le desactive.
     */
    @Transactional
    public void delete(Long id) {
        departementRepository.delete(charger(id));
        departementRepository.flush();
    }

    private Departement charger(Long id) {
        return departementRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Département introuvable"));
    }
}
