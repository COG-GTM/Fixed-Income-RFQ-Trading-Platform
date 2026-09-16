package com.javieraviles.splitthemonolith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RfqPreviewIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private BondRepository bonds;
    @Autowired
    private CounterpartyRepository counterparties;
    @Autowired
    private RfqRepository rfqs;

    private RfqDto request;
    private long initialCount;

    @BeforeEach
    void createIsolatedDesk() {
        Counterparty counterparty = counterparties.save(
                new Counterparty("Workshop fund", "SYNTHETIC-WORKSHOP", new BigDecimal("50.00")));
        Bond bond = bonds.save(new Bond(String.format("T%011d", counterparty.getId()), "Workshop issuer",
                new BigDecimal("2.75"), LocalDate.of(2030, 11, 15), new BigDecimal("100.00")));
        request = new RfqDto();
        request.setCounterpartyId(counterparty.getId());
        request.setBondId(bond.getId());
        request.setNotionalAmount(new BigDecimal("10.00"));
        request.setExecutionPrice(new BigDecimal("5.00"));
        request.setSide(Side.BUY);
        initialCount = rfqs.count();
    }

    @Test
    void repeatedPreviewsDoNotReserveCreditInventoryOrHistory() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(post("/rfqs/preview").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true))
                    .andExpect(jsonPath("$.remainingCredit").value(45))
                    .andExpect(jsonPath("$.remainingNotional").value(90))
                    .andExpect(jsonPath("$.reasons").isEmpty());
        }
        assertDesk("50.00", "100.00", initialCount);
    }

    @Test
    void reportsBothShortfallsWithoutMutatingState() throws Exception {
        request.setNotionalAmount(new BigDecimal("100.01"));
        request.setExecutionPrice(new BigDecimal("50.01"));
        mvc.perform(post("/rfqs/preview").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("INSUFFICIENT_NOTIONAL"))
                .andExpect(jsonPath("$.reasons[1]").value("INSUFFICIENT_CREDIT"));
        assertDesk("50.00", "100.00", initialCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUY", "SELL"})
    void executionAppliesTheExistingBothSideDeductionModel(String side) throws Exception {
        request.setSide(Side.valueOf(side));
        mvc.perform(post("/rfqs").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isCreated());
        assertDesk("45.00", "90.00", initialCount + 1);
    }

    @Test
    void failedExecutionRollsBackInventoryWhenCreditIsInsufficient() throws Exception {
        request.setExecutionPrice(new BigDecimal("50.01"));
        mvc.perform(post("/rfqs").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        assertDesk("50.00", "100.00", initialCount);
    }

    @Test
    void executionRechecksStateAfterAnEarlierEligiblePreview() throws Exception {
        request.setExecutionPrice(new BigDecimal("30.00"));
        mvc.perform(post("/rfqs/preview").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true));
        mvc.perform(post("/rfqs").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isCreated());
        mvc.perform(post("/rfqs").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        assertDesk("20.00", "90.00", initialCount + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rfqs", "/rfqs/preview"})
    void rejectsIncompleteOrInvalidRequests(String endpoint) throws Exception {
        RfqDto empty = new RfqDto();
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(empty))).andExpect(status().isBadRequest());
        request.setNotionalAmount(BigDecimal.ZERO);
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        request.setNotionalAmount(BigDecimal.ONE);
        request.setExecutionPrice(new BigDecimal("-0.01"));
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        assertDesk("50.00", "100.00", initialCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"counterparty", "bond"})
    void returnsNotFoundForAnUnknownResource(String resource) throws Exception {
        String json = mapper.writeValueAsString(request);
        if ("counterparty".equals(resource)) {
            request.setCounterpartyId(Long.MAX_VALUE);
        } else {
            request.setBondId(Long.MAX_VALUE);
        }
        mvc.perform(post("/rfqs/preview").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isNotFound());
        request = mapper.readValue(json, RfqDto.class);
        assertDesk("50.00", "100.00", initialCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rfqs", "/rfqs/preview"})
    void rejectsAmountsThatCannotBeStoredExactly(String endpoint) throws Exception {
        for (String invalid : new String[] {"0.001", "100000000000000000.00"}) {
            request.setNotionalAmount(new BigDecimal(invalid));
            request.setExecutionPrice(BigDecimal.ONE);
            mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
            request.setNotionalAmount(BigDecimal.ONE);
            request.setExecutionPrice(new BigDecimal(invalid));
            mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        assertDesk("50.00", "100.00", initialCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"credit", "notional"})
    void incompleteStoredBalancesReturnAConflict(String missing) throws Exception {
        if ("credit".equals(missing)) {
            Counterparty counterparty = counterparties.findById(request.getCounterpartyId()).orElseThrow();
            counterparty.setAvailableCredit(null);
            counterparty.setCreditLimit(null);
            counterparties.save(counterparty);
        } else {
            Bond bond = bonds.findById(request.getBondId()).orElseThrow();
            bond.setAvailableNotional(null);
            bonds.save(bond);
        }
        mvc.perform(post("/rfqs/preview").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isConflict());
        assertThat(rfqs.count()).isEqualTo(initialCount);
        if ("credit".equals(missing)) {
            assertThat(counterparties.findById(request.getCounterpartyId()).orElseThrow()
                    .getAvailableCredit()).isNull();
        } else {
            assertThat(bonds.findById(request.getBondId()).orElseThrow()
                    .getAvailableNotional()).isNull();
        }
    }

    private void assertDesk(String credit, String notional, long count) {
        assertThat(counterparties.findById(request.getCounterpartyId()).orElseThrow()
                .getAvailableCredit()).isEqualByComparingTo(credit);
        assertThat(bonds.findById(request.getBondId()).orElseThrow()
                .getAvailableNotional()).isEqualByComparingTo(notional);
        assertThat(rfqs.count()).isEqualTo(count);
    }
}
