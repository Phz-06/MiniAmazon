package edu.ece568.MiniAmazon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniAmazonApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniAmazonApplication.class, args);
	}

}
