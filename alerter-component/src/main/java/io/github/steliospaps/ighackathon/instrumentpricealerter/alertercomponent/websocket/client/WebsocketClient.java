package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.websocket.client;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.annotation.PreDestroy;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ig.orchestrations.fixp.Establish;
import com.ig.orchestrations.fixp.FlowType;
import com.ig.orchestrations.fixp.IgExtensionCredentials;
import com.ig.orchestrations.fixp.Negotiate;
import com.ig.orchestrations.fixp.NegotiationResponse;
import com.ig.orchestrations.fixp.UnsequencedHeartbeat;
import com.ig.orchestrations.us.rfed.fields.SecurityListRequestType;
import com.ig.orchestrations.us.rfed.fields.SecurityRequestResult;
import com.ig.orchestrations.us.rfed.fields.SubscriptionRequestType;
import com.ig.orchestrations.us.rfed.groups.QuotReqGrp;
import com.ig.orchestrations.us.rfed.groups.SecListGrp;
import com.ig.orchestrations.us.rfed.messages.Quote;
import com.ig.orchestrations.us.rfed.messages.QuoteRequest;
import com.ig.orchestrations.us.rfed.messages.SecurityList;
import com.ig.orchestrations.us.rfed.messages.SecurityListRequest;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.Util;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.PriceUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.ws.enabled", matchIfMissing = true)
@DependsOn({"priceAlerter","initialTableScanner","epicUpdater"}) //looks like event listener beans can be created after event publishers
public class WebsocketClient implements HealthIndicator{
	
	@Value("${app.ws.url}")
	private URI url;
	@Value("${app.ws.password}")
	private String password;
	@Value("${app.ws.username}")
	private String username;
	
	@Autowired
	private ApplicationEventPublisher publisher;
	
	private DateTimeFormatter fmt= DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
	
	
	
	@Autowired
	private ObjectMapper mapper;
	private UUID uuid = UUID.randomUUID();
	private Disposable disposable;
	
	private Duration retryConnectionInterval= Duration.ofSeconds(30);
	private Duration resetConnectionInterval = Duration.ofHours(8);
	private Duration heartBeatInterval = Duration.ofSeconds(20);
	private volatile boolean connected = false;
	private final AtomicInteger wsMessageCount = new AtomicInteger(0);
	
	@EventListener(ApplicationReadyEvent.class)
	public void run() {
		log.info("url={}",url);
		log.info("username={}",username);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		disposable = Flux.interval(retryConnectionInterval)//reconnect that often
			.startWith(-1L)
			.onBackpressureDrop()//otherwise it will blow up
			.flatMap(ignore-> client.execute(url, ws ->handleWs(ws)),//
					1)//how many concurrent connections
			.onErrorContinue((t, o)-> log.error("will retry to connect", t))//
			.take(resetConnectionInterval)//disconnect every 8 hours (and resubscribe)
			.log("ws",Level.INFO,SignalType.ON_SUBSCRIBE,SignalType.CANCEL,SignalType.ON_COMPLETE,SignalType.ON_ERROR,SignalType.AFTER_TERMINATE)
			.subscribe();
	}
	
	@PreDestroy
	public void shutdown() {
		disposable.dispose();
	}

	private Mono<Void> handleWs(WebSocketSession ws) {
		return ws.send(ws.receive()
				.map(WebSocketMessage::getPayloadAsText)//
				.doOnNext(s -> wsMessageCount.incrementAndGet())
				.log("ws-input",Level.FINEST,SignalType.ON_NEXT)//
				.log("ws-input",Level.INFO,SignalType.ON_SUBSCRIBE,SignalType.CANCEL,SignalType.ON_COMPLETE,SignalType.ON_ERROR,SignalType.AFTER_TERMINATE)//
				.map(Util.sneakyF(str -> mapper.readTree(str)))//
				.transform(flux -> jsonFluxHandler(flux))//
				.log("ws-output",Level.FINEST,SignalType.ON_NEXT)//
				.log("ws-output",Level.INFO,SignalType.ON_SUBSCRIBE,SignalType.CANCEL,SignalType.ON_COMPLETE,SignalType.ON_ERROR,SignalType.AFTER_TERMINATE)//
				.map(toSend->ws.textMessage(toSend)))
				.doOnTerminate(() -> log.warn("will retry connection in {} seconds", retryConnectionInterval.getSeconds()))
				.doFinally(s -> connected=false)
				;
	}

