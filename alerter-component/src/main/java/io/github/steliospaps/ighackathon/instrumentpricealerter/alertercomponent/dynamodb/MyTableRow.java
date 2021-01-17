package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamoDBTable(tableName = "dummy")//name comes from configuration in DynamoDBConfiguration
//see https://github.com/boostchicken/spring-data-dynamodb
public class MyTableRow {
	//If I omit the attributeName it does not work
	//see README.md of root for schema
	@Getter(onMethod = @__({@DynamoDBHashKey(attributeName = "PK")}))
	private String PK;
	@Getter(onMethod = @__({@DynamoDBRangeKey(attributeName = "SK")}))
	private String SK;
	
	/**
	 * seconds since epoch
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private Long expiresOn;
	
	
	/**
	 * on Trigger entity
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String triggerId;
	/**
	 * a string that can be parsed to a json object
	 * <pre>
	 * {
            "epic": "epic1",
            "direction": "OVER",
            "price":"10000.00"
        }
	 * </pre>
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String triggerFields;

	/**
	 * on Trigger entity

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

	/**
	 * on Trigger Event 

	 * a string that can be parsed to a json object
	 * <pre>
			{
			   "instrument_name":"someName",
			   "price":"10000",
			   "instrument":"epic",
			   "meta": {
			      "id": "14b9-1fd2-acaa-5df5",
			      "timestamp": 1383597267
			   }
			}
	 * </pre>
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String triggerEvent;

	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String triggerType;
	
	/**
	 * on instrument entity
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String epic;
	/**
	 * on instrument entity
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String description;
	/**
	 * on instrument entity
	 */
	@Getter(onMethod = @__({@DynamoDBAttribute}))
	private String symbol;
}
