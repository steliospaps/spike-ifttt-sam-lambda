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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
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
import com.amazonaws.services.kinesis.model.Record;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.Trigger;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Slf4j
@Profile("!junit")
public class StreamListener {
	public class RecordProcessor implements IRecordProcessor {

		@Override
		public void initialize(InitializationInput initializationInput) {
			log.info("initialize {}", initializationInput);
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
								.map(sneaky(str -> jaxbMapper.readValue(str, TriggerFields.class)))//
								.orElse(null);
						log.info("new triggerId={} triggerFields={}", tr.getPK(), tf);
						break;
					}
					case "MODIFY":
						continue;
					case "REMOVE": {
						Trigger tr = dynamoDbMapper.marshallIntoObject(Trigger.class, o.getDynamodb().getOldImage());
						log.info("delete triggerId={} triggerFields={}", tr.getPK());
						break;
					}
					default:
						log.warn("unexpected eventName {}",o.getEventName());
					}
				} else {
					log.warn("got record of unexpected class={}",r.getClass());

				}
				processRecordsInput.getCheckpointer().checkpoint(r);
			}
			

		}

		@Override
		@SneakyThrows
		public void shutdown(ShutdownInput shutdownInput) {
			log.info("shutdown {}", shutdownInput);
			shutdownInput.getCheckpointer().checkpoint();
		}

	}

	public interface SneakyFunction<T, R> {
		R apply(T t) throws Exception;

		@SneakyThrows
		default R sneakCall(T t) {
			return apply(t);
		}
	}

	public static <T, R> Function<T, R> sneaky(SneakyFunction<T, R> f) {
		return t -> f.sneakCall(t);
	}

	@Autowired
	private AmazonDynamoDB dynamoDb;

	@Value("${app.dynamodb.table-name}")
	private String tableName;

	@Autowired
	private AWSCredentialsProvider awsCredentialsProvider;

	@Autowired
	private ObjectMapper jaxbMapper;

	private Thread t;

	private ExecutorService execService;

	private Worker worker;

	private DynamoDBMapper dynamoDbMapper;

	@PostConstruct
	public void init() {
		AmazonDynamoDBStreams dynamoDBStreamsClient = AmazonDynamoDBStreamsClientBuilder.standard()
				// .withRegion(awsRegion)
				.withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-west-1"))
				.withCredentials(awsCredentialsProvider).build();
		AmazonDynamoDBStreamsAdapterClient adapterClient = new AmazonDynamoDBStreamsAdapterClient(
				dynamoDBStreamsClient);

		// cloudWatchClient = AmazonCloudWatchClientBuilder.standard()
		// //.withRegion(awsRegion)
		// .build();

		dynamoDbMapper = new DynamoDBMapper(dynamoDb);

		String streamArn = dynamoDb.describeTable(tableName).getTable().getLatestStreamArn();

		KinesisClientLibConfiguration workerConfig = new KinesisClientLibConfiguration("streams-adapter-triggerstable",
				streamArn, awsCredentialsProvider, "streams-adapter-triggerstable-worker").withMaxRecords(1000)
						.withIdleTimeBetweenReadsInMillis(500)
						.withInitialPositionInStream(InitialPositionInStream.LATEST);

		log.info("*********** Creating worker for stream: " + streamArn);
		IRecordProcessorFactory recordProcessorFactory = () -> new RecordProcessor();
		execService = Executors.newFixedThreadPool(1);
		worker = StreamsWorkerFactory.createDynamoDbStreamsWorker(recordProcessorFactory, workerConfig, adapterClient,
				dynamoDb, () -> new NullMetricsScope(), // TODO this should be using a cloudwatch client instead? for
														// now I leave it likie this so that I can start locally
				execService);
		log.info("**** Starting worker...");
		t = new Thread(worker);
		t.start();

	}

	@PreDestroy
	@SneakyThrows
	public void shutdown() {
		worker.shutdown();
		execService.shutdown();
		t.join();
	}
}
