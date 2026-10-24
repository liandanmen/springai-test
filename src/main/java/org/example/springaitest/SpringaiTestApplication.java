package org.example.springaitest;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.example.springaitest.mapper")
@Slf4j

public class SpringaiTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringaiTestApplication.class, args);
    }

}
