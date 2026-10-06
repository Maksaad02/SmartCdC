package com.recouvtech.recouvback.dao;

import com.recouvtech.recouvback.dto.CreanceDTO.CreanceDocumentInfoDTO;
import com.recouvtech.recouvback.entity.CreanceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Requetes JPQL uniquement : le filtre Hibernate de departement s'y applique (pas a em.find). */
@Transactional(readOnly = true)
public interface CreanceDocumentRepository extends JpaRepository<CreanceDocument, Long> {

    @Query("SELECT d FROM CreanceDocument d WHERE d.creance.id = :creanceId")
    Optional<CreanceDocument> findByCreanceId(@Param("creanceId") Long creanceId);

    /** Metadonnees sans charger le contenu (jusqu'a 10 Mo). */
    @Query("SELECT new com.recouvtech.recouvback.dto.CreanceDTO.CreanceDocumentInfoDTO(d.nomFichier, d.taille, d.dateAjout) "
            + "FROM CreanceDocument d WHERE d.creance.id = :creanceId")
    Optional<CreanceDocumentInfoDTO> findInfoByCreanceId(@Param("creanceId") Long creanceId);
}
