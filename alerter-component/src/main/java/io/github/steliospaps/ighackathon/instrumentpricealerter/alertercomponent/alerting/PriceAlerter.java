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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import lombok.Data;
import lombok.EqualsAndHashCode.Exclude;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Component
@Slf4j
@ConditionalOnProperty(name="app.alerter.dummy.enabled",havingValue = "false", matchIfMissing = true)
public class PriceAlerter implements Alerter{

	private ConcurrentMap<String, Subscription> subscriptionsByPK=new ConcurrentHashMap<>(); 
	private ConcurrentMap<String, Set<Subscription>> subscriptionsByEpic=new ConcurrentHashMap<>(); 
	private ConcurrentMap<String, InstrumentReceivedFromIGEvent> epicCache =new ConcurrentHashMap<>();
	
	@Builder
	@Data
	@AllArgsConstructor
	private static class Subscription{
		private final String epic;
		@Exclude//ignore in equals and hashcode
		private volatile boolean isArmed;
		private final BigDecimal targetPrice;
		private final boolean isOver;
		private final String pk;
	}
	
	@Autowired
	private DynamoDBMapper dynamoDBMapper;
	
	@Autowired
	private ObjectMapper jaxbMapper;
		
	@Override
	public void onNewTrigger(String pk, TriggerFields tf, boolean hasFired) {
		Subscription sub = Subscription.builder().epic(tf.getEpic())//
			.targetPrice(new BigDecimal(tf.getPrice())) //TODO: deal with this being bad? (in the api lambda?+here?)
			.isArmed(!hasFired)// will fire on the first matching price if not fired before
			//this is to avoid refiring on startup
			.isOver(tf.getDirection()==Direction.OVER)
			.pk(pk)//
			.build();
		Subscription old = subscriptionsByPK.put(pk, sub);
		if(old != null) {
			log.warn("onNewTrigger - subscription for pk={} already there. will replace",pk);
		}
		boolean isNew = subscriptionsByEpic.computeIfAbsent(sub.getEpic(), epic ->new CopyOnWriteArraySet<Subscription>())
			.add(sub);
		if(!isNew) {
			log.warn("onNewTrigger - subscription for pk={} on epic already there. will replace",pk);			
		}
	}

	@Override
	public void onDeleteTrigger(String pk) {
		Subscription sub = subscriptionsByPK.remove(pk);
		if(sub==null) {
			log.warn("onDeleteTrigger subscription for pk={} not found",pk);
			return;
		}
		Set<Subscription> set = subscriptionsByEpic.get(sub.getEpic());
		if(set == null) {
			log.warn("onDeleteTrigger for pk={} epic={} has no subscriptions",pk,sub.getEpic());
			return;
		}
		if(!set.remove(sub)) {
			log.warn("onDeleteTrigger for pk={} epic={} : could not find the subscription."
					+ " Will not remove anything",pk,sub.getEpic());
		}
	}
	@EventListener
	public void onInstrument(InstrumentReceivedFromIGEvent instr) {
		epicCache.put(instr.getEpic(),instr);
	}
	
	@EventListener
	public void onPriceTick(PriceUpdateEvent tick) {
		log.info("onPriceTick {}",tick);
		subscriptionsByEpic.getOrDefault(tick.getEpic(), Set.of())//
			.stream()//
			.forEach(sub -> {
				//TODO: this can be optimized for speed (order subs like an order book so that only the top ones need to be checked)
				// construct the payloads only once
				// effect of setting the volatile? (will it flush memory?)
				// 
				if(sub.isOver()) {
					if(sub.getTargetPrice().compareTo(tick.getOffer())<0) {
						if(sub.isArmed()) {
							Optional<InstrumentReceivedFromIGEvent> instr = Optional.ofNullable(epicCache.get(tick.getEpic()));
							triggerAlert(sub.getPk(), TriggerEventWrapper.builder()
									.seqNo(1L)//
									.data(TriggerEvent.builder()//
											.instrument(instr.map(i->i.getSymbol()).orElse("[N/A]"))//
											.instrumentName(instr.map(i->i.getDescription()).orElse("[N/A]"))//
											.price(tick.getOffer().toPlainString())//
											.meta(Meta.builder()//
													.id(UUID.randomUUID().toString())//TODO: come from bid/offer id?
													.timestamp(Instant.now().getEpochSecond())
													.build())
											.build())//
									.build());
							sub.setArmed(false);
						} else {
							sub.setArmed(true);
						}
					}
				} else {
					if(sub.getTargetPrice().compareTo(tick.getBid())>0) {
						if(sub.isArmed()) {
							Optional<InstrumentReceivedFromIGEvent> instr = Optional.ofNullable(epicCache.get(tick.getEpic()));
							triggerAlert(sub.getPk(), TriggerEventWrapper.builder()
									.seqNo(1L)//
									.data(TriggerEvent.builder()//
											.instrument(instr.map(i->i.getSymbol()).orElse("[N/A]"))//
											.instrumentName(instr.map(i->i.getDescription()).orElse("[N/A]"))//
											.price(tick.getBid().toPlainString())//
											.meta(Meta.builder()//
													.id(UUID.randomUUID().toString())//TODO: come from bid/offer id?
													.timestamp(Instant.now().getEpochSecond())
													.build())
											.build())//
									.build());
							sub.setArmed(false);
						} else {
							sub.setArmed(true);
						}
					}

				}
			});
	}
	
	@SneakyThrows
	private void triggerAlert(String pk, TriggerEventWrapper te) {
		//TODO: race condition this can end up inserting (via update) when the delete has not arrived yet
		// This can be addressed by changing the model to use a TTL field that gets set by the deletion
		// instead of deleting 
		log.info("triggerAlert - pk={} te={}",pk,te);
		MyTableRow toUpdate = new MyTableRow();
		toUpdate.setPK(pk);
		toUpdate.setSK(pk);//same for trigger entity
		toUpdate.setTriggerEvents(jaxbMapper.writeValueAsString(List.of(te)));
		dynamoDBMapper.save(toUpdate);//this updates non null fields (SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)

	}

}
