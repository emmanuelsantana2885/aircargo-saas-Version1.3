// Desarrollado por Emmanuel Santana Solano
package com.aircargo.flightservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = {"com.aircargo.flightservice", "com.aircargo.common"})
@EnableCaching
@EntityScan(basePackages = {"com.aircargo.flightservice.entity", "com.aircargo.common.entity"})
public class FlightServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightServiceApplication.class, args);
    }
}