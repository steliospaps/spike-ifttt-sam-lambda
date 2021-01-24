package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.Alerter;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRowUtil;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromDBEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.TableScannedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * scans the persistent store on application start.
 * 
 * @author stelios
 *
 */
@Component
@Slf4j
@Profile("!junit")
@DependsOn("streamListener")
public class InitialTableScanner {

	@Value("${app.dynamodb.scan-chunk}")
	private int scanChunk;
	@Autowired
	private Alerter alerter;
	
	@Autowired
	private ApplicationEventPublisher publisher;
	
	@Autowired
	private ObjectMapper jaxbMapper;

	@Autowired
	private DynamoDBMapper dynamoDbMapper;

	@EventListener(ApplicationReadyEvent.class)
	void scanDynamoDbEntities() {
		log.info("scanning all dynamoDb entities scanChunk={} ***********************", scanChunk);

		DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
		scanExpression.setLimit(scanChunk);
		// TODO: optimize this for parallel scan
		// TODO: how to deal with more results than jvm memory? (shard?)
		PaginatedScanList<MyTableRow> scan = dynamoDbMapper.scan(MyTableRow.class, scanExpression);

		scan//
				.forEach(Util.sneakyC(tr -> {
					log.info("scanned {}", tr);
					if (MyTableRowUtil.isTriggerRecord(tr)) {
						log.info("looks like a trigger");
						alerter.onNewTrigger(tr.getPK(),//
								jaxbMapper.readValue(tr.getTriggerFields(), TriggerFields.class),//
								tr.getTriggerEvents()!=null,// 
								Optional.ofNullable(tr.getTriggerType())//
									.orElse("instrument_price")//default value for field (only valid value when introduced) 
								);
					} else if(MyTableRowUtil.isInstrumentRecord(tr)) {
						log.info("looks like an instrument");
						publisher.publishEvent(MyTableRowUtil.toInstrumentReceivedFromDBEvent(tr));
					} else {
						log.info("ignoring (not instrument or trigger row)");
					}
				}));
		log.info("scanned entities ******************");
		publisher.publishEvent(new TableScannedEvent());

	}
}
