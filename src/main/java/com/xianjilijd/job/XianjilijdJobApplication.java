package com.xianjilijd.job;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xianjilijd.job.mapper")
public class XianjilijdJobApplication {
    public static void main(String[] args) {
        SpringApplication.run(XianjilijdJobApplication.class, args);
    }
}
