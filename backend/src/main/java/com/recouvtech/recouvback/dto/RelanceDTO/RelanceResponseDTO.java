package com.recouvtech.recouvback.dto.RelanceDTO;


import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RelanceResponseDTO {
    private Long id;
    private String numFacture;
    private String agentName;
    private LocalDate dateRelance;
    private TypeRelance typeRelance;
    private StatutRelance statutRelance;
    private String commentaire;
}
