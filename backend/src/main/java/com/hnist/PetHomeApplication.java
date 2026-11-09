package com.hnist;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 应用入口：@MapperScan 扫描 MyBatis mapper 包，@EnableScheduling 启用限流窗口清理等定时任务。 */
@MapperScan("com.hnist.mapper")
@EnableScheduling
@SpringBootApplication
public class PetHomeApplication {

    private PetHomeApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(PetHomeApplication.class, args);
    }
}
