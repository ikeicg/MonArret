package com.catchmystm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.catchmystm.backend.service.GtfsStaticLoader;

@SpringBootApplication
@EnableScheduling
public class CatchMySTM {

	public static void main(String[] args) {
		
		ConfigurableApplicationContext context = SpringApplication.run(CatchMySTM.class, args);
	}
	
}
