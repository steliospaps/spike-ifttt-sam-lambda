package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events;

import java.math.BigDecimal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class PriceUpdateEvent {
	private String epic;
	private BigDecimal bid;
	private BigDecimal offer;
}
