package com.mst.matt.tradingplatformapp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;

/**
 * SQLite-specific DataSource with WAL mode. PostgreSQL uses Spring Boot auto-config
 * from {@code application-docker.properties} when {@code spring.datasource.driver-class-name}
 * is {@code org.postgresql.Driver}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.sqlite.JDBC")
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String userHome = System.getProperty("user.home");
        String dbPath = userHome + "/.trading-platform/trading.db";
        File dbDir = new File(userHome, ".trading-platform");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        String url = "jdbc:sqlite:" + dbPath + "?busy_timeout=30000";
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .driverClassName("org.sqlite.JDBC")
                .build();
        ds.setConnectionInitSql(
                "PRAGMA busy_timeout=30000; PRAGMA journal_mode=WAL;");
        return ds;
    }
}
