package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb;

import lombok.Data;

@Data
public class TriggerFields {
	private String epic;
    private Direction direction;
    //@JsonFormat(shape=JsonFormat.Shape.STRING,pattern = "%,d")
    //private BigDecimal price;
    /**
     * string because cannot easily deal with thousands separators in input.
     * TODO:// sanitise input on lambda and pass this as number
     */
    private String price; 
    public enum Direction {
    	OVER, UNDER;
    }
}
