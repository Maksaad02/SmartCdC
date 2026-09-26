package com.recouvtech.recouvback.configuration;

import com.recouvtech.recouvback.security.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Remplace le gestionnaire de transactions de Spring Boot par celui qui active le filtre de
 * departement. Boot s'efface des qu'un gestionnaire de transactions est declare.
 */
@Configuration
public class JpaTransactionConfig {

    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
                                                    CurrentUser currentUser,
                                                    ObjectProvider<TransactionManagerCustomizers> customizers) {
        DepartementFilterTransactionManager manager =
                new DepartementFilterTransactionManager(entityManagerFactory, currentUser);
        customizers.ifAvailable(c -> c.customize(manager));
        return manager;
    }
}
