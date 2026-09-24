package com.jpmc.positionbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Trade event request body")
public class PositionBookRequest {

    @Schema(description = "Account number the trade belongs to", example = "ACC1")
    @NotBlank(message = "accountNumber is required")
    private String accountNumber;

    @Schema(description = "Security identifier (e.g. ISIN or ticker)", example = "SEC1")
    @NotBlank(message = "securityIdentifier is required")
    private String securityIdentifier;

    @Schema(description = "Number of securities. Must be positive for BUY/SELL; zero is accepted for CANCEL.", example = "100")
    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity must be zero or positive")
    private Integer quantity;
}
