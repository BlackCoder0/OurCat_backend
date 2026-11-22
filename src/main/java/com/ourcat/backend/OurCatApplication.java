package com.ourcat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.ourcat.backend.config.DemoProfileGuard;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class OurCatApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(OurCatApplication.class);
        app.addInitializers(new DemoProfileGuard());
        app.run(args);
    }
}
