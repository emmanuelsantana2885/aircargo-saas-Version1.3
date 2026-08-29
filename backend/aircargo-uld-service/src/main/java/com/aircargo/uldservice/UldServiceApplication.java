// Desarrollado por Emmanuel Santana Solano
package com.aircargo.uldservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.aircargo.uldservice", "com.aircargo.common"})
@EnableCaching
@EnableAsync
@EnableFeignClients(basePackages = "com.aircargo.feign.client")
public class UldServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UldServiceApplication.class, args);
    }
}
