package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;

/**
 * a registry for triggers
 * @author stelios
 *
 */
public interface Alerter {
	/**
	 * 
	 * @param pk trigger id
	 * @param tf trigger fields
	 */
	void onNewTrigger(String pk, TriggerFields tf);
	/**
	 * During startup this might be called before onNewTrigger
	 * @param pk trigger id
	 */
	void onDeleteTrigger(String pk);

}
