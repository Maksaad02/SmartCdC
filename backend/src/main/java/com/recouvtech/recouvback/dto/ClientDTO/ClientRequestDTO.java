package com.recouvtech.recouvback.dto.ClientDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientRequestDTO {

    @NotBlank(message = "La raison sociale est obligatoire")
    @Size(max = 255)
    private String raisonSociale;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Le téléphone est obligatoire")
    @Size(max = 50)
    private String telephone;

    // rc, ice et identiteFiscale sont facultatifs. Vide ou blanc = absent (null) : une chaine vide
    // enregistree violait l'unicite (ice) des qu'un second client n'avait pas d'ICE.
    @Size(max = 255)
    private String rc;

    @NotBlank(message = "L'adresse est obligatoire")
    @Size(max = 255)
    private String adresse;

    @Size(max = 255)
    private String ice;

    @Size(max = 255)
    private String identiteFiscale;

    /** Facultatif : un ADMIN ou un MANAGER peut affecter le client a un autre agent de son departement. */
    @Size(max = 255)
    private String agentName;

    /**
     * Departement du client : obligatoire pour un ADMIN a la creation ; ignore (et refuse s'il differe)
     * pour un MANAGER ou un AGENT, dont le client est toujours dans leur departement. Non modifiable
     * apres creation : les creances, reglements et relances du client portent le meme departement.
     */
    private Long departementId;

    public String getRc() {
        return blankToNull(rc);
    }

    public String getIce() {
        return blankToNull(ice);
    }

    public String getIdentiteFiscale() {
        return blankToNull(identiteFiscale);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
