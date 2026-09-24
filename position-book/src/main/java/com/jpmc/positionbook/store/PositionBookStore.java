package com.jpmc.positionbook.store;

import com.jpmc.positionbook.enums.EventType;
import com.jpmc.positionbook.model.Position;
import com.jpmc.positionbook.model.TradeEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class PositionBookStore {

    // This class is designed to be thread-safe and handle concurrent access to the position book data.
    private final ConcurrentHashMap<String, TradeEvent> eventStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Position> positionStore = new ConcurrentHashMap<>();
    private final Set<String> cancelledEvents = ConcurrentHashMap.newKeySet();

    // Save a buy event to the position book. If the event already exists, it will not be added again.
    public boolean saveBuyEvent(String eventKey, TradeEvent event, String positionKey) {
        if (eventStore.putIfAbsent(eventKey, event) != null) {
            return false;
        }
        Position position = positionStore.computeIfAbsent(positionKey, k -> new Position());
        position.addEvent(event);
        position.adjustQuantity(event.getQuantity());
        return true;
    }
    // Attempt to sell securities from the position book. It checks if the position exists, if there are sufficient securities to sell, and if the event is a duplicate. It returns an appropriate SellResult based on the outcome.
    public SellResult trySell(String eventKey, TradeEvent event, String positionKey) {
        Position position = positionStore.get(positionKey);
        if (position == null) {
            return SellResult.NOT_FOUND;
        }
        AtomicInteger quantity = position.getQuantityRef();
        synchronized (quantity) {
            if (quantity.get() < event.getQuantity()) {
                return SellResult.INSUFFICIENT;
            }
            if (eventStore.putIfAbsent(eventKey, event) != null) {
                return SellResult.DUPLICATE;
            }
            position.addEvent(event);
            quantity.addAndGet(-event.getQuantity());
            return SellResult.SUCCESS;
        }
    }
    // Attempt to cancel a trade event in the position book. It checks if the original event exists, if it has already been cancelled, and adjusts the position accordingly. It returns an appropriate CancelResult based on the outcome.
    public CancelResult tryCancel(String eventKey, TradeEvent cancelEvent) {
        TradeEvent originalEvent = eventStore.get(eventKey);
        if (originalEvent == null) {
            return CancelResult.NOT_FOUND;
        }
        if (!cancelledEvents.add(eventKey)) {
            return CancelResult.ALREADY_CANCELLED;
        }
        String positionKey = originalEvent.getAccountNumber() + ":" + originalEvent.getSecurityIdentifier();
        Position position = positionStore.get(positionKey);
        int adjustment = originalEvent.getEventType() == EventType.BUY
                ? -originalEvent.getQuantity()
                : originalEvent.getQuantity();
        position.adjustQuantity(adjustment);
        position.addEvent(cancelEvent);
        return CancelResult.SUCCESS;
    }
 
    // Retrieve all positions for a given account number. It filters the position store to find all positions that belong to the specified account and returns them as a map.
    public Map<String, Position> getPositionsByAccount(String accountNumber) {
        String prefix = accountNumber + ":";
        return positionStore.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public TradeEvent getEvent(String eventKey) {
        return eventStore.get(eventKey);
    }

    public enum SellResult {
        SUCCESS, DUPLICATE, INSUFFICIENT, NOT_FOUND
    }

    public enum CancelResult {
        SUCCESS, NOT_FOUND, ALREADY_CANCELLED
    }
}
