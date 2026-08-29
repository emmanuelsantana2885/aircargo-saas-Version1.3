// Desarrollado por Emmanuel Santana Solano
package com.aircargo.mawbservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = {"com.aircargo.mawbservice", "com.aircargo.common"})
@EnableCaching
public class MawbServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MawbServiceApplication.class, args);
    }
}