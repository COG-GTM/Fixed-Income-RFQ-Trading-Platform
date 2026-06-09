package com.javieraviles.counterpartyservice;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.counterpartyservice.dto.CreditDeductionRequest;
import com.javieraviles.counterpartyservice.entity.Counterparty;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class CounterpartyServiceIntegrationTest {

        @Autowired
        private MockMvc mvc;

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Test
        public void givenSeedCounterparty_whenGetCounterparties_thenReturnJsonArray() throws Exception {
                mvc.perform(get("/counterparties").contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].name", is("Acme Asset Management")));
        }

        @Test
        public void whenCreateCounterparty_thenReturnCreated() throws Exception {
                final Counterparty cp = new Counterparty("Fidelity Investments",
                                "549300FIDELITY00001", new BigDecimal("20000000.00"));
                mvc.perform(post("/counterparties").content(asJsonString(cp))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is("Fidelity Investments")));
        }

        @Test
        public void whenValidateExistingCounterparty_thenReturnOk() throws Exception {
                mvc.perform(post("/counterparties/1/validate")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Acme Asset Management")));
        }

        @Test
        public void whenValidateNonExistentCounterparty_thenReturnNotFound() throws Exception {
                mvc.perform(post("/counterparties/9999/validate")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound());
        }

        @Test
        public void whenDeductCredit_thenReturnUpdatedCounterparty() throws Exception {
                final Counterparty cp = new Counterparty("Deduct Test Corp",
                                "549300DEDUCTTEST01", new BigDecimal("10000000.00"));
                MvcResult result = mvc.perform(post("/counterparties").content(asJsonString(cp))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isCreated()).andReturn();

                final long cpId = extractId(result);
                final CreditDeductionRequest request = new CreditDeductionRequest(new BigDecimal("1000000.00"));

                mvc.perform(post("/counterparties/" + cpId + "/deduct-credit")
                                .content(asJsonString(request))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.availableCredit", is(9000000.00)));
        }

        @Test
        public void whenDeductCredit_withInsufficientCredit_thenReturnBadRequest() throws Exception {
                final Counterparty cp = new Counterparty("Small Fund LLC",
                                "549300SMALLFUND001", new BigDecimal("500000.00"));
                MvcResult result = mvc.perform(post("/counterparties").content(asJsonString(cp))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isCreated()).andReturn();

                final long cpId = extractId(result);
                final CreditDeductionRequest request = new CreditDeductionRequest(new BigDecimal("999999.00"));

                mvc.perform(post("/counterparties/" + cpId + "/deduct-credit")
                                .content(asJsonString(request))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isBadRequest());
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
