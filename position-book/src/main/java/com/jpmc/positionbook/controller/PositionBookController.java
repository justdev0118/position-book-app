package com.jpmc.positionbook.controller;

import com.jpmc.positionbook.dto.ErrorResponse;
import com.jpmc.positionbook.dto.PositionBookRequest;
import com.jpmc.positionbook.dto.PositionResponse;
import com.jpmc.positionbook.enums.EventType;
import com.jpmc.positionbook.service.PositionBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Position Book", description = "APIs for processing trade events and querying positions")
@Validated
@RestController
@RequestMapping("/v1/position-book")
public class PositionBookController {

    private final PositionBookService positionBookService;

    public PositionBookController(PositionBookService positionBookService) {
        this.positionBookService = positionBookService;
    }

    @Operation(
        summary = "Process a trade event",
        description = "Submits a BUY, SELL, or CANCEL trade event for a given account and security. " +
                      "Each event id + accountNumber combination must be unique."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Event processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request — bad input or insufficient quantity",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No position found for the given account and security",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Duplicate event or event already cancelled",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/process/{id}")
    public ResponseEntity<Void> processTradeEvent(
            @Parameter(description = "Unique trade event ID (must be positive)", example = "1")
            @PathVariable @Positive(message = "id must be positive") int id,
            @Parameter(description = "Type of trade event", example = "BUY")
            @RequestParam EventType eventType,
            @Valid @RequestBody PositionBookRequest request) {
        positionBookService.processTradeEvent(id, eventType, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
        summary = "Get positions by account",
        description = "Returns all security positions held by the given account, including net quantity and event history."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Positions retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No positions found for the given account",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{accountNumber}")
    public ResponseEntity<List<PositionResponse>> getPositions(
            @Parameter(description = "Account number to query", example = "ACC1")
            @PathVariable @NotBlank(message = "accountNumber must not be blank") String accountNumber) {
        return ResponseEntity.ok(positionBookService.getPositions(accountNumber));
    }
}