	private Flux<String> jsonFluxHandler(Flux<JsonNode> flux) {
		
		return flux.flatMap(Util.sneakyF(jsonNode ->{
			log.debug("received message:", jsonNode);
			String messageType = getMessageType(jsonNode);
			
			log.debug("messageType: {}",messageType);
			
			switch(messageType) {
			case "NegotiationResponse":
				log.info("will establish");
				return Flux.just(new Establish(uuid, (System.currentTimeMillis()*1_000_000L),
						heartBeatInterval.toMillis()+10_000L, null));
			case "EstablishmentAck":
				connected=true;
				return makeSecurityListRequest();
			case "SecurityList":
				return handleSecurityList(mapper.treeToValue(jsonNode, SecurityList.class));
			case "Quote":
				publishPrices(mapper.treeToValue(jsonNode, Quote.class));
				return Flux.empty();
			case "NegotiationReject":
				log.warn("Session failed to logon, reason={}", jsonNode.get("Reason"));
				return Flux.empty();
			default:
				log.info("ignoring msg={}",jsonNode);
				return Flux.empty();
			}
		}))//
			.startWith(loginMessage())//
			.switchMap(msg -> Flux.interval(heartBeatInterval)//
					.<Object>map(i -> new UnsequencedHeartbeat())//
					.startWith(msg))
			.map(Util.sneakyF(msg -> toJsonEnriched(msg)))
			;	
	}

	private void publishPrices(Quote quote) {
		publisher.publishEvent(PriceUpdateEvent.builder()
				.epic(quote.getQuoteReqID())//sending epic getting back epic saves us from having to keep a local map
				.bid(quote.getBidPx())//
				.offer(quote.getOfferPx())//
				.netChgPrevDay(quote.getNetChgPrevDay())//
				.build());
	}

	private String toJsonEnriched(Object msg) throws JsonProcessingException {
		Class<? extends Object> cls = msg.getClass();
			String messageName = cls.getSimpleName();
			ObjectNode json = (ObjectNode) mapper.valueToTree(msg);
		if(cls.getPackageName().equals(Negotiate.class.getPackageName())) {
			json.put("MessageType",messageName);
		} else if(cls.getPackageName().equals(SecurityListRequest.class.getPackageName())) {
			json.put("MsgType",messageName);
			json.put("SendingTime", ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)//
					.format(fmt));
			json.put("ApplVerID", "FIX50SP2");
		}
		return mapper.writeValueAsString(json);
	}

	private Publisher<? extends Object> handleSecurityList(SecurityList securityList) {
		log.info("got securityList");
		if(securityList.getSecurityRequestResult() != SecurityRequestResult.VALID_REQUEST) {
			log.error("bad SecurityRequestResult will error SecurityRequestResult={}",securityList.getSecurityRequestResult());
			return Flux.error(new RuntimeException("securityListRequest rejected"));
		}
		
		securityList.getSecListGrp().stream()//
			.forEach(sec -> publishInstrument(sec));
		
		return Flux.fromStream(securityList.getSecListGrp().stream()//
				.map(sec -> makeQuoteRequest(sec)));
	}

	private void publishInstrument(SecListGrp sec) {
		log.info("publishInstrument - {}", ReflectionToStringBuilder.toString(sec));
		publisher.publishEvent(InstrumentReceivedFromIGEvent.builder()//
				.symbol(sec.getSymbol())//
				.epic(sec.getSecurityID())//
				.description(sec.getSecurityDesc())//
				.build());
	}

	private QuoteRequest makeQuoteRequest(SecListGrp sec) {
		QuoteRequest qr = new QuoteRequest();
		qr.setQuoteReqID(sec.getSecurityID());
		qr.setSubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_AND_UPDATES);
		QuotReqGrp req = new QuotReqGrp();
		req.setSecurityID(sec.getSecurityID());
		req.setSecurityIDSource(sec.getSecurityIDSource());
		qr.setQuotReqGrp(List.of(req));
		return qr;
	}

	private Publisher<? extends Object> makeSecurityListRequest() {
		log.info("will request SecurityList");
		SecurityListRequest req = new SecurityListRequest();
		req.setSecurityReqID("req-1");
		req.setSecurityListRequestType(SecurityListRequestType.ALL_SECURITIES);
		req.setSubscriptionRequestType(SubscriptionRequestType.SNAPSHOT);
		return Flux.just(req);
	}

	private String getMessageType(JsonNode jsonNode) {
		JsonNode node = jsonNode.get("MessageType");
		if(node!=null) {
			log.info("session-level: {}",jsonNode);
			return node.asText();
		}
		node = jsonNode.get("MsgType");
		if(node!=null) {
			return node.asText();
		}
		log.error("unexpected type in {}",jsonNode);
		return "unexpected";
	}

	private Object loginMessage() {
		return new Negotiate(uuid,(System.currentTimeMillis()*1_000_000L),FlowType.UNSEQUENCED,
				new IgExtensionCredentials("login",username+":"+password),null); 
	}

	@Override
	public Health health() {
		if(connected) {
			return Health.up().build();
		} else {
			return Health.down().build();
		}
	}

	@Scheduled(fixedRate = 10_000)
	public void resetWsMessageReceivedCount() {
		log.info("Received {} ws messages in period", wsMessageCount);
		wsMessageCount.set(0);
	}

}
