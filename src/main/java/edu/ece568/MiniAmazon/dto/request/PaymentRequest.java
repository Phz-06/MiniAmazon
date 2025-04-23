package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotEmpty(message = "Order ID is required")
    private String orderId;

    @NotEmpty(message = "Credit card number is required")
    private String creditCardNumber;

    @NotEmpty(message = "Amount is required")
    private BigDecimal amount;
}
