package com.aiig.demo_app.snapshot;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Configuration for snapshot testing, loaded from YAML files.
 *
 * Configuration structure:
 *
 * src/test/resources/snapshot-config/
 *   ├── global.yml              <- Global masking rules for all endpoints
 *   └── endpoints/
 *       ├── homeowners-quote.yml    <- Endpoint-specific config
 *       ├── auto-quote.yml
 *       └── policy-retrieve.yml
 *
 * Global config (global.yml):
 * ```yaml
 * masking:
 *   paths:
 *     - $.responseId
 *     - $.correlationId
 *     - $.transactionId
 *     - $.timestamp
 * ```
 *
 * Endpoint config (homeowners-quote.yml):
 * ```yaml
 * endpoint:
 *   key: homeowners-quote
 *   method: POST
 *   url: /api/v1/quotes/homeowners
 *   requestFile: requests/homeowners-quote.json
 *
 * masking:
 *   paths:
 *     - $.quote.quoteId
 *     - $.quote.quoteNumber
 *     - $.quote.coverages[*].coverageId
 * ```
 */
public class SnapshotConfig {

    private static final String CONFIG_BASE = "src/test/resources/snapshot-config";
    private static final String APPLICATION_YML = "src/test/resources/application.yml";
    private static final String GLOBAL_CONFIG = "global.yml";
    private static final String ENDPOINTS_DIR = "endpoints";

    private final Path configBasePath;
    private final Yaml yaml;

    // Cached configurations
    private GlobalConfig globalConfig;
    private Map<String, EndpointConfig> endpointConfigs;
    private EnvironmentsConfig environmentsConfig;

    public SnapshotConfig() {
        this(Paths.get(CONFIG_BASE));
    }

    public SnapshotConfig(Path configBasePath) {
        this.configBasePath = configBasePath;
        this.yaml = new Yaml();
        this.endpointConfigs = new HashMap<>();
    }

    /**
     * Loads environment configuration from application.yml.
     */
    @SuppressWarnings("unchecked")
    public EnvironmentsConfig loadEnvironmentsConfig() throws IOException {
        if (environmentsConfig != null) {
            return environmentsConfig;
        }

        Path appYmlPath = Paths.get(APPLICATION_YML);
        if (!Files.exists(appYmlPath)) {
            throw new IllegalStateException("application.yml not found at: " + appYmlPath);
        }

        try (InputStream is = Files.newInputStream(appYmlPath)) {
            Map<String, Object> data = yaml.load(is);
            Map<String, Object> snapshot = (Map<String, Object>) data.get("snapshot");
            if (snapshot == null) {
                throw new IllegalStateException("Missing 'snapshot' section in application.yml");
            }

            Map<String, Object> environments = (Map<String, Object>) snapshot.get("environments");
            if (environments == null) {
                throw new IllegalStateException("Missing 'snapshot.environments' section in application.yml");
            }

            EnvironmentConfig baseline = parseEnvironmentConfig(
                (Map<String, Object>) environments.get("baseline"), "baseline");
            EnvironmentConfig current = parseEnvironmentConfig(
                (Map<String, Object>) environments.get("current"), "current");

            environmentsConfig = new EnvironmentsConfig(baseline, current);
            return environmentsConfig;
        }
    }

    private EnvironmentConfig parseEnvironmentConfig(Map<String, Object> data, String name) {
        if (data == null) {
            throw new IllegalStateException("Missing '" + name + "' environment config");
        }

        String url = (String) data.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Missing 'url' in " + name + " environment config");
        }

        String username = data.get("username") != null ? data.get("username").toString() : null;
        String password = data.get("password") != null ? data.get("password").toString() : null;

