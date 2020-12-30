package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.streamsadapter.AmazonDynamoDBStreamsAdapterClient;
import com.amazonaws.services.dynamodbv2.streamsadapter.StreamsWorkerFactory;
import com.amazonaws.services.dynamodbv2.streamsadapter.model.RecordAdapter;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.metrics.impl.NullMetricsScope;
import com.amazonaws.services.kinesis.metrics.interfaces.MetricsLevel;
import com.amazonaws.services.kinesis.model.Record;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.Alerter;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.Trigger;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Slf4j
@Profile("!junit")
public class StreamListener implements HealthIndicator{

	public class RecordProcessor implements IRecordProcessor {

		@Override
		public void initialize(InitializationInput initializationInput) {
			log.info("initialize {}", initializationInput);
			connected=true;
		}

		@Override
		@SneakyThrows
		public void processRecords(ProcessRecordsInput processRecordsInput) {
			log.info("processRecords {}", processRecordsInput);
			List<Record> records = processRecordsInput.getRecords();
			for (Record r : records) {
				if (r instanceof RecordAdapter) {
					RecordAdapter ra = (RecordAdapter) r;
					com.amazonaws.services.dynamodbv2.model.Record o = ra.getInternalObject();
					log.info("{}", o);

					switch (o.getEventName()) {
					case "INSERT": {
						Trigger tr = dynamoDbMapper.marshallIntoObject(Trigger.class, o.getDynamodb().getNewImage());
						TriggerFields tf = Optional.ofNullable(tr.getTriggerFields())//
								.map(Util.sneakyF(str -> jaxbMapper.readValue(str, TriggerFields.class)))//
								.orElse(null);
						log.info("new triggerId={} triggerFields={}", tr.getPK(), tf);
						alerter.onNewTrigger(tr.getPK(),tf);
						break;
					}
					case "MODIFY":
						continue;
					case "REMOVE": {
						Trigger tr = dynamoDbMapper.marshallIntoObject(Trigger.class, o.getDynamodb().getOldImage());
						log.info("delete triggerId={} triggerFields={}", tr.getPK());
						alerter.onDeleteTrigger(tr.getPK());
						break;
					}
					default:
						log.warn("unexpected eventName {}", o.getEventName());
					}
				} else {
					log.warn("got record of unexpected class={}", r.getClass());

				}
				processRecordsInput.getCheckpointer().checkpoint(r);
			}

		}

		@Override
		@SneakyThrows
		public void shutdown(ShutdownInput shutdownInput) {
			log.info("shutdown {}", shutdownInput);
			connected=false;
			shutdownInput.getCheckpointer().checkpoint();
		}

	}

	@Autowired
	private AmazonDynamoDB dynamoDb;

	@Value("${app.dynamodb.table-name}")
	private String tableName;
	@Value("${app.dynamodb.streams.table-name}")
	private String kinesisTableName;
	@Value("${app.dynamodb.streams.worker-name}")
	private String kinesisWorkerName;

	@Autowired
	private AWSCredentialsProvider awsCredentialsProvider;

	@Autowired
	private ObjectMapper jaxbMapper;

	private Thread t;

	private Worker worker;

	private DynamoDBMapper dynamoDbMapper;

	@Autowired
	private AmazonCloudWatch cloudWatchClient;

	@Autowired
	private AmazonDynamoDBStreamsAdapterClient adapterClient;
	@Value("${app.dynamodb.streams.metrics-level}")
	private MetricsLevel metricsLevel;
	
	@Autowired
	private Alerter alerter;

	private volatile boolean connected=false;
	
	@PostConstruct
	public void init() {
		connected=false;
		dynamoDbMapper = new DynamoDBMapper(dynamoDb);

		String streamArn = dynamoDb.describeTable(tableName).getTable().getLatestStreamArn();

		KinesisClientLibConfiguration workerConfig = new KinesisClientLibConfiguration(kinesisTableName,
				streamArn, awsCredentialsProvider, kinesisWorkerName)//
						.withMaxRecords(1000)//
						.withIdleTimeBetweenReadsInMillis(500)//
						.withMetricsLevel(metricsLevel)//
						.withInitialPositionInStream(InitialPositionInStream.LATEST);

		log.info("*********** Creating worker for stream: " + streamArn);
		IRecordProcessorFactory recordProcessorFactory = () -> new RecordProcessor();

		worker = StreamsWorkerFactory.createDynamoDbStreamsWorker(recordProcessorFactory,//
				workerConfig,//
				adapterClient, //
				dynamoDb, //
				cloudWatchClient);
		log.info("**** Starting worker...");
		t = new Thread(worker, "dynamodbStreams-worker");
		t.start();

	}

	@PreDestroy
	@SneakyThrows
	public void shutdown() {
		log.info("stopping worker");
		worker.shutdown();
		t.join();
		connected=false;
		log.info("stopped worker");
	}

	@Override
	public Health health() {
		if(t!=null && !t.isAlive()) {
			return Health.down().withDetail("worker-thread-status","down").build();
		}
		if(false && !connected) {//during a release the new image cannot get connected until the old one exits
			return Health.down().withDetail("connected", false).build();
		}
		return Health.up().build();
	}
}
