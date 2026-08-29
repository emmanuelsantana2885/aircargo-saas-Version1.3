// Desarrollado por Emmanuel Santana Solano
package com.aircargo.warehouseservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.aircargo.warehouseservice", "com.aircargo.common"})
@EnableCaching
@EnableAsync
@EnableFeignClients(basePackages = "com.aircargo.feign.client")
public class WarehouseServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseServiceApplication.class, args);
    }
}