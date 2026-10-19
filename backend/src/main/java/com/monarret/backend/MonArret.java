package com.monarret.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MonArret {

	public static void main(String[] args) {
		
		SpringApplication app = new SpringApplication(MonArret.class);
		
		ConfigurableApplicationContext context = app.run(args);
	}
	
}
