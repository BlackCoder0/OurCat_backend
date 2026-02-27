package com.ourcat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OurCatApplication {

    public static void main(String[] args) {
        SpringApplication.run(OurCatApplication.class, args);
    }
}
