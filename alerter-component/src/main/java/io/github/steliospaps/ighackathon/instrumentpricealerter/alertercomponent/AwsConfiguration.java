package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import org.apache.http.client.CredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.streamsadapter.AmazonDynamoDBStreamsAdapterClient;

@Configuration
public class AwsConfiguration {

	@Bean
	public AmazonDynamoDBStreamsAdapterClient amazonDynamoDBStreamsAdapterClient(
			AmazonDynamoDBStreams dynamoDBStreamsClient) {
		return new AmazonDynamoDBStreamsAdapterClient(dynamoDBStreamsClient);
	}

	@Value("${app.dynamodb.table-name}")
	private String tableName;

	@Bean
	public DynamoDBMapper dynamoDbMapperTriggersTable(AmazonDynamoDB dynamoDb) {

		DynamoDBMapperConfig cfg = new DynamoDBMapperConfig.Builder()
				.withTableNameOverride(TableNameOverride.withTableNameReplacement(tableName))//
				.withSaveBehavior(SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)// so that nulls are ignored
				.build();
		return new DynamoDBMapper(dynamoDb, cfg);
	}

	@Configuration
	@Profile("!local")
	public class NonLocalConfiguration {
		@Bean
		public AWSCredentialsProvider amazonAWSCredentialsProvider() {
			return DefaultAWSCredentialsProviderChain.getInstance();
		}

		@Bean
		public AmazonDynamoDB amazonDynamoDB() {
			return AmazonDynamoDBClientBuilder.standard().withCredentials(amazonAWSCredentialsProvider()).build();
		}

		@Bean
		public AmazonDynamoDBStreams amazonDynamoDBStreams(AWSCredentialsProvider awsCredentialsProvider) {
			return AmazonDynamoDBStreamsClientBuilder.standard().withCredentials(amazonAWSCredentialsProvider())
					.build();
		}

		@Bean
		public AmazonCloudWatch amazonCloudWatch() {
			return AmazonCloudWatchClientBuilder.standard().withCredentials(amazonAWSCredentialsProvider()).build();
		}
	}

	@Configuration
	@Profile("local")
	public class LocalConfiguration {

		@Value("${local.aws.endpoint}")
		private String endpoint;
		@Value("${local.aws.region}")
		private String region;

		@Bean
		public AWSCredentialsProvider amazonAWSCredentialsProvider() {
			return new AWSStaticCredentialsProvider(
					new BasicAWSCredentials("amazonAWSAccessKey", "amazonAWSSecretKey"));
		}

		@Bean
		public EndpointConfiguration endpoint() {
			return new EndpointConfiguration(endpoint, region);
		}

		@Bean
		public AmazonDynamoDB amazonDynamoDB() {
			return AmazonDynamoDBClientBuilder.standard().withCredentials(amazonAWSCredentialsProvider())
					.withEndpointConfiguration(endpoint()).build();
		}

		@Bean
		public AmazonDynamoDBStreams amazonDynamoDBStreams(AWSCredentialsProvider awsCredentialsProvider) {
			return AmazonDynamoDBStreamsClientBuilder.standard().withCredentials(amazonAWSCredentialsProvider())
					.withEndpointConfiguration(endpoint()).build();
		}

		@Bean
		public AmazonCloudWatch amazonCloudWatch() {
			return AmazonCloudWatchClientBuilder.standard().withCredentials(amazonAWSCredentialsProvider())
					.withEndpointConfiguration(endpoint()).build();
		}

	}
}
