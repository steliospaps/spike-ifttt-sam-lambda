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
	 * @param hasFired true if it has ever fired
	 * @param triggerType TODO
	 */
	void onNewTrigger(String pk, TriggerFields tf, boolean hasFired, String triggerType);
	/**
	 * During startup this might be called before onNewTrigger
	 * @param pk trigger id
	 */
	void onDeleteTrigger(String pk);

}
