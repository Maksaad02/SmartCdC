package com.recouvtech.recouvback.dto.ClientDTO;

import lombok.Data;

@Data
public class ClientRequestDTO {
    private String raisonSociale;
    private String email;
    private String telephone;
    private String rc;
    private String adresse;
    private String ice;
    private String identiteFiscale;
    private String agentName;
}