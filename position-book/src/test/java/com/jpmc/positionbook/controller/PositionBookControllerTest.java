package com.jpmc.positionbook.controller;

import com.jpmc.positionbook.dto.PositionBookRequest;
import com.jpmc.positionbook.enums.EventType;
import com.jpmc.positionbook.exception.BadRequestException;
import com.jpmc.positionbook.exception.DuplicateEventException;
import com.jpmc.positionbook.exception.SecurityNotFoundException;
import com.jpmc.positionbook.service.PositionBookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PositionBookController.class)
class PositionBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PositionBookService positionBookService;

    private static final String URL = "/v1/position-book/process";

    private static final String VALID_BODY = """
            {"accountNumber": "ACC1", "securityIdentifier": "SEC1", "quantity": 100}
            """;

    @Test
    void processTradeEvent_validBuyEvent_returns201() throws Exception {
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        verify(positionBookService).processTradeEvent(anyInt(), any(EventType.class), any(PositionBookRequest.class));
    }

    @Test
    void processTradeEvent_validSellEvent_returns201() throws Exception {
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "SELL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void processTradeEvent_validCancelEvent_returns201() throws Exception {
        String cancelBody = """
                {"accountNumber": "ACC1", "securityIdentifier": "SEC1", "quantity": 0}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "CANCEL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isCreated());
    }

    @Test
    void processTradeEvent_missingEventType_returns400() throws Exception {
        mockMvc.perform(post(URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void processTradeEvent_invalidEventType_returns400() throws Exception {
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "INVALID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processTradeEvent_missingAccountNumber_returns400() throws Exception {
        String body = """
                {"securityIdentifier": "SEC1", "quantity": 100}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processTradeEvent_blankAccountNumber_returns400() throws Exception {
        String body = """
                {"accountNumber": "", "securityIdentifier": "SEC1", "quantity": 100}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processTradeEvent_missingSecurityIdentifier_returns400() throws Exception {
        String body = """
                {"accountNumber": "ACC1", "quantity": 100}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processTradeEvent_missingQuantity_returns400() throws Exception {
        String body = """
                {"accountNumber": "ACC1", "securityIdentifier": "SEC1"}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processTradeEvent_negativeQuantity_returns400() throws Exception {
        String body = """
                {"accountNumber": "ACC1", "securityIdentifier": "SEC1", "quantity": -1}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processTradeEvent_duplicateEvent_returns409() throws Exception {
        doThrow(new DuplicateEventException("Event already exists"))
                .when(positionBookService).processTradeEvent(anyInt(), any(), any());

        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "BUY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Event already exists"));
    }

    @Test
    void processTradeEvent_securityNotFound_returns404() throws Exception {
        doThrow(new SecurityNotFoundException("No position found"))
                .when(positionBookService).processTradeEvent(anyInt(), any(), any());

        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "SELL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void processTradeEvent_insufficientQuantity_returns400() throws Exception {
        doThrow(new BadRequestException("Insufficient securities to sell"))
                .when(positionBookService).processTradeEvent(anyInt(), any(), any());

        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "SELL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient securities to sell"));
    }

    @Test
    void processTradeEvent_alreadyCancelled_returns409() throws Exception {
        doThrow(new DuplicateEventException("Event already cancelled"))
                .when(positionBookService).processTradeEvent(anyInt(), any(), any());

        String cancelBody = """
                {"accountNumber": "ACC1", "securityIdentifier": "SEC1", "quantity": 0}
                """;
        mockMvc.perform(post(URL + "/1")
                        .queryParam("eventType", "CANCEL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isConflict());
    }
}
