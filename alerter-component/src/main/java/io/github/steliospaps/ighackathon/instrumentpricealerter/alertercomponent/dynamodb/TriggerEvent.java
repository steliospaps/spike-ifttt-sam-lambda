package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerEvent {
	@JsonProperty("instrument_name")
	private String instrumentName;
	private String price;
	private String instrument;
	private Meta meta;
	@Data
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Meta{
		private String id;
		/**
		 * epoch seconds
		 */
		private long timestamp;
	}
}
