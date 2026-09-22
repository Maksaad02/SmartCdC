package com.recouvtech.recouvback.configuration;

import com.recouvtech.recouvback.security.TenantContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fournit a Hibernate l'organisation courante.
 *
 * Couple a @TenantId sur les entites metier, Hibernate ajoute lui-meme le
 * filtre a chaque SELECT et renseigne la colonne a chaque INSERT. C'est
 * volontairement automatique : un cloisonnement ecrit a la main dans chaque
 * service repose sur le fait que toute requete future y pense, ce qui est le
 * mauvais defaut pour des donnees inter-clients.
 */
@Component
public class TenantResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return TenantContext.get();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /**
     * false : le resolveur peut renvoyer AUCUNE_ORGANISATION hors requete
     * authentifiee (demarrage, tache planifiee), ce qui n'est pas une erreur.
     */
    @Override
    public boolean isRoot(Long tenantId) {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
