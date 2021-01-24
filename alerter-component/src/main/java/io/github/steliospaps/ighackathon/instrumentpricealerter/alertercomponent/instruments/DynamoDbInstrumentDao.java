package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.instruments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRowUtil;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;

@Component
public class DynamoDbInstrumentDao implements InstrumentDao {
	@Autowired
	private DynamoDBMapper dynamoDBMapper;

	@Override
	public void insert(InstrumentReceivedFromIGEvent instrument) {
		MyTableRow row =  MyTableRowUtil.makeInstrumentRow(instrument.getEpic())//
				.epic(instrument.getEpic())//
				.symbol(instrument.getSymbol())//
				.description(instrument.getDescription())//
				.build();
		dynamoDBMapper.save(row);
	}

	@Override
	public void delete(String epic) {
		dynamoDBMapper.delete(MyTableRowUtil.makeInstrumentRow(epic)//
				.build());
		
	}

}
