package org.maksaad.recouvchatbot_rag.config;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties postgresProps(){
        return new DataSourceProperties();
    }

    @Bean(name = "postgresDataSource")
    @Primary
    public DataSource postgresDataSource(){
        return postgresProps().initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ====== Secondary DataSource (Mysql) ======

    @Bean
    @ConfigurationProperties("app.datasource.mysql")
    public DataSourceProperties mysqlProps(){
        return new DataSourceProperties();
    }

    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(){
        return mysqlProps().initializeDataSourceBuilder().build();
    }

    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource")  DataSource dataSource){
        return new JdbcTemplate(dataSource);
    }

}
