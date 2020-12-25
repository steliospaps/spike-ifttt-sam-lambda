package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;


@Configuration
@EnableDynamoDBRepositories(basePackageClasses = PagingTriggerRepository.class)
public class DynamoDBConfiguration {
	/*
	@Bean
	public DynamoDBMapperConfig dynamoDBMapperConfig() {
		return DynamoDBMapperConfig.DEFAULT;
	}
	 */
	/*
	@Bean
	public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB, DynamoDBMapperConfig config) {
		return new DynamoDBMapper(amazonDynamoDB, config);
	}*/

	@Bean
	public AmazonDynamoDB amazonDynamoDB(AWSCredentialsProvider credentialsProvider) {
		return AmazonDynamoDBClientBuilder.standard().withCredentials(credentialsProvider)
				.withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-west-2"))
				.build();
	}

}
