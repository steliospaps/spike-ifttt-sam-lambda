package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.prevdaychange;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.prevdaychange.Orderer.Order;

class OrdererTest {
	
	@Test
	void test() {
		var impl = new Orderer<String>();
		Order<String> or = impl.tick(new Order<>(), "hi", new BigDecimal(1));
		assertEquals(List.of("hi"),or.getItems());
		assertEquals(or.getValues(),List.of(new BigDecimal(1)));
		
		or = impl.tick(or, "there", new BigDecimal(-2));
		assertEquals(List.of("hi","there"),or.getItems());
		assertEquals(or.getValues(),List.of(new BigDecimal(1),new BigDecimal(-2)));

		or = impl.tick(or, "there", new BigDecimal(2));
		assertEquals(List.of("there","hi"),or.getItems());
		assertEquals(or.getValues(),List.of(new BigDecimal(2),new BigDecimal(1)));

		or = impl.tick(or, "there", new BigDecimal(0));
		assertEquals(List.of("there","hi"),or.getItems());
		assertEquals(List.of(new BigDecimal(2),new BigDecimal(1)),or.getValues());

		or = impl.tick(or, "hi", new BigDecimal(3));
		assertEquals(List.of("hi","there"),or.getItems());
		assertEquals(List.of(new BigDecimal(3),new BigDecimal(2)),or.getValues());

		or = or.limitSizeTo(3);
		assertEquals(List.of("hi","there"),or.getItems());
		assertEquals(List.of(new BigDecimal(3),new BigDecimal(2)),or.getValues());

		or = or.limitSizeTo(1);
		assertEquals(List.of("hi"),or.getItems());
		assertEquals(List.of(new BigDecimal(3)),or.getValues());
	}

	@Test
	void testAbsolute() {
		var impl = new Orderer<String>((a,b)->a.abs().compareTo(b.abs()));
		Order<String> or = impl.tick(new Order<>(), "hi", new BigDecimal(1));
		assertEquals(List.of("hi"),or.getItems());
		assertEquals(or.getValues(),List.of(new BigDecimal(1)));
		
		or = impl.tick(or, "there", new BigDecimal(-2));
		assertEquals(List.of("there","hi"),or.getItems());
		assertEquals(or.getValues(),List.of(new BigDecimal(-2),new BigDecimal(1)));

		or = impl.tick(or, "there", new BigDecimal(0));
		assertEquals(List.of("there","hi"),or.getItems());
		assertEquals(List.of(new BigDecimal(-2),new BigDecimal(1)),or.getValues());

		or = impl.tick(or, "hi", new BigDecimal(3));
		assertEquals(List.of("hi","there"),or.getItems());
		assertEquals(List.of(new BigDecimal(3),new BigDecimal(-2)),or.getValues());

		or = or.limitSizeTo(3);
		assertEquals(List.of("hi","there"),or.getItems());
		assertEquals(List.of(new BigDecimal(3),new BigDecimal(-2)),or.getValues());

		or = or.limitSizeTo(1);
		assertEquals(List.of("hi"),or.getItems());
		assertEquals(List.of(new BigDecimal(3)),or.getValues());
	}
}
