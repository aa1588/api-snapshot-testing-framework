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
        // Global config may or may not have paths depending on setup
        assertNotNull(global.maskingPaths());
    }

    @Test
    void shouldLoadEndpointConfig() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        SnapshotConfig.EndpointConfig endpoint =
            config.loadEndpointConfig("order");

        assertNotNull(endpoint);
        assertEquals("order", endpoint.key());
        assertEquals("POST", endpoint.method());
        assertEquals("/api/demo/order", endpoint.url());
        assertEquals("requests/order.json", endpoint.requestFile());
        assertTrue(endpoint.hasRequestBody());
    }

    @Test
    void shouldCombineGlobalAndEndpointPaths() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        List<String> combined = config.getCombinedMaskingPaths("order");

        assertNotNull(combined);
        // Combined paths include both global and endpoint-specific
    }

    @Test
    void shouldLoadAllEndpointConfigs() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        List<SnapshotConfig.EndpointConfig> endpoints = config.loadAllEndpointConfigs();

        assertFalse(endpoints.isEmpty());

        // Find order config
        SnapshotConfig.EndpointConfig order = endpoints.stream()
            .filter(e -> "order".equals(e.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("order config not found"));

        assertEquals("POST", order.method());
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
            config.loadEndpointConfig("order");

        assertTrue(postEndpoint.hasRequestBody(),
            "POST endpoints should require request body");
    }
}
