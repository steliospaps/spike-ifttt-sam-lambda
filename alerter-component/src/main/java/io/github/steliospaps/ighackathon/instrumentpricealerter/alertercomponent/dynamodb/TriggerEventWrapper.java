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
public class TriggerEventWrapper {
	/**
	 * ignored?
	 */
	private long seqNo;
	private TriggerEvent data;
}
