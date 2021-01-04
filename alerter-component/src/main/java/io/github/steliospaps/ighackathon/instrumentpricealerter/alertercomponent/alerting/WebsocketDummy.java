package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting;

import java.net.URL;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WebsocketDummy {
	@Value("${app.ws.url}")
	private String url;
	@Value("${app.ws.password}")
	private String password;
	@Value("${app.ws.username}")
	private String username;
	
	@PostConstruct
	public void log() {
		log.info("url={}",url);
		log.info("password={}",password);
		log.info("username={}",username);
	}
}
