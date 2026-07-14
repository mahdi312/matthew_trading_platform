package com.mst.matt.tradingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TradingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingServiceApplication.class, args);
    }

}
