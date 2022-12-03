package com.navercorp.captchaBatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.navercorp.captchaBatch.mapper")
public class CaptchaBatchApplication {
	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(CaptchaBatchApplication.class, args)));
	}
}

