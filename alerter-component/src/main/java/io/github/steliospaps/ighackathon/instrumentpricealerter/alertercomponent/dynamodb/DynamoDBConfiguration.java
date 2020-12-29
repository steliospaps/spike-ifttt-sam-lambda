package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;


@Configuration
public class DynamoDBConfiguration {

	/*
	@Bean
	//name of function has to match argument of @EnableDynamoDBRepositories.dynamoDBMapperConfigRef above 
    public DynamoDBMapperConfig myDynamoDBMapperConfig(TableNameOverride tableNameOverrider) {
        // Create empty DynamoDBMapperConfig builder
	DynamoDBMapperConfig.Builder builder = new DynamoDBMapperConfig.Builder();
	// Inject missing defaults from the deprecated method
	builder.withTypeConverterFactory(DynamoDBTypeConverterFactory.standard());
	builder.withTableNameResolver(DefaultTableNameResolver.INSTANCE);
	builder.setPaginationLoadingStrategy(PaginationLoadingStrategy.ITERATION_ONLY);
        // Inject the table name overrider bean
	builder.withTableNameOverride(tableNameOverrider);
	return builder.build();
    }
    
    @Bean
    public TableNameOverride tableNameOverrider(@Value("${app.dynamodb.table-name}") String tableName) {
        return TableNameOverride.withTableNameReplacement(tableName);
    }
    */
	@Bean
	public AmazonDynamoDB amazonDynamoDB(AWSCredentialsProvider credentialsProvider) {
		return AmazonDynamoDBClientBuilder.standard().withCredentials(credentialsProvider)
				.withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-west-2"))
				.build();
	}

}
