package io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.instruments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromDBEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromDBEvent.InstrumentReceivedFromDBEventBuilder;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent;
import io.github.steliospaps.ighackathon.instrumentpricealerter.alertercomponent.events.InstrumentReceivedFromIGEvent.InstrumentReceivedFromIGEventBuilder;

@ExtendWith(MockitoExtension.class)
class EpicUpdaterTest {

	private static final String DESCRIPTION = "description";

	private static final String EPIC = "epic";

	private static final String SYMBOL = "symbol";

	@Mock
	private InstrumentDao instrumentDao;

	@InjectMocks
	private EpicUpdater impl;

	@Test
	void testHappyPathInsert() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onSecurityListReceived();
		impl.onDBInstrumentsRead();
		verify(instrumentDao).insert(defaultIgInstrument().build());
	}

	@Test
	void testHappyPathDelete() {
		impl.onDbInstrument(defaultDbInstrument().build());
		impl.onSecurityListReceived();
		impl.onDBInstrumentsRead();
		verify(instrumentDao).delete(EPIC);
	}

	@Test
	void testHappyPathInsert2() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verify(instrumentDao).insert(defaultIgInstrument().build());
	}

	@Test
	void testInsertNotTrigger() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onSecurityListReceived();
		verifyNoInteractions(instrumentDao);
	}

	@Test
	void testInsertNotTrigger2() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onSecurityListReceived();
		verifyNoInteractions(instrumentDao);
	}
	
	@Test
	void testInsertNotHappenIfThere() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onDbInstrument(defaultDbInstrument().build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verifyNoInteractions(instrumentDao);
	}

	private InstrumentReceivedFromIGEventBuilder defaultIgInstrument() {
		return InstrumentReceivedFromIGEvent.builder()
				.description(DESCRIPTION)//
				.epic(EPIC)//
				.symbol(SYMBOL);
	}
	private InstrumentReceivedFromDBEventBuilder defaultDbInstrument() {
		return InstrumentReceivedFromDBEvent.builder()
				.description(DESCRIPTION)//
				.epic(EPIC)//
				.symbol(SYMBOL);
	}

	@Test
	void testInsertHappensIfDbDescrOrSymbolAreDifferent_description() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onDbInstrument(defaultDbInstrument().description("foo").build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verify(instrumentDao).insert(defaultIgInstrument().build());
	}
	@Test
	void testInsertHappensIfDbDescrOrSymbolAreDifferent_symbol() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onDbInstrument(defaultDbInstrument().symbol("foo").build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verify(instrumentDao).insert(defaultIgInstrument().build());
	}

	@Test
	void testSecurityListIsReceivedAgainOnReconnection_noEffect() {
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verify(instrumentDao).insert(defaultIgInstrument().build());
		
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onSecurityListReceived();
		Mockito.verifyNoMoreInteractions(instrumentDao);
		
	}
	@Test
	void testSecurityListIsReceivedAgainOnReconnection_noEffect_wasDelete() {
		impl.onDbInstrument(defaultDbInstrument().build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verify(instrumentDao).delete(EPIC);
		impl.onSecurityListReceived();
		Mockito.verifyNoMoreInteractions(instrumentDao);
		
	}

	@Test
	void testSecurityListIsReceivedAgainOnReconnection_shouldInsert_wasDelete() {
		impl.onDbInstrument(defaultDbInstrument().build());
		impl.onDBInstrumentsRead();
		impl.onSecurityListReceived();
		verify(instrumentDao).delete(EPIC);
		
		impl.onIgInstrument(defaultIgInstrument().build());
		impl.onSecurityListReceived();
		verify(instrumentDao).insert(defaultIgInstrument().build());
	}

	@Test
	void testSecurityListIsReceivedAgainOnReconnection_insertIfDifferent() {
			impl.onIgInstrument(defaultIgInstrument().build());
			impl.onDBInstrumentsRead();
			impl.onSecurityListReceived();
			verify(instrumentDao).insert(defaultIgInstrument().build());
			
			InstrumentReceivedFromIGEvent differentInstrument = defaultIgInstrument().description("foo").build();
			impl.onIgInstrument(differentInstrument);
			impl.onSecurityListReceived();
			verify(instrumentDao).insert(differentInstrument);
	}
	@Test
	void testSecurityListIsReceivedAgainMissingInstrument_willDelete() {
			impl.onIgInstrument(defaultIgInstrument().build());
			impl.onDBInstrumentsRead();
			impl.onSecurityListReceived();
			verify(instrumentDao).insert(defaultIgInstrument().build());
			verifyNoMoreInteractions(instrumentDao);
			
			//instrument not received the second time
			impl.onSecurityListReceived();
			verify(instrumentDao).delete(EPIC);
	}
}
