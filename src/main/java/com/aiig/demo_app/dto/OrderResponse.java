package com.aiig.demo_app.dto;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderId,
    String status,
    Instant createdAt,
    Instant processedAt,
    Metadata metadata,
    Customer customer,
    List<Item> items,
    double total
) {
    public record Metadata(
        String requestId,
        String serverNode,
        String callbackUrl
    ) {}

    public record Customer(
        String id,
        String name,
        String email
    ) {}

    public record Item(
        String productId,
        String name,
        int quantity,
        double price,
        Instant processedAt
    ) {}
}
