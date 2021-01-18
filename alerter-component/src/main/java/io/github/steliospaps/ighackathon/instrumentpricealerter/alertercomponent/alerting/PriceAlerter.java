package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.TriggersUtil;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.PriceAlerter.PrevDayChangeState;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.PriceAlerter.TriggerType;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent.Meta;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEventWrapper;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields.Direction;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.PriceUpdateEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode.Exclude;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.alerter.dummy.enabled", havingValue = "false", matchIfMissing = true)
public class PriceAlerter implements Alerter {

	/**
	 * data specific for prev day change trigger
	 * @author stelios
	 *
	 */
	@Builder
	@Data
	@AllArgsConstructor
	public static class PrevDayChangeState {
		private String epicSent;
		private BigDecimal prevDayChangeSentValue;
	}

	public enum TriggerType {
		PREV_DAY_CHANGE, INSTRUMENT_PRICE;
	}

	private ConcurrentMap<String, Subscription> subscriptionsByPK = new ConcurrentHashMap<>();
	private ConcurrentMap<String, Set<Subscription>> subscriptionsByEpic = new ConcurrentHashMap<>();
	private Set<Subscription> subscriptionsForNetDayChange = ConcurrentHashMap.newKeySet();
	private ConcurrentMap<String, InstrumentReceivedFromIGEvent> epicCache = new ConcurrentHashMap<>();

	@Builder
	@Data
	@AllArgsConstructor
	private static class Subscription {
		private final String epic;
		@Exclude // ignore in equals and hashcode
		private volatile boolean isArmed;
		@Default
		@Exclude // ignore in equals and hashcode
		private AtomicReference<PrevDayChangeState> prevDayChangeState = new AtomicReference();
		private final BigDecimal targetPrice;
		private final boolean isOver;
		private final String pk;
		private final TriggerType triggerType;
	}

	@Autowired
	private DynamoDBMapper dynamoDBMapper;

	@Autowired
	private ObjectMapper jaxbMapper;

	@Override
	public void onNewTrigger(String pk, TriggerFields tf, boolean hasFired, String triggerType) {
		log.info("triggerType={} {}",triggerType,triggerType.toUpperCase());
		TriggerType type = TriggerType.valueOf(triggerType.toUpperCase());
		Subscription sub;
		if(type == TriggerType.PREV_DAY_CHANGE) {
			sub = Subscription.builder()//
				.triggerType(type)//
				.pk(pk)//
				.build();
		Subscription old = subscriptionsByPK.put(pk, sub);
		if (old != null) {
			log.warn("onNewTrigger - subscription for pk={} already there. will replace", pk);
		}
		boolean isNew;
			isNew = subscriptionsForNetDayChange.add(sub);
		if (!isNew) {
			log.warn("onNewTrigger - subscription for pk={} on subscriptionsForNetDayChange already there. will replace", pk);
		}
		} else {
			sub = Subscription.builder().epic(tf.getEpic())//
					.targetPrice(new BigDecimal(tf.getPrice().replaceAll(",", ""))) // TODO: deal with this being bad? (in the api
																// lambda?+here?)
					.isArmed(!hasFired)// will fire on the first matching price if not fired before
					// this is to avoid refiring on startup
					.isOver(tf.getDirection() == Direction.OVER)
					.pk(pk)//
					.triggerType(type)
					.build();
			Subscription old = subscriptionsByPK.put(pk, sub);
			if (old != null) {
				log.warn("onNewTrigger - subscription for pk={} already there. will replace", pk);
			}
			boolean isNew=subscriptionsByEpic
					.computeIfAbsent(sub.getEpic(), epic -> new CopyOnWriteArraySet<Subscription>()).add(sub);
			if (!isNew) {
				log.warn("onNewTrigger - subscription for pk={} on subscriptionsByEpic already there. will replace", pk);
			}
		}
	}

