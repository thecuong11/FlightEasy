package com.flighteasy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlighteasyApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlighteasyApplication.class, args);
	}

}
