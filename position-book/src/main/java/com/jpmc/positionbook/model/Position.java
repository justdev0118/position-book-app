package com.jpmc.positionbook.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Position {

    private final AtomicInteger quantity = new AtomicInteger(0);
    private final CopyOnWriteArrayList<TradeEvent> eventHistory = new CopyOnWriteArrayList<>();

    public AtomicInteger getQuantityRef() {
        return quantity;
    }

    public int getQuantity() {
        return quantity.get();
    }

    public void adjustQuantity(int qnt) {
        quantity.addAndGet(qnt);
    }

    public void addEvent(TradeEvent event) {
        eventHistory.add(event);
    }

    public List<TradeEvent> getEventHistory() {
        return eventHistory;
    }
}
