package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromDBEvent;

public class MyTableRowUtil {
	private MyTableRowUtil() {
	}
	
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	
	static public boolean isTriggerRecord(MyTableRow tr) {
		return tr.getPK().startsWith("TR#") &&
			(tr.getPK().equals(tr.getSK())); 
	}
	
	static public MyTableRow.MyTableRowBuilder makeTriggerEventRow(String triggerPK) {
		return MyTableRow.builder()//
				.PK(triggerPK)//
				.SK("EV#"+ZonedDateTime.now(ZoneOffset.UTC).format(FORMATTER))
				.expiresOn(Instant.now().plusSeconds(24*60*60).getEpochSecond());//24h
	}

	static public boolean isInstrumentRecord(MyTableRow tr) {
		return tr.getPK().equals("INSTRUMENT") &&
			tr.getSK().startsWith("EPIC#"); 
	}

	static public MyTableRow.MyTableRowBuilder makeInstrumentRow(String epic) {
		return MyTableRow.builder()//
				.PK("INSTRUMENT")//
				.SK("EPIC#"+epic);
	}

	public static InstrumentReceivedFromDBEvent toInstrumentReceivedFromDBEvent(MyTableRow tr) {
		return InstrumentReceivedFromDBEvent.builder()//
				.description(tr.getDescription())//
				.epic(tr.getEpic())//
				.symbol(tr.getSymbol())//
				.build();
	}
}
