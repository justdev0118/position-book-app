package com.jpmc.positionbook.service;

import com.jpmc.positionbook.dto.EventHistoryItem;
import com.jpmc.positionbook.dto.PositionBookRequest;
import com.jpmc.positionbook.dto.PositionResponse;
import com.jpmc.positionbook.enums.EventType;
import com.jpmc.positionbook.exception.BadRequestException;
import com.jpmc.positionbook.exception.DuplicateEventException;
import com.jpmc.positionbook.exception.SecurityNotFoundException;
import com.jpmc.positionbook.model.Position;
import com.jpmc.positionbook.model.TradeEvent;
import com.jpmc.positionbook.store.PositionBookStore;
import com.jpmc.positionbook.store.PositionBookStore.CancelResult;
import com.jpmc.positionbook.store.PositionBookStore.SellResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PositionBookService {

    private final PositionBookStore store;

    public PositionBookService(PositionBookStore store) {
        this.store = store;
    }
    // This method processes a trade event based on its type (BUY, SELL, CANCEL) and updates the position book accordingly.
    public void processTradeEvent(int id, EventType eventType, PositionBookRequest request) {
        switch (eventType) {
            case BUY -> processBuy(id, eventType, request);
            case SELL -> processSell(id, eventType, request);
            case CANCEL -> processCancel(id, eventType, request);
        }
    }
    // This method processes a BUY event by validating the quantity and saving the event to the position book. If the event already exists, it throws a DuplicateEventException.
    private void processBuy(int id, EventType eventType, PositionBookRequest request) {
        validatePositiveQuantity(request.getQuantity(), eventType);
        EventContext ctx = buildContext(id, eventType, request);
        if (!store.saveBuyEvent(ctx.eventKey(), ctx.event(), ctx.positionKey())) {
            throw new DuplicateEventException("Event already exists for id " + id + " and account " + request.getAccountNumber());
        }
    }
    // This method processes a SELL event by validating the quantity and attempting to sell the securities from the position book. It handles various outcomes such as insufficient securities, duplicate events, or successful sales, throwing appropriate exceptions when necessary.
    private void processSell(int id, EventType eventType, PositionBookRequest request) {
        validatePositiveQuantity(request.getQuantity(), eventType);
        EventContext ctx = buildContext(id, eventType, request);
        SellResult result = store.trySell(ctx.eventKey(), ctx.event(), ctx.positionKey());
        switch (result) {
            case NOT_FOUND -> throw new SecurityNotFoundException("No position found for account " + request.getAccountNumber() + " and security " + request.getSecurityIdentifier());
            case DUPLICATE -> throw new DuplicateEventException("Event already exists for id " + id + " and account " + request.getAccountNumber());
            case INSUFFICIENT -> throw new BadRequestException("Insufficient securities to sell");
            case SUCCESS -> {}
        }
    }

    //  This method processes a CANCEL event by attempting to cancel the specified trade event in the position book. It handles various outcomes such as the event not being found, already being cancelled, or successful cancellation, throwing appropriate exceptions when necessary.
    private void processCancel(int id, EventType eventType, PositionBookRequest request) {
        EventContext ctx = buildContext(id, eventType, request);
        CancelResult result = store.tryCancel(ctx.eventKey(), ctx.event());
        switch (result) {
            case NOT_FOUND -> throw new BadRequestException("No event found to cancel for id " + id + " and account " + request.getAccountNumber());
            case ALREADY_CANCELLED -> throw new DuplicateEventException("Event with id " + id + " has already been cancelled");
            case SUCCESS -> {}
        }
    }
    //  This method validates that the quantity for a trade event is positive. If the quantity is zero or negative, it throws a BadRequestException with a message indicating that the quantity must be positive for the specified event type.
    private void validatePositiveQuantity(int quantity, EventType eventType) {
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be positive for " + eventType + " events");
        }
    }
    // This method builds an EventContext object that contains the event key, position key, and TradeEvent object based on the provided id, event type, and request. The event key is constructed using the id and account number, while the position key is constructed using the account number and security identifier. The TradeEvent object is created with the provided details.
    private EventContext buildContext(int id, EventType eventType, PositionBookRequest request) {
        String eventKey = id + ":" + request.getAccountNumber();
        String positionKey = request.getAccountNumber() + ":" + request.getSecurityIdentifier();
        TradeEvent event = TradeEvent.builder()
                .id(id)
                .eventType(eventType)
                .accountNumber(request.getAccountNumber())
                .securityIdentifier(request.getSecurityIdentifier())
                .quantity(request.getQuantity())
                .build();
        return new EventContext(eventKey, positionKey, event);
    }
    // This method retrieves the positions for a given account number from the position book. It calls the store to get the positions and checks if any positions are found. If no positions are found, it throws a SecurityNotFoundException. Otherwise, it maps the positions to PositionResponse objects, which include the account number, security identifier, net quantity, and event history, and returns them as a list.
    public List<PositionResponse> getPositions(String accountNumber) {
        Map<String, Position> positions = store.getPositionsByAccount(accountNumber);
        if (positions.isEmpty()) {
            throw new SecurityNotFoundException("No positions found for account " + accountNumber);
        }
        return positions.entrySet().stream()
                .map(e -> toResponse(accountNumber, e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private PositionResponse toResponse(String accountNumber, String positionKey, Position position) {
        String securityIdentifier = positionKey.substring(accountNumber.length() + 1);
        List<EventHistoryItem> history = position.getEventHistory().stream()
                .map(e -> EventHistoryItem.builder()
                        .id(e.getId())
                        .eventType(e.getEventType())
                        .quantity(e.getQuantity())
                        .build())
                .collect(Collectors.toList());
        return PositionResponse.builder()
                .accountNumber(accountNumber)
                .securityIdentifier(securityIdentifier)
                .netQuantity(position.getQuantity())
                .eventHistory(history)
                .build();
    }

    private record EventContext(String eventKey, String positionKey, TradeEvent event) {}
}
