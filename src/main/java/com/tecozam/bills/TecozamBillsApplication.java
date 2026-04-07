package com.tecozam.bills;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TecozamBillsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TecozamBillsApplication.class, args);
    }
}
