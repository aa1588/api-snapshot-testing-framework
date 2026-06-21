package com.aiig.demo_app.service;

import com.aiig.demo_app.dto.OrderRequest;
import com.aiig.demo_app.dto.OrderResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    // Static product catalog
    private static final Map<String, ProductInfo> PRODUCTS = Map.of(
        "PROD-A1", new ProductInfo("Widget Pro", 29.99),
        "PROD-B2", new ProductInfo("Gadget Plus", 49.99)
    );

    // Static customer data
    private static final Map<String, CustomerInfo> CUSTOMERS = Map.of(
        "CUST-001", new CustomerInfo("John Doe", "john@example.com")
    );

    public OrderResponse processOrder(OrderRequest request) {
        // Dynamic values - these change every run
        Instant now = Instant.now();
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        String serverNode = "node-us-east-" + (int)(Math.random() * 10) + "a";
        String callbackUrl = "https://webhook.example.com/callback/" + UUID.randomUUID().toString().substring(0, 6);

        // Build items with dynamic processedAt
        List<OrderResponse.Item> items = request.items().stream()
            .map(item -> {
                ProductInfo product = PRODUCTS.getOrDefault(
                    item.productId(),
                    new ProductInfo("Unknown Product", 0.0)
                );
                return new OrderResponse.Item(
                    item.productId(),
                    product.name(),
                    item.quantity(),
                    product.price(),
                    Instant.now()  // Dynamic - changes every run
                );
            })
            .toList();

        // Calculate total
        double total = items.stream()
            .mapToDouble(item -> item.price() * item.quantity())
            .sum();

        // Get customer info
        CustomerInfo customerInfo = CUSTOMERS.getOrDefault(
            request.customerId(),
            new CustomerInfo("Unknown", "unknown@example.com")
        );

        return new OrderResponse(
            "ORD-" + (int)(Math.random() * 900000 + 100000),  // Static for demo, but could be dynamic
            "CONFIRMED",
            now,           // Dynamic
            now,           // Dynamic
            new OrderResponse.Metadata(
                requestId,     // Dynamic
                serverNode,    // Dynamic
                callbackUrl    // Dynamic
            ),
            new OrderResponse.Customer(
                request.customerId(),
                customerInfo.name(),
                customerInfo.email()
            ),
            items,
            Math.round(total * 100.0) / 100.0
        );
    }

    private record ProductInfo(String name, double price) {}
    private record CustomerInfo(String name, String email) {}
}
