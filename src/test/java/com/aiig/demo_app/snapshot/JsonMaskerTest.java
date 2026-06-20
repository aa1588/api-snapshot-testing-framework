package com.aiig.demo_app.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the JsonMasker class.
 * Verifies masking works correctly for various JSONPath patterns.
 */
class JsonMaskerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldMaskSimpleRootField() throws Exception {
        String json = """
            {
              "responseId": "RSP-12345",
              "status": "SUCCESS"
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of("$.responseId"));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);
        assertEquals("<MASKED>", node.get("responseId").asText());
        assertEquals("SUCCESS", node.get("status").asText());
    }

    @Test
    void shouldMaskNestedField() throws Exception {
        String json = """
            {
              "quote": {
                "quoteId": "QUO-12345",
                "quoteNumber": "HO-123456-7890",
                "status": "QUOTED"
              }
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of(
            "$.quote.quoteId",
            "$.quote.quoteNumber"
        ));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);
        assertEquals("<MASKED>", node.path("quote").get("quoteId").asText());
        assertEquals("<MASKED>", node.path("quote").get("quoteNumber").asText());
        assertEquals("QUOTED", node.path("quote").get("status").asText());
    }

    @Test
    void shouldMaskDeeplyNestedField() throws Exception {
        String json = """
            {
              "quote": {
                "customer": {
                  "customerId": "CUS-ABCDEF",
                  "firstName": "John",
                  "creditInfo": {
                    "lastChecked": "2025-01-15T10:30:00Z"
                  }
                }
              }
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of(
            "$.quote.customer.customerId",
            "$.quote.customer.creditInfo.lastChecked"
        ));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);
        assertEquals("<MASKED>", node.at("/quote/customer/customerId").asText());
        assertEquals("<MASKED>", node.at("/quote/customer/creditInfo/lastChecked").asText());
        assertEquals("John", node.at("/quote/customer/firstName").asText());
    }

    @Test
    void shouldMaskAllArrayElements() throws Exception {
        String json = """
            {
              "coverages": [
                {"coverageId": "COV-001", "code": "DWELLING"},
                {"coverageId": "COV-002", "code": "LIABILITY"},
                {"coverageId": "COV-003", "code": "MEDICAL"}
              ]
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of("$.coverages[*].coverageId"));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);
        JsonNode coverages = node.get("coverages");

        assertEquals(3, coverages.size());
        for (int i = 0; i < coverages.size(); i++) {
            assertEquals("<MASKED>", coverages.get(i).get("coverageId").asText(),
                "Coverage " + i + " should be masked");
            // code should NOT be masked
            assertNotEquals("<MASKED>", coverages.get(i).get("code").asText());
        }
    }

    @Test
    void shouldMaskNestedArrayElements() throws Exception {
        String json = """
            {
              "quote": {
                "coverages": [
                  {
                    "coverageId": "COV-001",
                    "subCoverages": [
                      {"subCoverageId": "SUB-001"},
                      {"subCoverageId": "SUB-002"}
                    ]
                  },
                  {
                    "coverageId": "COV-002",
                    "subCoverages": [
                      {"subCoverageId": "SUB-003"}
                    ]
                  }
                ]
              }
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of(
            "$.quote.coverages[*].coverageId",
            "$.quote.coverages[*].subCoverages[*].subCoverageId"
        ));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);

        // Check coverageIds are masked
        assertEquals("<MASKED>", node.at("/quote/coverages/0/coverageId").asText());
        assertEquals("<MASKED>", node.at("/quote/coverages/1/coverageId").asText());

        // Check subCoverageIds are masked
        assertEquals("<MASKED>", node.at("/quote/coverages/0/subCoverages/0/subCoverageId").asText());
        assertEquals("<MASKED>", node.at("/quote/coverages/0/subCoverages/1/subCoverageId").asText());
        assertEquals("<MASKED>", node.at("/quote/coverages/1/subCoverages/0/subCoverageId").asText());
    }

    @Test
    void shouldMaskWithRecursiveDescent() throws Exception {
        String json = """
            {
              "timestamp": "2025-01-15T10:30:00Z",
              "nested": {
                "timestamp": "2025-01-15T10:31:00Z",
                "deeper": {
                  "timestamp": "2025-01-15T10:32:00Z"
                }
              },
              "items": [
                {"timestamp": "2025-01-15T10:33:00Z"},
                {"timestamp": "2025-01-15T10:34:00Z"}
              ]
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of("$..timestamp"));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);

        assertEquals("<MASKED>", node.get("timestamp").asText());
        assertEquals("<MASKED>", node.at("/nested/timestamp").asText());
        assertEquals("<MASKED>", node.at("/nested/deeper/timestamp").asText());
        assertEquals("<MASKED>", node.at("/items/0/timestamp").asText());
        assertEquals("<MASKED>", node.at("/items/1/timestamp").asText());
    }

    @Test
    void shouldHandleMissingPaths() throws Exception {
        String json = """
            {
              "existing": "value"
            }
            """;

        // These paths don't exist in the JSON - should not throw
        JsonMasker masker = new JsonMasker(objectMapper, List.of(
            "$.nonexistent",
            "$.deeply.nested.missing"
        ));

        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);
        assertEquals("value", node.get("existing").asText());
    }

    @Test
    void shouldPreserveFieldStructure() throws Exception {
        String json = """
            {
              "quote": {
                "quoteId": "QUO-12345",
                "premium": 1500.00,
                "active": true,
                "details": null
              }
            }
            """;

        JsonMasker masker = new JsonMasker(objectMapper, List.of("$.quote.quoteId"));
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);

        // quoteId should exist and be masked
        assertTrue(node.at("/quote").has("quoteId"),
            "quoteId field should still exist");
        assertEquals("<MASKED>", node.at("/quote/quoteId").asText());

        // Other fields should be unchanged
        assertEquals(1500.00, node.at("/quote/premium").asDouble(), 0.01);
        assertTrue(node.at("/quote/active").asBoolean());
        assertTrue(node.at("/quote/details").isNull());
    }

    @Test
    void shouldCombineGlobalAndEndpointPaths() throws Exception {
        String json = """
            {
              "responseId": "RSP-12345",
              "correlationId": "abc-123",
              "quote": {
                "quoteNumber": "HO-123"
              }
            }
            """;

        // Global paths
        JsonMasker globalMasker = new JsonMasker(objectMapper, List.of(
            "$.responseId",
            "$.correlationId"
        ));

        // Add endpoint-specific paths
        JsonMasker combinedMasker = globalMasker.withAdditionalPaths(List.of(
            "$.quote.quoteNumber"
        ));

        String masked = combinedMasker.mask(json);
        JsonNode node = objectMapper.readTree(masked);

        assertEquals("<MASKED>", node.get("responseId").asText());
        assertEquals("<MASKED>", node.get("correlationId").asText());
        assertEquals("<MASKED>", node.at("/quote/quoteNumber").asText());
    }

    @Test
    void shouldMaskMultipleDynamicFields() throws Exception {
        // This simulates a realistic INOW response with many dynamic fields
        String json = """
            {
              "responseId": "RSP-ABC123",
              "correlationId": "550e8400-e29b-41d4-a716-446655440000",
              "transactionId": "TXN-XYZ789",
              "timestamp": "2025-01-15T10:30:00Z",
              "quote": {
                "quoteId": "QUO-DEF456",
                "quoteNumber": "HO-1234567890-9999",
                "createdAt": "2025-01-15T10:30:00Z",
                "customer": {
                  "customerId": "CUS-GHI012",
                  "firstName": "John"
                },
                "coverages": [
                  {
                    "coverageId": "COV-111",
                    "code": "DWELLING",
                    "premium": 750.00
                  },
                  {
                    "coverageId": "COV-222",
                    "code": "LIABILITY",
                    "premium": 250.00
                  }
                ],
                "discounts": [
                  {
                    "discountId": "DSC-AAA",
                    "appliedAt": "2025-01-15T10:30:05Z",
                    "discountCode": "ALARM"
                  }
                ]
              }
            }
            """;

        List<String> maskPaths = List.of(
            "$.responseId",
            "$.correlationId",
            "$.transactionId",
            "$.timestamp",
            "$.quote.quoteId",
            "$.quote.quoteNumber",
            "$.quote.createdAt",
            "$.quote.customer.customerId",
            "$.quote.coverages[*].coverageId",
            "$.quote.discounts[*].discountId",
            "$.quote.discounts[*].appliedAt"
        );

        JsonMasker masker = new JsonMasker(objectMapper, maskPaths);
        String masked = masker.mask(json);

        JsonNode node = objectMapper.readTree(masked);

        // All dynamic fields should be masked
        assertEquals("<MASKED>", node.get("responseId").asText());
        assertEquals("<MASKED>", node.get("correlationId").asText());
        assertEquals("<MASKED>", node.get("transactionId").asText());
        assertEquals("<MASKED>", node.get("timestamp").asText());
        assertEquals("<MASKED>", node.at("/quote/quoteId").asText());
        assertEquals("<MASKED>", node.at("/quote/quoteNumber").asText());
        assertEquals("<MASKED>", node.at("/quote/createdAt").asText());
        assertEquals("<MASKED>", node.at("/quote/customer/customerId").asText());
        assertEquals("<MASKED>", node.at("/quote/coverages/0/coverageId").asText());
        assertEquals("<MASKED>", node.at("/quote/coverages/1/coverageId").asText());
        assertEquals("<MASKED>", node.at("/quote/discounts/0/discountId").asText());
        assertEquals("<MASKED>", node.at("/quote/discounts/0/appliedAt").asText());

        // Static fields should NOT be masked
        assertEquals("John", node.at("/quote/customer/firstName").asText());
        assertEquals("DWELLING", node.at("/quote/coverages/0/code").asText());
        assertEquals(750.00, node.at("/quote/coverages/0/premium").asDouble(), 0.01);
        assertEquals("ALARM", node.at("/quote/discounts/0/discountCode").asText());
    }
}
