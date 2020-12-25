package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.PagingTriggerRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * scans the persistent store on application start.
 * @author stelios
 *
 */
@Component
@Slf4j
@Profile("!junit")
public class InitialTableScanner {

	@Autowired
	private PagingTriggerRepository repository;
	
	@PostConstruct
	void scanForTriggers(){
		log.info("scanning entities ***********************");
		//scan
		StreamSupport.stream(repository.findAll().spliterator(),false)//
			.forEach(tr -> log.info("scanned {}",tr));
		
		
		log.info("scanned entities ******************");
		
	}
}
