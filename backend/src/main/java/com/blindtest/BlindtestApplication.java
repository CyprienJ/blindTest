package com.blindtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BlindtestApplication {

    /** Application entry point. */
    public static void main(String[] args) {
        SpringApplication.run(BlindtestApplication.class, args);
    }
}
