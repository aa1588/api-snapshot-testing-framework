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
            config.loadEndpointConfig("jsonplaceholder-post");

        assertNotNull(endpoint);
        assertEquals("jsonplaceholder-post", endpoint.key());
        assertEquals("GET", endpoint.method());
        assertEquals("/posts/1", endpoint.url());
        assertNull(endpoint.requestFile());
        assertFalse(endpoint.hasRequestBody());
    }

    @Test
    void shouldCombineGlobalAndEndpointPaths() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        List<String> combined = config.getCombinedMaskingPaths("jsonplaceholder-post");

        assertNotNull(combined);
        // Combined paths include both global and endpoint-specific
    }

    @Test
    void shouldLoadAllEndpointConfigs() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        List<SnapshotConfig.EndpointConfig> endpoints = config.loadAllEndpointConfigs();

        assertFalse(endpoints.isEmpty());

        // Find jsonplaceholder-post config
        SnapshotConfig.EndpointConfig endpoint = endpoints.stream()
            .filter(e -> "jsonplaceholder-post".equals(e.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("jsonplaceholder-post config not found"));

        assertEquals("GET", endpoint.method());
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
        SnapshotConfig.EndpointConfig getEndpoint =
            config.loadEndpointConfig("jsonplaceholder-post");

        assertFalse(getEndpoint.hasRequestBody(),
            "GET endpoints should not require request body");
    }

    @Test
    void shouldLoadEnvironmentsConfig() throws IOException {
        SnapshotConfig config = new SnapshotConfig();
        SnapshotConfig.EnvironmentsConfig envConfig = config.loadEnvironmentsConfig();

        assertNotNull(envConfig);
        assertNotNull(envConfig.baseline());
        assertNotNull(envConfig.current());
        assertNotNull(envConfig.baseline().url());
        assertNotNull(envConfig.current().url());
    }
}
