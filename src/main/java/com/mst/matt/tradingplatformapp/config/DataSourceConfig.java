package com.mst.matt.tradingplatformapp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.File;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        String userHome = System.getProperty("user.home");
        String dbPath = userHome + "/.trading-platform/trading.db";
        File dbDir = new File(userHome, ".trading-platform");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        // busy_timeout + WAL reduce SQLITE_BUSY when UI and background tasks write concurrently
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
