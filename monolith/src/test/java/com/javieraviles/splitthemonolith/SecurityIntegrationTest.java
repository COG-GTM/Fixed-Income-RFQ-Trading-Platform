package com.javieraviles.splitthemonolith;

import static com.javieraviles.splitthemonolith.TestAuth.ACME_TRADER;
import static com.javieraviles.splitthemonolith.TestAuth.OPERATOR;
import static com.javieraviles.splitthemonolith.TestAuth.UNBOUND_TRADER;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityIntegrationTest {

	private static final String ACME_LEI = "549300EXAMPLE12345678";

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Test
	public void everyEndpointWithoutCredentials_returnsUnauthorized() throws Exception {
		final String body = "{}";
		final List<MockHttpServletRequestBuilder> requests = Arrays.asList(
				get("/counterparties"), post("/counterparties").content(body), get("/counterparties/1"),
				put("/counterparties/1").content(body),
				patch("/counterparties/1").content("{\"amount\":\"1\",\"operation\":\"ADD\"}"),
				delete("/counterparties/1"),
				get("/bonds"), post("/bonds").content(body), get("/bonds/1"), put("/bonds/1").content(body),
				patch("/bonds/1").content("{\"amount\":\"1\",\"operation\":\"ADD\"}"), delete("/bonds/1"),
				get("/rfqs"), post("/rfqs").content(body), get("/rfqs/1"), delete("/rfqs/1"));
		for (final MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	public void wrongPassword_returnsUnauthorized() throws Exception {
		mvc.perform(get("/bonds").with(TestAuth.basic("test-operator", "wrong")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void userWithoutConfiguredPassword_cannotLogIn() throws Exception {
		mvc.perform(get("/bonds").with(TestAuth.basic("disabled-operator", ""))).andExpect(status().isUnauthorized());
	}

	@Test
	public void unsupportedPasswordPrefix_isHashedAsLiteralSecret() throws Exception {
		mvc.perform(get("/bonds").with(TestAuth.basic("prefixed-operator", "literal-pw")))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/bonds").with(TestAuth.basic("prefixed-operator", "{noop}literal-pw")))
				.andExpect(status().isOk());
	}

	@Test
	public void duplicateLei_isRejectedAndTraderKeepsAccess() throws Exception {
		mvc.perform(post("/counterparties").with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
				.content(json(new Counterparty("Acme Clone", ACME_LEI, new BigDecimal("1")))))
				.andExpect(status().isConflict());
		final Counterparty other = counterpartyRepository
				.save(new Counterparty("Lei Fund", "549300LEIFUND0000001", new BigDecimal("1000.00")));
		mvc.perform(put("/counterparties/" + other.getId()).with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
				.content(json(new Counterparty("Lei Fund", ACME_LEI, new BigDecimal("1000.00")))))
				.andExpect(status().isConflict());
		mvc.perform(put("/counterparties/" + other.getId()).with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
				.content(json(new Counterparty("Lei Fund Renamed", "549300LEIFUND0000001", new BigDecimal("1000.00")))))
				.andExpect(status().isOk());
		mvc.perform(get("/counterparties").with(ACME_TRADER)).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].lei", is(ACME_LEI)));
	}

	@Test
	public void trader_cannotChangeCreditOrNotionalOrDelete() throws Exception {
		final long acmeId = acme().getId();
		mvc.perform(patch("/counterparties/" + acmeId).with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"1000000\",\"operation\":\"ADD\"}")).andExpect(status().isForbidden());
		mvc.perform(put("/counterparties/" + acmeId).with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content(json(acme()))).andExpect(status().isForbidden());
		mvc.perform(post("/counterparties").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content(json(new Counterparty("Rogue", "549300ROGUE000000001", new BigDecimal("1")))))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/counterparties/" + acmeId).with(ACME_TRADER)).andExpect(status().isForbidden());
		mvc.perform(patch("/bonds/1").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"1000000\",\"operation\":\"ADD\"}")).andExpect(status().isForbidden());
		mvc.perform(put("/bonds/1").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/bonds").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(delete("/bonds/1").with(ACME_TRADER)).andExpect(status().isForbidden());
		mvc.perform(delete("/rfqs/1").with(ACME_TRADER)).andExpect(status().isForbidden());
	}

	@Test
	public void trader_cannotExecuteRfqForAnotherCounterparty() throws Exception {
		final Counterparty other = counterpartyRepository
				.save(new Counterparty("Other Fund", "549300OTHERFUND00001", new BigDecimal("10000000.00")));
		final Bond bond = newBond("US91282SEC01");
		final RfqDto rfq = rfq(other.getId(), bond.getId(), "1000000.00", "998750.00");

		mvc.perform(post("/rfqs").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON).content(json(rfq)))
				.andExpect(status().isForbidden());

		assertCredit(other.getId(), "10000000.00");
	}

	@Test
	public void trader_executesRfqAsOwnCounterparty() throws Exception {
		final long acmeId = acme().getId();
		final Bond bond = newBond("US91282SEC02");

		mvc.perform(post("/rfqs").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content(json(rfq(0, bond.getId(), "100000.00", "99875.00")))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.counterpartyId", is((int) acmeId)))
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		mvc.perform(post("/rfqs").with(ACME_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content(json(rfq(acmeId, bond.getId(), "100000.00", "99875.00")))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.counterpartyId", is((int) acmeId)));
	}

	@Test
	public void trader_onlySeesOwnCounterpartyAndRfqs() throws Exception {
		final Counterparty other = counterpartyRepository
				.save(new Counterparty("Hidden Fund", "549300HIDDENFUND0001", new BigDecimal("10000000.00")));
		final long acmeId = acme().getId();

		mvc.perform(get("/counterparties").with(ACME_TRADER)).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].lei", is(ACME_LEI)));
		mvc.perform(get("/counterparties/" + acmeId).with(ACME_TRADER)).andExpect(status().isOk());
		mvc.perform(get("/counterparties/" + other.getId()).with(ACME_TRADER)).andExpect(status().isForbidden());

		final Bond bond = newBond("US91282SEC03");
		mvc.perform(post("/rfqs").with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
				.content(json(rfq(other.getId(), bond.getId(), "100000.00", "99875.00"))))
				.andExpect(status().isCreated());

		mvc.perform(get("/rfqs").with(ACME_TRADER)).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].counterpartyId", everyItem(is((int) acmeId))));
		mvc.perform(get("/rfqs").with(OPERATOR)).andExpect(status().isOk());
	}

	@Test
	public void trader_cannotReadAnotherCounterpartysRfq() throws Exception {
		final Counterparty other = counterpartyRepository
				.save(new Counterparty("Private Fund", "549300PRIVATEFUND001", new BigDecimal("10000000.00")));
		final Bond bond = newBond("US91282SEC04");
		final String created = mvc
				.perform(post("/rfqs").with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
						.content(json(rfq(other.getId(), bond.getId(), "100000.00", "99875.00"))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		final long rfqId = MAPPER.readTree(created).get("id").asLong();

		mvc.perform(get("/rfqs/" + rfqId).with(ACME_TRADER)).andExpect(status().isForbidden());
		mvc.perform(get("/rfqs/" + rfqId).with(OPERATOR)).andExpect(status().isOk());
	}

	@Test
	public void traderWithoutBoundCounterparty_isForbidden() throws Exception {
		mvc.perform(get("/counterparties").with(UNBOUND_TRADER)).andExpect(status().isForbidden());
		mvc.perform(post("/rfqs").with(UNBOUND_TRADER).contentType(MediaType.APPLICATION_JSON)
				.content(json(rfq(0, 1, "100000.00", "99875.00")))).andExpect(status().isForbidden());
	}

	@Test
	public void operator_canManageCredit() throws Exception {
		final Counterparty cp = counterpartyRepository
				.save(new Counterparty("Managed Fund", "549300MANAGEDFUND001", new BigDecimal("1000000.00")));
		mvc.perform(patch("/counterparties/" + cp.getId()).with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"500000\",\"operation\":\"DEDUCT\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(500000.00)));
		mvc.perform(delete("/counterparties/" + cp.getId()).with(OPERATOR)).andExpect(status().isOk());
	}

	@Test
	public void executionPriceOutsideBand_isRejected() throws Exception {
		final Counterparty cp = counterpartyRepository
				.save(new Counterparty("Band Fund", "549300BANDFUND000001", new BigDecimal("10000000.00")));
		final Bond bond = newBond("US91282SEC05");

		for (final String price : new String[] { "0.01", "-100.00", "2000000.00" }) {
			mvc.perform(post("/rfqs").with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
					.content(json(rfq(cp.getId(), bond.getId(), "1000000.00", price))))
					.andExpect(status().isBadRequest());
		}
		mvc.perform(post("/rfqs").with(OPERATOR).contentType(MediaType.APPLICATION_JSON)
				.content(json(rfq(cp.getId(), bond.getId(), "-1000000.00", "-998750.00"))))
				.andExpect(status().isBadRequest());

		assertCredit(cp.getId(), "10000000.00");
		mvc.perform(get("/bonds/" + bond.getId()).with(OPERATOR))
				.andExpect(jsonPath("$.availableNotional", is(50000000.00)));
	}

	private Counterparty acme() {
		return counterpartyRepository.findByLei(ACME_LEI).get(0);
	}

	private Bond newBond(final String isin) {
		return bondRepository.save(new Bond(isin, "US Treasury", new BigDecimal("3.0000"), LocalDate.of(2034, 5, 15),
				new BigDecimal("50000000.00")));
	}

	private void assertCredit(final long counterpartyId, final String expected) {
		final BigDecimal actual = counterpartyRepository.findById(counterpartyId).get().getAvailableCredit();
		if (actual.compareTo(new BigDecimal(expected)) != 0) {
			throw new AssertionError("Expected credit " + expected + " but was " + actual);
		}
	}

	private static RfqDto rfq(final long counterpartyId, final long bondId, final String notional,
			final String price) {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(counterpartyId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal(notional));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal(price));
		return rfq;
	}

	private static String json(final Object obj) throws Exception {
		return MAPPER.writeValueAsString(obj);
	}
}
