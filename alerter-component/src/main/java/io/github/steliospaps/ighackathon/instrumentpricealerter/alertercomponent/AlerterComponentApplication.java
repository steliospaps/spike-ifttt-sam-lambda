package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

@SpringBootApplication
public class AlerterComponentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlerterComponentApplication.class, args);
	}

	@Bean
	@Profile("ChangeME")
	public AWSCredentialsProvider amazonAWSCredentialsProvider() {
		return DefaultAWSCredentialsProviderChain.getInstance();
	}

	@Bean
	//Profile("local")
	public AWSCredentialsProvider localAmazonAWSCredentialsProvider() {
		return new AWSStaticCredentialsProvider(new BasicAWSCredentials("amazonAWSAccessKey", "amazonAWSSecretKey"));
	}
}
