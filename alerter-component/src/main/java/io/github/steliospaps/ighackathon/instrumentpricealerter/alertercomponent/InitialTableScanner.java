package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.server.upgrade.TomcatRequestUpgradeStrategy;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.PagingTriggerRepository;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.Trigger;
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
			.forEach(tr -> {
				log.info("scanned {}",tr);
				Trigger toUpdate = new Trigger();
				toUpdate.setPK(tr.getPK());
				toUpdate.setTriggerEvents("[]");
				repository.save(toUpdate);//this overwrites other fields
			});
		
		
		log.info("scanned entities ******************");
		
	}
}
