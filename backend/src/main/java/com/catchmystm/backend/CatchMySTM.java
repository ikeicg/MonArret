package com.catchmystm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CatchMySTM {

	public static void main(String[] args) {
		
		SpringApplication app = new SpringApplication(CatchMySTM.class);
		app.setWebApplicationType(WebApplicationType.REACTIVE);
		
		ConfigurableApplicationContext context = app.run(args);
	}
	
}
