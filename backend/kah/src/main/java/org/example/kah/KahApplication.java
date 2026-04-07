package org.example.kah;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.example.kah.mapper")
@ConfigurationPropertiesScan
@EnableScheduling
public class KahApplication {

    public static void main(String[] args) {
        SpringApplication.run(KahApplication.class, args);
    }
}