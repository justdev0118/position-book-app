package com.jpmc.positionbook.dto;

import com.jpmc.positionbook.enums.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "A single trade event in the position history")
public class EventHistoryItem {

    @Schema(description = "Trade event ID", example = "1")
    private final int id;

    @Schema(description = "Event type", example = "BUY")
    private final EventType eventType;

    @Schema(description = "Quantity involved in this event", example = "100")
    private final int quantity;
}
