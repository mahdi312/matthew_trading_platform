package com.mst.matt.tradingplatformapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/context-test.db",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",   // ← add this
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect", // if needed
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.hikari.maximum-pool-size=1",
    "app.live.startup-enabled=false"
})
class TradingPlatformAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
