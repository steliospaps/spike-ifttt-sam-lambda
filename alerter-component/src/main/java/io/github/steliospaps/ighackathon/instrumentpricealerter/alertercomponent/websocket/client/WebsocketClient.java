package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.websocket.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

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
import com.ig.orchestrations.us.rfed.fields.SubscriptionRequestType;
import com.ig.orchestrations.us.rfed.messages.SecurityListRequest;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.Util;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.ws.enabled", matchIfMissing = true)
public class WebsocketClient {
	
	@Value("${app.ws.url}")
	private URI url;
	@Value("${app.ws.password}")
	private String password;
	@Value("${app.ws.username}")
	private String username;
	
	
	private DateTimeFormatter fmt= DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
	
	@Autowired
	private ObjectMapper mapper;
	private UUID uuid = UUID.randomUUID();
	private Disposable disposable;
	
	private Duration retryConnectionInterval= Duration.ofSeconds(30);
	private Duration resetConnectionInterval = Duration.ofHours(8);
	private Duration heartBeatInterval = Duration.ofSeconds(20);
	
	@PostConstruct
	public void run() {
		log.info("url={}",url);
		//log.info("password={}",password);
		log.info("username={}",username);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		disposable = Flux.interval(retryConnectionInterval)//reconnect that often
			.startWith(-1L)
			.onBackpressureDrop()//otherwise it will blow up
			.flatMap(ignore-> client.execute(url, ws ->handleWs(ws)),//
					1)//how many concurrent connections
			.take(resetConnectionInterval)//disconnect every 8 hours (and resubscribe)
			.subscribe();
	}
	
	@PreDestroy
	public void shutdown() {
		disposable.dispose();
	}

	private Mono<Void> handleWs(WebSocketSession ws) {
		
		return ws.send(ws.receive()
				.map(WebSocketMessage::getPayloadAsText)//
				.log("ws-input")//
				.map(Util.sneakyF(str -> mapper.readTree(str)))//
				.transform(flux -> jsonFluxHandler(flux))//
				.log("ws-output")//
				.map(toSend->ws.textMessage(toSend)));
	}

	private Flux<String> jsonFluxHandler(Flux<JsonNode> flux) {
		
		return flux.flatMap(jsonNode ->{
			log.info("received message:", jsonNode);
			String messageType = getMessageType(jsonNode);
			
			log.info("messageType: {}",messageType);
			
			switch(messageType) {
			case "NegotiationResponse":
				log.info("will establish");
				return Flux.just(new Establish(uuid, (System.currentTimeMillis()*1_000_000L),
						heartBeatInterval.toMillis()+10_000L, null));
			case "EstablishmentAck":
				log.info("will request SecurityList");
				SecurityListRequest req = new SecurityListRequest();
				req.setSecurityReqID("req-1");
				req.setSecurityListRequestType(SecurityListRequestType.ALL_SECURITIES);
				req.setSubscriptionRequestType(SubscriptionRequestType.SNAPSHOT);
				return Flux.just(req);
			case "SecurityList":
				log.info("got securityList");
				return Flux.empty();
			default:
				log.info("ignoring msg={}",jsonNode);
				return Flux.empty();
			}
		})//
			.startWith(loginMessage())//
			.switchMap(msg -> Flux.interval(heartBeatInterval)//
					.<Object>map(i -> new UnsequencedHeartbeat())//
					.startWith(msg))
			.map(Util.sneakyF(msg -> {
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
			}))
			;	
	}

	private String getMessageType(JsonNode jsonNode) {
		JsonNode node = jsonNode.get("MessageType");
		if(node!=null) {
			return node.asText();
		}
		node = jsonNode.get("MsgType");
		if(node!=null) {
			return node.asText();
		}
		log.error("unexpected type in {}",jsonNode);
		return "unexpeted";
	}

	private Object loginMessage() {
		return new Negotiate(uuid,(System.currentTimeMillis()*1_000_000L),FlowType.UNSEQUENCED,
				new IgExtensionCredentials("login",username+":"+password),null); 
	}

}
