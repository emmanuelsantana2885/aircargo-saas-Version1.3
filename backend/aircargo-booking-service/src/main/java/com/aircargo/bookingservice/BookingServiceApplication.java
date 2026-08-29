// Desarrollado por Emmanuel Santana Solano
package com.aircargo.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.aircargo.bookingservice", "com.aircargo.common"})
@EnableFeignClients(basePackages = "com.aircargo.feign.client")
@EntityScan(basePackages = {"com.aircargo.bookingservice.entity", "com.aircargo.common.entity"})
@EnableCaching
public class BookingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}