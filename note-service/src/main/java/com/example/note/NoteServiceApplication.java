package com.example.note;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
public class NoteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteServiceApplication.class, args);
    }
}
