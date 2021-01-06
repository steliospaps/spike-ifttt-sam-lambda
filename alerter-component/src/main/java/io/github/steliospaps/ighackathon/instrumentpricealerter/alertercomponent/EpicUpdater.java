package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EpicUpdater {
	@Autowired
	private DynamoDBMapper dynamoDBMapper;
	@EventListener
	public void onInstrument(InstrumentReceivedFromIGEvent instr) {
		log.info("onInstrument {}",instr);
		//TODO: make this write only if the table row not there (and delete non existent?)
		// perhaps on an event LastFragment received?
		MyTableRow row = MyTableRow.builder().epic(instr.getEpic())//
				.symbol(instr.getSymbol())//
				.description(instr.getDescription())//
				.PK("INSTRUMENT")//
				.SK("EPIC#"+instr.getEpic())//
				.build();
		dynamoDBMapper.save(row);
	}
}
