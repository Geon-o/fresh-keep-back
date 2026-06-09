package com.example.fresh_keep;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FreshKeepApplication {

	public static void main(String[] args) {
		// Load .env variables into System properties before Spring Boot starts
		try {
			Dotenv dotenv = Dotenv.configure()
					.ignoreIfMalformed()
					.ignoreIfMissing()
					.load();
			dotenv.entries().forEach(entry -> {
				if (System.getProperty(entry.getKey()) == null) {
					System.setProperty(entry.getKey(), entry.getValue());
				}
			});
		} catch (Exception e) {
			// Ignore if .env is missing or fails to load
		}

		SpringApplication.run(FreshKeepApplication.class, args);
	}

}
