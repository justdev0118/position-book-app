package com.jpmc.positionbook.service;

import com.jpmc.positionbook.dto.PositionBookRequest;
import com.jpmc.positionbook.enums.EventType;
import com.jpmc.positionbook.exception.BadRequestException;
import com.jpmc.positionbook.exception.DuplicateEventException;
import com.jpmc.positionbook.exception.SecurityNotFoundException;
import com.jpmc.positionbook.store.PositionBookStore;
import com.jpmc.positionbook.store.PositionBookStore.CancelResult;
import com.jpmc.positionbook.store.PositionBookStore.SellResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionBookServiceTest {

    @Mock
    private PositionBookStore store;

    @InjectMocks
    private PositionBookService service;

    private PositionBookRequest buildRequest(String accountNumber, String securityIdentifier, int quantity) {
        PositionBookRequest request = new PositionBookRequest();
        request.setAccountNumber(accountNumber);
        request.setSecurityIdentifier(securityIdentifier);
        request.setQuantity(quantity);
        return request;
    }

    @Test
    void processBuy_success_savesEvent() {
        when(store.saveBuyEvent(anyString(), any(), anyString())).thenReturn(true);

        service.processTradeEvent(1, EventType.BUY, buildRequest("ACC1", "SEC1", 100));

        verify(store).saveBuyEvent(eq("1:ACC1"), any(), eq("ACC1:SEC1"));
    }

    @Test
    void processBuy_duplicate_throwsDuplicateEventException() {
        when(store.saveBuyEvent(anyString(), any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.processTradeEvent(1, EventType.BUY, buildRequest("ACC1", "SEC1", 100)))
                .isInstanceOf(DuplicateEventException.class);
    }

    @Test
    void processBuy_zeroQuantity_throwsBadRequestException() {
        assertThatThrownBy(() -> service.processTradeEvent(1, EventType.BUY, buildRequest("ACC1", "SEC1", 0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Quantity must be positive");

        verify(store, never()).saveBuyEvent(anyString(), any(), anyString());
    }

    @Test
    void processBuy_negativeQuantity_throwsBadRequestException() {
        assertThatThrownBy(() -> service.processTradeEvent(1, EventType.BUY, buildRequest("ACC1", "SEC1", -10)))
                .isInstanceOf(BadRequestException.class);

        verify(store, never()).saveBuyEvent(anyString(), any(), anyString());
    }

    @Test
    void processSell_success_deductsQuantity() {
        when(store.trySell(anyString(), any(), anyString())).thenReturn(SellResult.SUCCESS);

        service.processTradeEvent(2, EventType.SELL, buildRequest("ACC1", "SEC1", 50));

        verify(store).trySell(eq("2:ACC1"), any(), eq("ACC1:SEC1"));
    }

    @Test
    void processSell_positionNotFound_throwsSecurityNotFoundException() {
        when(store.trySell(anyString(), any(), anyString())).thenReturn(SellResult.NOT_FOUND);

        assertThatThrownBy(() -> service.processTradeEvent(2, EventType.SELL, buildRequest("ACC1", "SEC1", 50)))
                .isInstanceOf(SecurityNotFoundException.class);
    }

    @Test
    void processSell_duplicateEvent_throwsDuplicateEventException() {
        when(store.trySell(anyString(), any(), anyString())).thenReturn(SellResult.DUPLICATE);

        assertThatThrownBy(() -> service.processTradeEvent(2, EventType.SELL, buildRequest("ACC1", "SEC1", 50)))
                .isInstanceOf(DuplicateEventException.class);
    }

    @Test
    void processSell_insufficientQuantity_throwsBadRequestException() {
        when(store.trySell(anyString(), any(), anyString())).thenReturn(SellResult.INSUFFICIENT);

        assertThatThrownBy(() -> service.processTradeEvent(2, EventType.SELL, buildRequest("ACC1", "SEC1", 50)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient securities");
    }

    @Test
    void processSell_zeroQuantity_throwsBadRequestException() {
        assertThatThrownBy(() -> service.processTradeEvent(2, EventType.SELL, buildRequest("ACC1", "SEC1", 0)))
                .isInstanceOf(BadRequestException.class);

        verify(store, never()).trySell(anyString(), any(), anyString());
    }

    @Test
    void processCancel_success_cancelsEvent() {
        when(store.tryCancel(anyString(), any())).thenReturn(CancelResult.SUCCESS);

        service.processTradeEvent(1, EventType.CANCEL, buildRequest("ACC1", "SEC1", 0));

        verify(store).tryCancel(eq("1:ACC1"), any());
    }

    @Test
    void processCancel_eventNotFound_throwsBadRequestException() {
        when(store.tryCancel(anyString(), any())).thenReturn(CancelResult.NOT_FOUND);

        assertThatThrownBy(() -> service.processTradeEvent(1, EventType.CANCEL, buildRequest("ACC1", "SEC1", 0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No event found to cancel");
    }

    @Test
    void processCancel_alreadyCancelled_throwsDuplicateEventException() {
        when(store.tryCancel(anyString(), any())).thenReturn(CancelResult.ALREADY_CANCELLED);

        assertThatThrownBy(() -> service.processTradeEvent(1, EventType.CANCEL, buildRequest("ACC1", "SEC1", 0)))
                .isInstanceOf(DuplicateEventException.class)
                .hasMessageContaining("already been cancelled");
    }
}
