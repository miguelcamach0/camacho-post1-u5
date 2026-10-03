package com.universidad.reservaslabs.reservas_labs_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.universidad.reservaslabs")
@EntityScan(basePackages = "com.universidad.reservaslabs.model")
@EnableJpaRepositories(basePackages = "com.universidad.reservaslabs.repository")
public class ReservasLabsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservasLabsApiApplication.class, args);
    }
}
