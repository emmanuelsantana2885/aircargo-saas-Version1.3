// Desarrollado por Emmanuel Santana Solano
package com.aircargo.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.aircargo.authservice", "com.aircargo.common"})
@EntityScan(basePackages = {"com.aircargo.authservice.entity", "com.aircargo.authservice.event",
        "com.aircargo.common.entity"})
@EnableCaching
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
