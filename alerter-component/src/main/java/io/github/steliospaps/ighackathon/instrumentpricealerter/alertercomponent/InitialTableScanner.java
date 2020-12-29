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
@DependsOn("streamListener")
public class InitialTableScanner {

	@Autowired
	private AmazonDynamoDB dynamoDb;
	
	@Value("${app.dynamodb.table-name}") 
	private String tableName;
	@Value("${app.dynamodb.scan-chunk:10}") 
	private int scanChunk;
		
	@PostConstruct
	void scanForTriggers(){
		log.info("scanning entities scanChunk={} tableName={} ***********************",scanChunk, tableName);

		DynamoDBMapperConfig cfg = new DynamoDBMapperConfig.Builder()
				.withTableNameOverride(TableNameOverride.withTableNameReplacement(tableName))//
				.withSaveBehavior(SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)// so that nulls are ignored
				.build();
		DynamoDBMapper mapper = new DynamoDBMapper(dynamoDb,cfg);
		
		
			DynamoDBScanExpression scanExpression =  new DynamoDBScanExpression();
			scanExpression.setLimit(scanChunk);
			//TODO: optimize this for parallel scan
			//TODO: how to deal with more results than jvm memory? (shard?)
			PaginatedScanList<Trigger> scan = mapper.scan(Trigger.class, scanExpression);
			
			scan//
				.forEach(tr -> {
				log.info("scanned {}",tr);
					//Trigger toUpdate = new Trigger();
					//toUpdate.setPK(tr.getPK());
					//toUpdate.setTriggerEvents("[]");
					//mapper.save(toUpdate);//this updates non null fields (SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)
				});
		log.info("scanned entities ******************");

	}
}
