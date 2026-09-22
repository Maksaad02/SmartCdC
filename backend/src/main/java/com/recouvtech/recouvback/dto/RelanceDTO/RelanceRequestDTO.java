package com.recouvtech.recouvback.dto.RelanceDTO;


import com.recouvtech.recouvback.entity.enums.StatutRelance;
import com.recouvtech.recouvback.entity.enums.TypeRelance;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RelanceRequestDTO {
    private String numFacture;
    private String agentName;
    private LocalDate dateRelance;
    private TypeRelance typeRelance;
    private StatutRelance statutRelance;
    private String commentaire;
    private String message;
}
