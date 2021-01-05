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
	
	@PostConstruct
	public void run() {
		log.info("url={}",url);
		//log.info("password={}",password);
		log.info("username={}",username);
		ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
		disposable = Flux.interval(Duration.ofSeconds(30))//reconnect that often
			.startWith(-1L)
			.onBackpressureDrop()//otherwise it will blow up
			.flatMap(ignore-> client.execute(url, ws ->handleWs(ws)),//
					1)//how many concurrent connections
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
			String messageType = jsonNode.get("MessageType").asText();
			switch(messageType) {
			case "NegotiationResponse":
				return Flux.just(new Establish(uuid, (System.currentTimeMillis()*1_000_000L), 30_000L, null));
			case "EstablishmentAck":
				SecurityListRequest req = new SecurityListRequest();
				req.setSecurityReqID("req-1");
				req.setSecurityListRequestType(SecurityListRequestType.ALL_SECURITIES);
				req.setSubscriptionRequestType(SubscriptionRequestType.SNAPSHOT);
				return Flux.just(req);
			default:
				log.info("ignoring MessageType={} msg={}",jsonNode);
				return Flux.empty();
			}
		})//
			.startWith(loginMessage())//
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

	private Object loginMessage() {
		return new Negotiate(uuid,(System.currentTimeMillis()*1_000_000L),FlowType.UNSEQUENCED,
				new IgExtensionCredentials("login",username+":"+password),null); 
	}

}
