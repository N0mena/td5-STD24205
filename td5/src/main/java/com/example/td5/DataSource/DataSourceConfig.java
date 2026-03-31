package com.example.td5.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.management.PersistentMBean;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {


    private final Dotenv dotenv = Dotenv.load();

    @Bean
    public DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(dotenv.get("DB_URL"));
        ds.setUser(dotenv.get("DB_USER"));
        ds.setPassword(dotenv.get("DB_PASSWORD"));
        return ds;
    }
}
