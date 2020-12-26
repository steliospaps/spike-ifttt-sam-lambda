package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields.Direction;

class TriggerFieldsTest {

	private ObjectMapper mapper = new ObjectMapper();
	
	@Test
	void test() throws JsonMappingException, JsonProcessingException {
		String str = "{\"epic\": \"epic1\",\n"
				+ "            \"direction\": \"OVER\",\n"
				+ "            \"price\":\"10,000.00\"}";
		TriggerFields fields = mapper.readValue(str, TriggerFields.class);
		assertEquals("epic1",fields.getEpic());
		assertEquals(Direction.OVER,fields.getDirection());
		assertEquals("10,000.00",fields.getPrice());
	}
}
