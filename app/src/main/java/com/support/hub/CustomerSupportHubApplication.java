package com.support.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.support.customer")
@EnableMongoRepositories(basePackages = "com.support.ticket")
@EnableRetry
@EnableScheduling
public class CustomerSupportHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerSupportHubApplication.class, args);
    }
}

