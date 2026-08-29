// Desarrollado por Emmanuel Santana Solano
package com.aircargo.exportservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.aircargo.exportservice", "com.aircargo.common"})
@EnableCaching
@EnableFeignClients(basePackages = "com.aircargo.feign.client")
public class ExportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExportServiceApplication.class, args);
    }
}
