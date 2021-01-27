package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.prevdaychange.Orderer;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting.prevdaychange.Orderer.Order;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRowUtil;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent.Meta;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEventWrapper;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields.Direction;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.PriceUpdateEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode.Exclude;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * TODO: split into 3 components epicLookup, price_alert, prevDayChange
 * @author stelios
 *
 */
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
	private ConcurrentMap<String, Disposable> disposablesByPK = new ConcurrentHashMap<>();
	private ConcurrentMap<String, Set<Subscription>> subscriptionsByEpic = new ConcurrentHashMap<>();

	private ConcurrentMap<String, InstrumentReceivedFromIGEvent> epicCache = new ConcurrentHashMap<>();
	private Counter epicPriceTriggeredCounter=Metrics.counter("alerts.epic-price.triggered.count");
	private Counter prevDayChangeTriggeredCounter=Metrics.counter("alerts.prev-day-change.triggered.count");
	{
		Metrics.gauge("alerts.epic-price.subscriptions.count", subscriptionsByPK, c->c.size());
		Metrics.gauge("alerts.prev-day-change.subscriptions.count", disposablesByPK, c->c.size());
	}
	
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
		log.info("onNewTrigger triggerType={} {}",triggerType,triggerType.toUpperCase());
		TriggerType type = TriggerType.valueOf(triggerType.toUpperCase());

		if(type == TriggerType.PREV_DAY_CHANGE) {
			addNetDayChangeSubscription(pk, type);
		} else if(type == TriggerType.INSTRUMENT_PRICE) {
			addInstrumentPriceSubscription(pk, tf, hasFired, type);
		} else {
			log.error("invalid triggerType={}",triggerType);
		}
	}
	
	private void addInstrumentPriceSubscription(String pk, TriggerFields tf, boolean hasFired, TriggerType type) {
		Subscription sub;
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
			log.warn("addInstrumentPriceSubscription - subscription for pk={} already there. will replace", pk);
		}
		boolean isNew=subscriptionsByEpic
				.computeIfAbsent(sub.getEpic(), epic -> new CopyOnWriteArraySet<Subscription>()).add(sub);
		if (!isNew) {
			log.warn("addInstrumentPriceSubscription - subscription for pk={} on subscriptionsByEpic already there. will replace", pk);
		}
	}

	private void addNetDayChangeSubscription(String pk, TriggerType type) {
		Subscription sub;
		sub = Subscription.builder()//
				.triggerType(type)//
				.pk(pk)//
				.build();
		//this will alert on trigger added and on restart
		//TODO: stop it from alerting on restart (load last sent alert?)
		Disposable disposable = prevDayFlux//
			.sampleFirst(input -> Flux.interval(activeInstrumentsDuration))// emit first and then nothing for activeInstrumentsDuration
			.distinct(orderTup -> orderTup.mapT1(order ->new HashSet<>(order.getItems())))// as long as order of items does not change we do not care
			.log("netDayChange-"+pk)//
			.map(Tuple2::getT1)//
			.subscribe(order->{
				for(int i=0;i<order.getItems().size();i++) {
					triggerNetDayChangedAlert(sub.getPk(), TriggerEvent.builder()//
						.instrument(order.getItems().get(i).getSymbol())//
						.instrumentName(order.getItems().get(i).getDescription())//
						.price(order.getValues().get(i).toPlainString())//
						.meta(Meta.builder()//
								//make a predictable id so that we do not alert more than once a day for an epic
								.id(LocalDate.now()+"-"+order.getItems().get(i).getSymbol())
								.timestamp(Instant.now().getEpochSecond()).build())
						.build());
				}
			});
		
		Disposable old = disposablesByPK.put(pk, disposable);
		if (old != null) {
			log.warn("addNetDayChangeSubscription - subscription for pk={} already there. will replace", pk);
			old.dispose();
		}
	}

	@Scheduled(cron = "${app.alerter.price.net-day-change.reset.cron}")
	public void resetNetDayChange() {
		log.info("resetNetDayChange (also called on startup)");
		prevDayChangeStartSendingAlertsOnTicksAfter.set(LocalDateTime.now().plus(activeInstrumentsDayStartIgnoreDuration));
		prevDayAlertPeriod.incrementAndGet();
		log.info("resetNetDayChange - will suppress alerts until {}",prevDayChangeStartSendingAlertsOnTicksAfter.get());
	}
	
	@Override
	public void onDeleteTrigger(String pk) {
		Subscription sub = subscriptionsByPK.remove(pk);
		if (sub == null) {
			Disposable disposable = disposablesByPK.remove(pk);
			if(disposable == null) {
				log.warn("onDeleteTrigger subscription for pk={} not found", pk);
				return;
			} else {
				disposable.dispose();
			}
		}
		if(sub.getTriggerType() == TriggerType.PREV_DAY_CHANGE) {
			log.warn("onDeleteTrigger - found wrong triggerType will ignore: {}",sub);
		} else {
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
		log.debug("onInstrument {}", instr);
		epicCache.put(instr.getEpic(), instr);
	}

	@EventListener
	public void onPriceTick(PriceUpdateEvent tick) {
		log.debug("onPriceTick {}", tick);
		subscriptionsByEpic.getOrDefault(tick.getEpic(), Set.of())//
				.stream()//
				.forEach(sub ->handleEpicPriceTrigger(tick, sub));
		
		FluxSink<PriceUpdateEvent> fluxSink = fluxSinkRef.get();
		if(fluxSink==null) {
			log.error("fluxSink is null");
		} else {
			fluxSink.next(tick);
		}

	}

	private AtomicReference<FluxSink<PriceUpdateEvent>> fluxSinkRef=new AtomicReference<>();
	/**
	 * flux of (instrument,alertPeriod)
	 * alertPeriod is a monotonically increasing number so that we can send the same instrument
	 * on different days (instrument same, period different) when we are using Discreet
	 */
	private Flux<Tuple2<Order<InstrumentReceivedFromIGEvent>, Integer>> prevDayFlux;
	private Disposable prevDayFluxDisposable; 
	//used to define alerting periods. On reset the busines period changed and the leaderboard resets
	private AtomicInteger prevDayAlertPeriod=new AtomicInteger(); 
	private AtomicReference<LocalDateTime> prevDayChangeStartSendingAlertsOnTicksAfter=new AtomicReference<>();
	
	@Value("${app.alerter.price.net-day-change.max-instruments-count}")
	private int activeInstrumentsCount;
	//sample the alerts so as to not alert more frequently than this
	@Value("${app.alerter.price.net-day-change.alert-inteval}")
	private Duration activeInstrumentsDuration;
	//on period start mute alerts for so long
	@Value("${app.alerter.price.net-day-change.day-start-inteval}")
	private Duration activeInstrumentsDayStartIgnoreDuration;
	
	@PreDestroy
	private void destroyPrevDayChangedFlux() {
		prevDayFluxDisposable.dispose();
	}
	
	@PostConstruct
	private void initialisePrevDayChangedFlux() {
		resetNetDayChange();
		
		Orderer<InstrumentReceivedFromIGEvent> orderer = 
				new Orderer<InstrumentReceivedFromIGEvent>((a,b)->a.abs().compareTo(b.abs()));
		
		ConnectableFlux<Tuple2<Order<InstrumentReceivedFromIGEvent>, Integer>> connectable = 
				Flux.<PriceUpdateEvent>create(fluxSink -> {
			if(!fluxSinkRef.compareAndSet(null, fluxSink)) {
				log.error("I expected the fluxSink to not be set, but it is");
			}
		})//
		.map(tick->enrichWithInstrument(tick))
		.scan(Tuples.of(new Orderer.Order<InstrumentReceivedFromIGEvent>(),prevDayAlertPeriod.get()), //
				(orderTup,tup2)-> {
					int currentPeriod = prevDayAlertPeriod.get();
					if(orderTup.getT2() == currentPeriod) {
						return Tuples.of(orderer.tick(orderTup.getT1(), tup2.getT1(), tup2.getT2()),orderTup.getT2());
					} else {
						log.info("reseting prevDayChange (in scan)");
						return Tuples.of(orderer.tick(new Orderer.Order<InstrumentReceivedFromIGEvent>(), 
								tup2.getT1(), tup2.getT2()),currentPeriod);
					}
				})//
		.map(orderTup -> orderTup.mapT1(o -> o.limitSizeTo(activeInstrumentsCount)))//
		.filter(ignored -> LocalDateTime.now().isAfter(prevDayChangeStartSendingAlertsOnTicksAfter.get()))
		.log("prevDayFlux",Level.INFO, SignalType.AFTER_TERMINATE,SignalType.CANCEL,SignalType.ON_COMPLETE,
				SignalType.ON_ERROR,SignalType.ON_SUBSCRIBE,SignalType.SUBSCRIBE)
		.publish();
		
		prevDayFlux=connectable.cache(1);
		prevDayFluxDisposable = connectable.connect();
	}

	private Tuple2<InstrumentReceivedFromIGEvent, BigDecimal> enrichWithInstrument(PriceUpdateEvent tick) {
		InstrumentReceivedFromIGEvent instrument = epicCache.get(tick.getEpic());
		if(instrument==null) {
			log.error("could not find instrument {} will use dummy",tick.getEpic());
			instrument = InstrumentReceivedFromIGEvent.builder()//
					.epic(tick.getEpic())//
					.symbol("[N/A]")//
					.description("[N/A]")//
					.build();
		}
		return Tuples.of(instrument, tick.getNetChgPrevDay());
	}

	/**
	 * TODO: on restart we lose the state so we will always retrigger on restart. Make it preserve state and load
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
		prevDayChangeTriggeredCounter.increment();
		MyTableRow row = MyTableRowUtil.makeTriggerEventRow(pk)
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
		epicPriceTriggeredCounter.increment();
		MyTableRow toUpdate = new MyTableRow();
		toUpdate.setPK(pk);
		toUpdate.setSK(pk);// same for trigger entity
		toUpdate.setTriggerEvents(jaxbMapper.writeValueAsString(List.of(te)));
		dynamoDBMapper.save(toUpdate);// this updates non null fields (SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)

	}

}