	@Scheduled(cron = "${app.alerter.price.net-day-change.reset.cron}")
	public void resetNetDayChange() {
		log.info("resetNetDayChange");
		subscriptionsForNetDayChange.stream().forEach(sub -> sub.getPrevDayChangeState().set(null));
	}
	
	@Override
	public void onDeleteTrigger(String pk) {
		Subscription sub = subscriptionsByPK.remove(pk);
		if (sub == null) {
			log.warn("onDeleteTrigger subscription for pk={} not found", pk);
			return;
		}
		if(sub.getTriggerType() == TriggerType.PREV_DAY_CHANGE) {
			if (!subscriptionsForNetDayChange.remove(sub)) {
				log.warn("onDeleteTrigger for pk={} epic={} : could not find the subscription in subscriptionsForNetDayChange."
						+ " Will not remove anything", pk, sub.getEpic());
			}
		}else {
		Set<Subscription> set = subscriptionsByEpic.get(sub.getEpic());
		if (set == null) {
			log.warn("onDeleteTrigger for pk={} epic={} has no subscriptions", pk, sub.getEpic());
			return;
		}
		if (!set.remove(sub)) {
			log.warn("onDeleteTrigger for pk={} epic={} : could not find the subscription in subscriptionsByEpic."
					+ " Will not remove anything", pk, sub.getEpic());
		}
		}
	}

	@EventListener
	public void onInstrument(InstrumentReceivedFromIGEvent instr) {
		log.info("onInstrument {}", instr);
		epicCache.put(instr.getEpic(), instr);
	}

	@EventListener
	public void onPriceTick(PriceUpdateEvent tick) {
		log.debug("onPriceTick {}", tick);
		subscriptionsByEpic.getOrDefault(tick.getEpic(), Set.of())//
				.stream()//
				.forEach(sub ->handleEpicPriceTrigger(tick, sub));
		subscriptionsForNetDayChange.stream()//
		.forEach(sub -> handleDayChangedTrigger(tick, sub));

	}

	private void handleDayChangedTrigger(PriceUpdateEvent tick, Subscription sub) {
		//TODO: optimize for cost: Update a single record and have all the triggers check it
		if(hasNotBeenTriggeredBefore(sub) // first check
				|| (netDayChangeShouldFire(tick, sub) 
						&& notSentThisEpicLast(tick, sub) 
						)) {
			
			//TODO: make this trigger specific type
			Optional<InstrumentReceivedFromIGEvent> instr = Optional
					.ofNullable(epicCache.get(tick.getEpic()));
			if(!instr.isPresent()){
				log.warn("handleDayChangedTrigger - could not find instrument for epic {}",tick.getEpic());
			}
			triggerNetDayChangedAlert(sub.getPk(), TriggerEvent.builder()//
							.instrument(instr.map(i -> i.getSymbol()).orElse("[N/A]"))//
							.instrumentName(instr.map(i -> i.getDescription()).orElse("[N/A]"))//
							.price(tick.getNetChgPrevDay().toPlainString())//
							.meta(Meta.builder()//
									.id(UUID.randomUUID().toString())// TODO: come from bid/offer
																		// id?
									.timestamp(Instant.now().getEpochSecond()).build())
							.build());
			sub.getPrevDayChangeState().set(PrevDayChangeState.builder()//
					.epicSent(tick.getEpic())//
					.prevDayChangeSentValue(tick.getNetChgPrevDay())//
					.build());
		}
		
	}

	private boolean notSentThisEpicLast(PriceUpdateEvent tick, Subscription sub) {
		return !tick.getEpic().equals(sub.getPrevDayChangeState().get().getEpicSent());
	}

	private boolean netDayChangeShouldFire(PriceUpdateEvent tick, Subscription sub) {
		return tick.getNetChgPrevDay().abs().compareTo(
				sub.getPrevDayChangeState().get().getPrevDayChangeSentValue().abs())>0;
	}

