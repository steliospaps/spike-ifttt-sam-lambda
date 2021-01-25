package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlerterComponentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlerterComponentApplication.class, args);
	}

}
