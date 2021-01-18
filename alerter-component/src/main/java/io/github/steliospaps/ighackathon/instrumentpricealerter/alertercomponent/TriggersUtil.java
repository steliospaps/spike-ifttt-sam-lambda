package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;

public class TriggersUtil {
	private TriggersUtil() {
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
}
