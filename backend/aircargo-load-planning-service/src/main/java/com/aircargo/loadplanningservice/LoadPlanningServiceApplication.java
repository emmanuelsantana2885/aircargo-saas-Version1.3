// Desarrollado por Emmanuel Santana Solano
package com.aircargo.loadplanningservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.aircargo.loadplanningservice", "com.aircargo.common"})
@EnableCaching
@EnableFeignClients(basePackages = "com.aircargo.feign.client")
public class LoadPlanningServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoadPlanningServiceApplication.class, args);
    }
}
