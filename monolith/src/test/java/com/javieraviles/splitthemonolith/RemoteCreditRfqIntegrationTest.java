package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

/**
 * Exercises RFQ execution while the credit-check capability is routed through
 * the extracted credit-service ({@code rfq.credit.service.remote=true}). The
 * remote service is emulated with {@link MockRestServiceServer} bound to the
 * monolith's {@link RestTemplate}, so the full /rfqs &rarr; saga &rarr;
 * RemoteCreditService HTTP path is covered without a running service.
 */
@SpringBootTest(properties = {
		"rfq.credit.service.remote=true",
		// Isolate this context's in-memory schema from the default-profile
		// IntegrationTest context so the startup data seeder does not collide.
		"spring.datasource.url=jdbc:h2:mem:remote-credit-rfq-testdb;DB_CLOSE_DELAY=-1" })
@AutoConfigureMockMvc
public class RemoteCreditRfqIntegrationTest {

	private static final String RESERVATIONS_URL = "http://localhost:8060/credit/reservations";
	private static final String RELEASES_URL = "http://localhost:8060/credit/releases";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RestTemplate restTemplate;

	private MockRestServiceServer creditService;

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void setUp() {
		creditService = MockRestServiceServer.createServer(restTemplate);
	}

	@AfterEach
	void tearDown() {
		creditService.verify();
	}

	@Test
	public void whenExecuteRfq_reservesCreditRemotely_andDeductsNotionalLocally() throws Exception {
		final long cpId = createCounterparty(new Counterparty("Remote Fidelity",
				"549300REMOTEFID0001", new BigDecimal("20000000.00")));
		final long bondId = createBond(new Bond("US912828RC01", "US Treasury",
				new BigDecimal("3.1250"), LocalDate.of(2032, 5, 15), new BigDecimal("50000000.00")));

		creditService.expect(requestTo(RESERVATIONS_URL)).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.OK));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, "1000000.00", "998750.00", Side.BUY)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		// Notional invariant: bond inventory was reduced locally by the trade.
		mvc.perform(get("/bonds/" + bondId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional").value(49000000.00));
	}

	@Test
	public void whenExecuteRfq_withRemoteInsufficientCredit_thenBadRequest_andNotionalUntouched() throws Exception {
		final long cpId = createCounterparty(new Counterparty("Remote Small Fund",
				"549300REMOTESML0001", new BigDecimal("500000.00")));
		final long bondId = createBond(new Bond("US912828RC02", "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2033, 2, 15), new BigDecimal("50000000.00")));

		// Service rejects the reservation (credit invariant enforced remotely).
		creditService.expect(requestTo(RESERVATIONS_URL)).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.CONFLICT));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, "1000000.00", "999000.00", Side.SELL)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		// Atomicity: notional untouched and no RFQ persisted when credit is denied.
		mvc.perform(get("/bonds/" + bondId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional").value(50000000.00));
	}

	@Test
	public void whenExecuteRfq_withInsufficientNotional_thenCreditReservationIsCompensated() throws Exception {
		final long cpId = createCounterparty(new Counterparty("Remote BlackRock",
				"549300REMOTEBLK0001", new BigDecimal("30000000.00")));
		final long bondId = createBond(new Bond("US912828RC03", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2031, 8, 15), new BigDecimal("2000000.00")));

		// Reservation succeeds, but the local notional step fails, so the saga
		// must issue a compensating release.
		creditService.expect(requestTo(RESERVATIONS_URL)).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.OK));
		creditService.expect(requestTo(RELEASES_URL)).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.OK));

		mvc.perform(post("/rfqs").content(asJsonString(rfq(cpId, bondId, "5000000.00", "4993750.00", Side.BUY)))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));

		// Atomicity: notional untouched and no RFQ persisted after compensation.
		mvc.perform(get("/bonds/" + bondId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional").value(2000000.00));
	}

	private static RfqDto rfq(final long cpId, final long bondId, final String notional,
			final String price, final Side side) {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal(notional));
		rfq.setSide(side);
		rfq.setExecutionPrice(new BigDecimal(price));
		return rfq;
	}

	private long createCounterparty(final Counterparty cp) throws Exception {
		final MvcResult result = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private long createBond(final Bond bond) throws Exception {
		final MvcResult result = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
	}

	private static long extractId(final MvcResult result) throws Exception {
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
