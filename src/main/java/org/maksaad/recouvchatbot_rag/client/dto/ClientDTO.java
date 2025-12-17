package org.maksaad.recouvchatbot_rag.client.dto;

import lombok.Data;

/**
 * DTO for Client information (mirror of Backend's ClientResponseDTO)
 */
@Data
public class ClientDTO {
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
