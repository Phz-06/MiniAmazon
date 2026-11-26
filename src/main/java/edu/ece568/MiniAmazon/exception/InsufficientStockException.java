package edu.ece568.MiniAmazon.exception;

/** A reservation asked for more units than are on hand. Maps to HTTP 409. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId) {
        super("Not enough stock for product " + productId);
    }
}