	/**
	 * TODO: on restart we lose the state so we wioll always retrigger on restart. Make it preserve state and load
	 * @param sub
	 * @return true if subscription has not fired before
	 */
	private boolean hasNotBeenTriggeredBefore(Subscription sub) {
		return sub.getPrevDayChangeState().get()==null;
	}

	private void handleEpicPriceTrigger(PriceUpdateEvent tick, Subscription sub) {
		// TODO: this can be optimized for speed (order subs like an order book so that
		// only the top ones need to be checked)
		// construct the payloads only once
		// effect of setting the volatile? (will it flush memory?)
		//
		if (sub.isOver()) {
			if (sub.getTargetPrice().compareTo(tick.getOffer()) <= 0) {
				if (sub.isArmed()) {
					Optional<InstrumentReceivedFromIGEvent> instr = Optional
							.ofNullable(epicCache.get(tick.getEpic()));
					triggerAlert(sub.getPk(), TriggerEventWrapper.builder().seqNo(1L)//
							.data(TriggerEvent.builder()//
									.instrument(instr.map(i -> i.getSymbol()).orElse("[N/A]"))//
									.instrumentName(instr.map(i -> i.getDescription()).orElse("[N/A]"))//
									.price(tick.getOffer().toPlainString())//
									.meta(Meta.builder()//
											.id(UUID.randomUUID().toString())// TODO: come from bid/offer
																				// id?
											.timestamp(Instant.now().getEpochSecond()).build())
									.build())//
							.build());
					sub.setArmed(false);
				}
			} else if (sub.getTargetPrice().doubleValue() > tick.getOffer().doubleValue() * 0.95) {
				if(!sub.isArmed()) {
					log.info("arming {}", sub);
					sub.setArmed(true);
				}
			}
		} else {
			if (sub.getTargetPrice().compareTo(tick.getBid()) > 0) {
				if (sub.isArmed()) {
					Optional<InstrumentReceivedFromIGEvent> instr = Optional
							.ofNullable(epicCache.get(tick.getEpic()));
					triggerAlert(sub.getPk(), TriggerEventWrapper.builder().seqNo(1L)//
							.data(TriggerEvent.builder()//
									.instrument(instr.map(i -> i.getSymbol()).orElse("[N/A]"))//
									.instrumentName(instr.map(i -> i.getDescription()).orElse("[N/A]"))//
									.price(tick.getBid().toPlainString())//
									.meta(Meta.builder()//
											.id(UUID.randomUUID().toString())// TODO: come from bid/offer
																				// id?
											.timestamp(Instant.now().getEpochSecond()).build())
									.build())//
							.build());
					sub.setArmed(false);
				}
			} else if (sub.getTargetPrice().doubleValue() < tick.getBid().doubleValue() * 0.95) {
				if(!sub.isArmed()) {
					log.info("arming {}", sub);
					sub.setArmed(true);
				}
			}
		}
	}

	@SneakyThrows
	private void triggerNetDayChangedAlert(String pk, TriggerEvent payload) {
		log.info("triggerNetDayChangedAlert - pk={} payload={}", pk, payload);
		MyTableRow row = TriggersUtil.makeTriggerEventRow(pk)
				.triggerEvent(jaxbMapper.writeValueAsString(payload))//
				.build();
		dynamoDBMapper.save(row);
	}

	@SneakyThrows
	private void triggerAlert(String pk, TriggerEventWrapper te) {
		// TODO: race condition this can end up inserting (via update) when the delete
		// has not arrived yet
		// This can be addressed by changing the model to use a TTL field that gets set
		// by the deletion
		// instead of deleting
		log.info("triggerAlert - pk={} te={}", pk, te);
		MyTableRow toUpdate = new MyTableRow();
		toUpdate.setPK(pk);
		toUpdate.setSK(pk);// same for trigger entity
		toUpdate.setTriggerEvents(jaxbMapper.writeValueAsString(List.of(te)));
		dynamoDBMapper.save(toUpdate);// this updates non null fields (SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)

	}

}
