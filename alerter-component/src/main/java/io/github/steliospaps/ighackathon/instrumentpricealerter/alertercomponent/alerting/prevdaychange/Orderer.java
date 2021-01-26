package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.prevdaychange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * maintain a limited descending order of items based a big decimal value
 * 
 * @author stelios
 *
 * @param <T> the type of the items
 */
public class Orderer<T> {
	private Comparator<BigDecimal> comparator;

	@AllArgsConstructor
	@NoArgsConstructor
	@Data
	@Setter(value = AccessLevel.NONE)
	public static class Order<T> {
		List<T> items = List.of();
		List<BigDecimal> values = List.of();
		public Order<T> limitSizeTo(int i) {
			if(i>=items.size()) {
				return this;
			} else {
				return new Order<>(items.subList(0, i),values.subList(0, i));
			}
		}
	}

	public Orderer() {
		this.comparator = BigDecimal::compareTo;
	}

	public Orderer(Comparator<BigDecimal> comparator) {
		this.comparator = comparator;
	}

	public Order<T> tick(Order<T> original, T item, BigDecimal value) {
		int found = original.getItems().indexOf(item);

		if (found != -1 && comparator.compare(original.getValues().get(found),value) >= 0) {
			return original;// nothing to do
		}

		var items = new ArrayList<>(original.getItems());
		var values = new ArrayList<>(original.getValues());

		if (found != -1) {
			//found and value changed for the better
			items.remove(found);
			values.remove(found);
		}

		// to be here we have to insert
		for (int i = 0; i < original.getValues().size(); i++) {
			if (comparator.compare(value,original.getValues().get(i)) > 0) {
				return insertBefore(i, items, values, item, value);
			}
		}
		return insertBefore(original.getValues().size(), items, values, item, value);
	}

	private Order<T> insertBefore(int i, ArrayList<T>items, ArrayList<BigDecimal> values, T item, BigDecimal value) {
		items.add(i, item);
		values.add(i, value);
		return new Order<>(items, values);
	}
}