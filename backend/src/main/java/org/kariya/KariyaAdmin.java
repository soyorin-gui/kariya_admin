package org.kariya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KariyaAdmin {
    public static void main(String[] args) {
        SpringApplication.run(KariyaAdmin.class, args);
    }
}
