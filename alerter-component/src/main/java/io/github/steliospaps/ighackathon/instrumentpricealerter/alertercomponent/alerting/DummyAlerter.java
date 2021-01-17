package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.alerting;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.TriggersUtil;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.MyTableRow;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEvent.Meta;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerEventWrapper;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.dynamodb.TriggerFields;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Component
@Slf4j
@ConditionalOnProperty("app.alerter.dummy.enabled")
public class DummyAlerter implements Alerter{

	private ConcurrentMap<String, Disposable> subscriptions=new ConcurrentHashMap<>(); 
	
	@Autowired
	private DynamoDBMapper dynamoDBMapper;
	
	@Autowired
	private ObjectMapper jaxbMapper;
	
	@Value("${app.alerter.dummy.initial-delay}")
	private Duration initialDelay;
	@Value("${app.alerter.dummy.interval}")
	private Duration interval;
	
	@Override
	public void onNewTrigger(String pk, TriggerFields tf, boolean hasFired, String triggerType) {
		Disposable d1 = Flux.interval(interval)//
			.map(i->i+1)//
			.startWith(0L)//
			.delaySubscription(initialDelay)//
			.map(i -> 
				TriggerEventWrapper.builder()
					.seqNo(i)//
					.data(TriggerEvent.builder()//
						.instrument(tf.getEpic())//
						.instrumentName("nameOf-"+tf.getEpic())//
						.price(""+(1000+i))//
						.meta(Meta.builder()//
								.id(UUID.randomUUID().toString())//
								.timestamp(Instant.now().getEpochSecond())//
								.build()
								)//
						.build())//
						.build()//
						)
			.log("pk="+pk)
			.subscribe(te -> {
				if(triggerType.equals("instrument_price")) {
					triggerInstrumentPriceAlert(pk, te);
				}else {
					triggerPrevDayChangeAlert(pk, te);
				}
			});
		Disposable old = subscriptions.put(pk, d1);
		if(old != null) {
			log.warn("onNewTrigger - disposable for pk={} already there. will dispose",pk);
			old.dispose();
		}
		
	}

	@Override
	public void onDeleteTrigger(String pk) {
		// TODO race condition: this can be called before the onNew
		Disposable disposable = subscriptions.get(pk);
		if(disposable !=null) {
			disposable.dispose();
		} else {
			log.warn("onDeleteTrigger - no disposable found for pk="+pk);
		}
	}
	
	@SneakyThrows
	private void triggerInstrumentPriceAlert(String pk, TriggerEventWrapper te) {
		//TODO: race condition this can end up inserting (via update) when the delete has not arrived yet
		// This can be addressed by changing the model to use a TTL field that gets set by the deletion
		// instead of deleting 
		log.info("triggerInstrumentPriceAlert - pk={} te={}",pk,te);
		MyTableRow toUpdate = new MyTableRow();
		toUpdate.setPK(pk);
		toUpdate.setSK(pk);//same for trigger entity
		toUpdate.setTriggerEvents(jaxbMapper.writeValueAsString(List.of(te)));
		dynamoDBMapper.save(toUpdate);//this updates non null fields (SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)

	}
	@SneakyThrows
	private void triggerPrevDayChangeAlert(String pk, TriggerEventWrapper te) {
		log.info("triggerPrevDayChangeAlert - pk={} te={}",pk,te);
		MyTableRow row = TriggersUtil.makeTriggerEventRow(pk)
				.triggerEvent(jaxbMapper.writeValueAsString(te.getData()))//
				.build();
		dynamoDBMapper.save(row);
	}

}
