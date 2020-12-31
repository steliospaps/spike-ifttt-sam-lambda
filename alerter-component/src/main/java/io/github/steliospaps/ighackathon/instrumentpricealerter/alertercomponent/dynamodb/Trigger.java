package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import lombok.Data;
import lombok.Getter;

@Data
@DynamoDBTable(tableName = "dummy")//name comes from configuration in DynamoDBConfiguration
//see https://github.com/boostchicken/spring-data-dynamodb
public class Trigger {
	//If I omit the attributeName is does not work
	@Getter(onMethod = @__({@DynamoDBHashKey(attributeName = "PK")}))
	private String PK;
	@Getter(onMethod = @__({@DynamoDBHashKey(attributeName = "SK")}))
	private String SK;
	
	private String triggerId;
	/**
	 * a string that can be parsed to a json object
	 * <pre>
	 * {
            "epic": "epic1",
            "direction": "OVER",
            "price":"10,000.00"
        }
	 * </pre>
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String triggerFields;

	/**
	 * a string that can be parsed to a json object
	 * <pre>
		[
		  {
			"seqNo": 1,
			"data": {
			   "instrument_name":"someName",
			   "price":"10000",
			   "instrument":"epic",
			   "meta": {
			      "id": "14b9-1fd2-acaa-5df5",
			      "timestamp": 1383597267
			   }
			}
		  }
		] 
	 * </pre>
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String triggerEvents;
	
}
