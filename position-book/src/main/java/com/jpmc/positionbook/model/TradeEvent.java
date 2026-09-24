package com.jpmc.positionbook.model;

import com.jpmc.positionbook.enums.EventType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TradeEvent {
    private final int id;
    private final EventType eventType;
    private final String accountNumber;
    private final String securityIdentifier;
    private final int quantity;
}
