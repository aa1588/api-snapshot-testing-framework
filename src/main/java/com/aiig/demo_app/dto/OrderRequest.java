package com.aiig.demo_app.dto;

import java.util.List;

public record OrderRequest(
    String customerId,
    List<OrderItem> items
) {
    public record OrderItem(
        String productId,
        int quantity
    ) {}
}
