package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent.Meta;

class TriggerEventTest {
	private ObjectMapper mapper=new ObjectMapper();
	@Test
	void test() throws JsonProcessingException {
		String expected = "[{\"seqNo\":1, \"data\":{\n"
				+ "			   \"instrument_name\":\"someName\",\n"
				+ "			   \"price\":\"10000\",\n"
				+ "			   \"instrument\":\"epic\",\n"
				+ "			   \"meta\": {\n"
				+ "			      \"id\": \"14b9-1fd2-acaa-5df5\",\n"
				+ "			      \"timestamp\": 1383597267\n"
				+ "			   }}}]";
		TriggerEvent event = TriggerEvent.builder()
				.instrument("epic")//
				.instrumentName("someName")//
				.price("10000")//
				.meta(Meta.builder()//
						.id("14b9-1fd2-acaa-5df5")//
						.timestamp(1383597267)//
						.build())//
				.build();
		String str = mapper.writeValueAsString(
				List.of(TriggerEventWrapper.builder().seqNo(1).data(event).build()));

		assertEquals(mapper.readTree(expected), mapper.readTree(str));
		
	}

}
