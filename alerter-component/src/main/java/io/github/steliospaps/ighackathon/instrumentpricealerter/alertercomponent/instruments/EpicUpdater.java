package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.instruments;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromDBEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.SecurityListReceivedEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.TableScannedEvent;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * updates the INSTRUMENTs in the DB to match the Data in the SecurityList
 * 
 * @author stelios
 *
 */
@Component
@Slf4j
public class EpicUpdater {
	@Autowired
	private InstrumentDao instrumentDao;

	private volatile boolean securityListReceived = false;

	private volatile boolean dbInstrumentsRead = false;

	@Data
	@NoArgsConstructor
	private static class InstrumentTuple {
		InstrumentReceivedFromIGEvent ig;
		InstrumentReceivedFromDBEvent db;
	}

	private Map<String, InstrumentTuple> instruments = new ConcurrentHashMap<>();

	@EventListener
	public void onIgInstrument(InstrumentReceivedFromIGEvent instrument) {
		log.info("onIgInstrument {}", instrument);
		getInstrumentTuple(instrument.getEpic()).setIg(instrument);
	}

	private InstrumentTuple getInstrumentTuple(String epic) {
		return instruments.computeIfAbsent(epic, k -> new InstrumentTuple());

	}

	@EventListener(SecurityListReceivedEvent.class)
	public void onSecurityListReceived() {
		securityListReceived = true;
		conditionallyReconcile();
	}

	private void conditionallyReconcile() {
		if (dbInstrumentsRead && securityListReceived) {
			instruments.values().stream()//
					.forEach(tup -> {
						if (tup.getIg() != null) {
							if (tup.getDb() == null) {
								log.info("conditionallyReconcile epic={} inserting because no entry in DB",
										tup.getIg().getEpic());
							} else if (!StringUtils.equals(tup.getDb().getSymbol(), tup.getIg().getSymbol())) {
								log.info("conditionallyReconcile epic={} inserting because Symbols differ",
										tup.getIg().getEpic());
							} else if (!StringUtils.equals(tup.getDb().getDescription(),
									tup.getIg().getDescription())) {
								log.info("conditionallyReconcile epic={} inserting because Symbols differ",
										tup.getIg().getEpic());
							} else {
								log.info("conditionallyReconcile epic={} ignoring because identical to DB entry",
										tup.getIg().getEpic());
								return;
							}
							instrumentDao.insert(tup.getIg());
							// set it so that next time we get a security list we do not re-insert
							tup.setDb(InstrumentReceivedFromDBEvent.builder()//
									.symbol(tup.getIg().getSymbol())//
									.description(tup.getIg().getDescription())//
									.epic(tup.getIg().getEpic())//
									.build());
							tup.setIg(null); //so that if it is not received next time we delete it
						} else if (tup.getDb() != null) {
							log.info("conditionallyReconcile epic={} deleting because it is not in the SecList",
									tup.getDb().getEpic());
							instrumentDao.delete(tup.getDb().getEpic());
							tup.setDb(null); // deleted
						}
					});
			securityListReceived = false; // arm for the next time we reconnect
			// TODO: garbage collect? (items without db and sec list entry
		}

	}

	@EventListener(TableScannedEvent.class)
	public void onDBInstrumentsRead() {
		dbInstrumentsRead = true;
		conditionallyReconcile();
	}

	@EventListener
	public void onDbInstrument(InstrumentReceivedFromDBEvent instrument) {
		log.info("onDbInstrument {}", instrument);
		getInstrumentTuple(instrument.getEpic()).setDb(instrument);
	}
}
