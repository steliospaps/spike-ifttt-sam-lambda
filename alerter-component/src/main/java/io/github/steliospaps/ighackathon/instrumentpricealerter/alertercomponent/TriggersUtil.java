package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;

public class TriggersUtil {
	private TriggersUtil() {
	}
	
	static public boolean isTriggerRecord(MyTableRow tr) {
		return tr.getPK().startsWith("TR#") &&
			(tr.getPK().equals(tr.getSK())); 
	}
}
