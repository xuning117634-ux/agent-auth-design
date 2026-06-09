package com.huawei.it.roma.liveeda.policycenter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.huawei.it.roma.liveeda.policycenter.infrastructure.mybatis")
@SpringBootApplication
public class PolicyCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyCenterApplication.class, args);
    }
}
