package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ConfirmOrderRequest {
    @NotEmpty(message = "Order ID is required")
    private long orderId;

    @NotEmpty(message = "x coordinate is required")
    private int x;

    @NotEmpty(message = "y coordinate is required")
    private int y;

}
