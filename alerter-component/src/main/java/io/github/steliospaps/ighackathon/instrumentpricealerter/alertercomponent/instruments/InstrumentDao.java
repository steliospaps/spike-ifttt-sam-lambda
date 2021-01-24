package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.instruments;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;

public interface InstrumentDao {
	/**
	 * TODO on devition make this use it's own VO class
	 * @param instrument
	 */
	void insert(InstrumentReceivedFromIGEvent instrument);

	/**
	 * delete the instrument identified by the epic
	 * @param epic
	 */
	void delete(String epic);

}
