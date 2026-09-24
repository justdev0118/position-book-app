package com.jpmc.positionbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Current position for an account and security, including full event history")
public class PositionResponse {

    @Schema(description = "Account number", example = "ACC1")
    private final String accountNumber;

    @Schema(description = "Security identifier", example = "SEC1")
    private final String securityIdentifier;

    @Schema(description = "Current net quantity held", example = "60")
    private final int netQuantity;

    @Schema(description = "Ordered list of all trade events that affected this position")
    private final List<EventHistoryItem> eventHistory;
}
