package com.belsenai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BelsenAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BelsenAiApplication.class, args);
    }
}
