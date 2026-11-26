package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmOrderRequest {
    @NotNull(message = "Order ID is required")
    private long orderId;

    @NotNull(message = "x coordinate is required")
    private int x;

    @NotNull(message = "y coordinate is required")
    private int y;

}
