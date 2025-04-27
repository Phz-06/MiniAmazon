package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotNull(message = "Order ID is required")
    private long orderId;

    @NotEmpty(message = "Credit card number is required")
    private String creditCardNumber;

    @NotNull
    private BigDecimal amount;
}
