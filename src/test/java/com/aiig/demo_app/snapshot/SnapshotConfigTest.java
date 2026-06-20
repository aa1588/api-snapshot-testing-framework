package com.aiig.demo_app.snapshot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SnapshotConfig YAML loading.
 */
class SnapshotConfigTest {

    @Test
    void shouldLoadGlobalConfig() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        SnapshotConfig.GlobalConfig global = config.loadGlobalConfig();

        assertNotNull(global);
        assertFalse(global.maskingPaths().isEmpty(),
            "Global config should have masking paths");

        // Verify expected global paths
        assertTrue(global.maskingPaths().contains("$.responseId"));
        assertTrue(global.maskingPaths().contains("$.correlationId"));
        assertTrue(global.maskingPaths().contains("$.transactionId"));
        assertTrue(global.maskingPaths().contains("$.timestamp"));
    }

    @Test
    void shouldLoadEndpointConfig() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        SnapshotConfig.EndpointConfig endpoint =
            config.loadEndpointConfig("homeowners-quote");

        assertNotNull(endpoint);
        assertEquals("homeowners-quote", endpoint.key());
        assertEquals("POST", endpoint.method());
        assertEquals("/api/v1/quotes/homeowners", endpoint.url());
        assertEquals("requests/homeowners-quote.json", endpoint.requestFile());
        assertTrue(endpoint.hasRequestBody());

        // Verify endpoint-specific masking paths
        assertTrue(endpoint.maskingPaths().contains("$.quote.quoteId"));
        assertTrue(endpoint.maskingPaths().contains("$.quote.quoteNumber"));
        assertTrue(endpoint.maskingPaths().contains("$.quote.coverages[*].coverageId"));
    }

    @Test
    void shouldCombineGlobalAndEndpointPaths() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        List<String> combined = config.getCombinedMaskingPaths("homeowners-quote");

        // Should have global paths
        assertTrue(combined.contains("$.responseId"));
        assertTrue(combined.contains("$.correlationId"));

        // Should have endpoint-specific paths
        assertTrue(combined.contains("$.quote.quoteId"));
        assertTrue(combined.contains("$.quote.quoteNumber"));

        // Verify order: global paths come first
        int globalIndex = combined.indexOf("$.responseId");
        int endpointIndex = combined.indexOf("$.quote.quoteId");
        assertTrue(globalIndex < endpointIndex,
            "Global paths should come before endpoint paths");
    }

    @Test
    void shouldLoadAllEndpointConfigs() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        List<SnapshotConfig.EndpointConfig> endpoints = config.loadAllEndpointConfigs();

        assertFalse(endpoints.isEmpty());

        // Find homeowners-quote config
        SnapshotConfig.EndpointConfig homeowners = endpoints.stream()
            .filter(e -> "homeowners-quote".equals(e.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("homeowners-quote config not found"));

        assertEquals("POST", homeowners.method());
    }

    @Test
    void shouldThrowForMissingEndpoint() {
        SnapshotConfig config = new SnapshotConfig();

        assertThrows(IllegalArgumentException.class, () ->
            config.loadEndpointConfig("nonexistent-endpoint"));
    }

    @Test
    void shouldIdentifyRequestBodyRequirements() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        SnapshotConfig.EndpointConfig postEndpoint =
            config.loadEndpointConfig("homeowners-quote");

        assertTrue(postEndpoint.hasRequestBody(),
            "POST endpoints should require request body");
    }
}