        return new EnvironmentConfig(url, username, password);
    }

    /**
     * Loads the global configuration.
     */
    public GlobalConfig loadGlobalConfig() throws IOException {
        if (globalConfig != null) {
            return globalConfig;
        }

        Path globalPath = configBasePath.resolve(GLOBAL_CONFIG);
        if (!Files.exists(globalPath)) {
            globalConfig = new GlobalConfig(Collections.emptyList());
            return globalConfig;
        }

        try (InputStream is = Files.newInputStream(globalPath)) {
            Map<String, Object> data = yaml.load(is);
            List<String> paths = extractMaskingPaths(data);
            globalConfig = new GlobalConfig(paths);
            return globalConfig;
        }
    }

    /**
     * Loads configuration for a specific endpoint.
     */
    public EndpointConfig loadEndpointConfig(String endpointKey) throws IOException {
        if (endpointConfigs.containsKey(endpointKey)) {
            return endpointConfigs.get(endpointKey);
        }

        Path endpointPath = configBasePath.resolve(ENDPOINTS_DIR)
            .resolve(endpointKey + ".yml");

        if (!Files.exists(endpointPath)) {
            throw new IllegalArgumentException(
                "No configuration found for endpoint: " + endpointKey +
                " (expected at: " + endpointPath + ")");
        }

        try (InputStream is = Files.newInputStream(endpointPath)) {
            Map<String, Object> data = yaml.load(is);
            EndpointConfig config = parseEndpointConfig(data, endpointKey);
            endpointConfigs.put(endpointKey, config);
            return config;
        }
    }

    /**
     * Loads all endpoint configurations from the endpoints directory.
     */
    public List<EndpointConfig> loadAllEndpointConfigs() throws IOException {
        Path endpointsDir = configBasePath.resolve(ENDPOINTS_DIR);
        if (!Files.exists(endpointsDir)) {
            return Collections.emptyList();
        }

        List<EndpointConfig> configs = new ArrayList<>();
        try (var stream = Files.list(endpointsDir)) {
            stream.filter(p -> p.toString().endsWith(".yml"))
                  .forEach(path -> {
                      String filename = path.getFileName().toString();
                      String endpointKey = filename.substring(0, filename.length() - 4);
                      try {
                          configs.add(loadEndpointConfig(endpointKey));
                      } catch (IOException e) {
                          throw new RuntimeException("Failed to load config: " + path, e);
                      }
                  });
        }
        return configs;
    }

    /**
     * Gets the combined masking paths (global + endpoint-specific).
     */
    public List<String> getCombinedMaskingPaths(String endpointKey) throws IOException {
        List<String> combined = new ArrayList<>();

        // Add global paths first
        GlobalConfig global = loadGlobalConfig();
        combined.addAll(global.maskingPaths());

        // Add endpoint-specific paths
        EndpointConfig endpoint = loadEndpointConfig(endpointKey);
        combined.addAll(endpoint.maskingPaths());

        return combined;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractMaskingPaths(Map<String, Object> data) {
        if (data == null) {
            return Collections.emptyList();
        }

        Map<String, Object> masking = (Map<String, Object>) data.get("masking");
        if (masking == null) {
            return Collections.emptyList();
        }

        List<String> paths = (List<String>) masking.get("paths");
        return paths != null ? new ArrayList<>(paths) : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private EndpointConfig parseEndpointConfig(Map<String, Object> data, String defaultKey) {
        Map<String, Object> endpoint = (Map<String, Object>) data.get("endpoint");
        if (endpoint == null) {
            throw new IllegalArgumentException("Missing 'endpoint' section in config");
        }

        String key = getStringOrDefault(endpoint, "key", defaultKey);
        String method = getStringOrDefault(endpoint, "method", "GET");
        String url = (String) endpoint.get("url");
        String requestFile = (String) endpoint.get("requestFile");
        String description = getStringOrDefault(endpoint, "description", "");

        if (url == null) {
            throw new IllegalArgumentException("Missing 'url' in endpoint config");
        }

        List<String> maskingPaths = extractMaskingPaths(data);

        return new EndpointConfig(key, method, url, requestFile, description, maskingPaths);
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Global configuration applied to all endpoints.
     */
    public record GlobalConfig(
        List<String> maskingPaths
    ) {}

    /**
     * Configuration for a specific endpoint.
     */
    public record EndpointConfig(
        String key,
        String method,
        String url,
        String requestFile,
        String description,
        List<String> maskingPaths
    ) {
        /**
         * Returns true if this endpoint requires a request body.
         */
        public boolean hasRequestBody() {
            return "POST".equalsIgnoreCase(method) ||
                   "PUT".equalsIgnoreCase(method) ||
                   "PATCH".equalsIgnoreCase(method);
        }

        @Override
        public String toString() {
            return method + " " + url + " [" + key + "]";
        }
    }

    /**
     * Environment configurations for baseline and current.
     */
    public record EnvironmentsConfig(
        EnvironmentConfig baseline,
        EnvironmentConfig current
    ) {}

    /**
     * Single environment configuration.
     */
    public record EnvironmentConfig(
        String url,
        String username,
        String password
    ) {}
}
