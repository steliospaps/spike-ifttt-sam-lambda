package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.Alerter;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.Trigger;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import lombok.extern.slf4j.Slf4j;

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
	private ObjectMapper jaxbMapper;

	@Autowired
	private DynamoDBMapper dynamoDbMapper;

	@PostConstruct
	void scanForTriggers() {
		log.info("scanning entities scanChunk={} ***********************", scanChunk);

		DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
		scanExpression.setLimit(scanChunk);
		// TODO: optimize this for parallel scan
		// TODO: how to deal with more results than jvm memory? (shard?)
		PaginatedScanList<Trigger> scan = dynamoDbMapper.scan(Trigger.class, scanExpression);

		scan//
				.forEach(Util.sneakyC(tr -> {
					log.info("scanned {}", tr);
					if (TriggersUtil.isTriggerRecord(tr)) {
						log.info("looks like a trigger");
						alerter.onNewTrigger(tr.getPK(),
								jaxbMapper.readValue(tr.getTriggerFields(), TriggerFields.class));

					} else {
						log.info("skipping");
					}
				}));
		log.info("scanned entities ******************");

	}
}
