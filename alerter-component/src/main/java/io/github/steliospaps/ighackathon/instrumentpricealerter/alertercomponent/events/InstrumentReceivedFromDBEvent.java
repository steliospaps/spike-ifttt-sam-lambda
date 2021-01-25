package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class InstrumentReceivedFromDBEvent {
	private String symbol;
	private String epic;
	private String description;
	
}
