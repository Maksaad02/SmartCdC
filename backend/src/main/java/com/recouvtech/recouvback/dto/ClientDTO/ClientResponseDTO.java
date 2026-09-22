package com.recouvtech.recouvback.dto.ClientDTO;

import lombok.Data;

@Data
public class ClientResponseDTO {
    private Long id;
    private String raisonSociale;
    private String email;
    private String telephone;
    private String rc;
    private String adresse;
    private String ice;
    private String identiteFiscale;
    private String agentName;
}
