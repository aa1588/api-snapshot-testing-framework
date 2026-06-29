package com.aiig.demo_app;

import com.aiig.demo_app.snapshot.SnapshotConfig;
import com.aiig.demo_app.snapshot.SnapshotConfig.EndpointConfig;
import com.aiig.demo_app.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized snapshot tests for all API endpoints using REAL HTTP calls.
 *
 * Supports two environments:
 * - Baseline: Used when capturing snapshots (-Dsnapshot.update=true)
 * - Current: Used when verifying against baselines
 *
 * Configure environments in application.yml:
 *   snapshot.environments.baseline.url
 *   snapshot.environments.current.url
 */
@SpringBootTest(
    classes = DemoAppApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("API Snapshot Tests")
@Epic("API Regression Testing")
@Feature("Snapshot Verification")
class ApiSnapshotTest {

    private static final Logger log = LoggerFactory.getLogger(ApiSnapshotTest.class);

    @LocalServerPort
    private int port;

    // Baseline environment (for capturing snapshots)
    @Value("${snapshot.environments.baseline.url:#{null}}")
    private String baselineUrl;

    @Value("${snapshot.environments.baseline.username:${spring.security.user.name}}")
    private String baselineUsername;

    @Value("${snapshot.environments.baseline.password:${spring.security.user.password}}")
    private String baselinePassword;

    // Current environment (for verification)
    @Value("${snapshot.environments.current.url:#{null}}")
    private String currentUrl;

    @Value("${snapshot.environments.current.username:${spring.security.user.name}}")
    private String currentUsername;

    @Value("${snapshot.environments.current.password:${spring.security.user.password}}")
    private String currentPassword;

    private RestClient baselineClient;
    private RestClient currentClient;
    private SnapshotManager snapshotManager;
    private SnapshotComparator comparator;
    private SnapshotConfig config;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Resolve URLs - use localhost if not configured
        String resolvedBaselineUrl = resolveUrl(baselineUrl);
        String resolvedCurrentUrl = resolveUrl(currentUrl);

        // Create clients for each environment
        baselineClient = createRestClient(resolvedBaselineUrl, baselineUsername, baselinePassword);
        currentClient = createRestClient(resolvedCurrentUrl, currentUsername, currentPassword);

        snapshotManager = new SnapshotManager();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        comparator = new SnapshotComparator(objectMapper);
        config = new SnapshotConfig();

        log.info("========================================");
        log.info("Baseline environment: {}", resolvedBaselineUrl);
        log.info("Current environment:  {}", resolvedCurrentUrl);
        log.info("========================================");
    }

    private String resolveUrl(String url) {
        if (url == null || url.contains("${local.server.port}")) {
            return "http://localhost:" + port;
        }
        return url;
    }

    private RestClient createRestClient(String baseUrl, String username, String password) {
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
            .build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointProvider")
    @DisplayName("Snapshot verification")
    @Severity(SeverityLevel.CRITICAL)
    void verifyEndpointSnapshot(EndpointConfig endpoint) throws Exception {
        // Filter by endpoint key if specified via -Dsnapshot.endpoint=<key>
        String filterEndpoint = System.getProperty("snapshot.endpoint");
        if (filterEndpoint != null && !filterEndpoint.isEmpty()
                && !filterEndpoint.equals(endpoint.key())) {
            log.info("Skipping endpoint: {} (filter: {})", endpoint.key(), filterEndpoint);
            return; // Skip this endpoint
        }

        // Set Allure metadata dynamically
        Allure.epic("API Regression Testing");
        Allure.feature(endpoint.key());
        Allure.story(endpoint.method() + " " + endpoint.url());
        Allure.description(endpoint.description() != null ? endpoint.description() : "Snapshot test for " + endpoint.key());

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ TESTING ENDPOINT: {}", endpoint.key());
        log.info("│ {} {}", endpoint.method(), endpoint.url());
        log.info("└─────────────────────────────────────────────────────────────");

        // 1. Load request body
        String requestBody = null;
        if (endpoint.hasRequestBody() && endpoint.requestFile() != null) {
            Path requestPath = Path.of("src/test/resources").resolve(endpoint.requestFile());
            log.info("[1/6] Loading request body from: {}", requestPath);
            requestBody = Files.readString(requestPath);
            log.info("      Request body size: {} characters", requestBody.length());
            attachJson("Request Body", requestBody);
        } else {
            log.info("[1/6] No request body required for {} method", endpoint.method());
        }

        // 2. Choose client based on mode
        boolean isUpdateMode = snapshotManager.isUpdateMode();
        boolean noBaselineExists = !snapshotManager.hasApprovedSnapshot(endpoint.key());

        // Use baseline client when capturing, current client when verifying
        RestClient activeClient = (isUpdateMode || noBaselineExists) ? baselineClient : currentClient;
        String envName = (isUpdateMode || noBaselineExists) ? "BASELINE" : "CURRENT";

        String url = endpoint.url();
        log.info("[2/6] Making HTTP request to {} environment: {} {}", envName, endpoint.method(), url);

        long startTime = System.currentTimeMillis();
        String actualResponse = activeClient.method(HttpMethod.valueOf(endpoint.method()))
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(actualResponse, "Response body should not be null");
        log.info("      Response received: {} characters in {} ms", actualResponse.length(), duration);

        Allure.step("HTTP " + endpoint.method() + " " + endpoint.url() + " [" + envName + "] (" + duration + "ms)");

        // 3. Load masking configuration
        log.info("[3/6] Loading masking configuration...");
        List<String> maskingPaths = config.getCombinedMaskingPaths(endpoint.key());
        log.info("      Global masks: {} paths", config.loadGlobalConfig().maskingPaths().size());
        log.info("      Endpoint masks: {} paths", endpoint.maskingPaths().size());
        log.info("      Total masks to apply: {} paths", maskingPaths.size());

        // 4. Apply masking
        log.info("[4/6] Applying masks to response...");
        JsonMasker masker = new JsonMasker(objectMapper, maskingPaths);
        String maskedResponse = masker.mask(actualResponse);
        log.info("      Masking complete");
        attachJson("Masked Response", maskedResponse);

        // 5. Save received snapshot
        log.info("[5/6] Saving received snapshot...");
        snapshotManager.saveReceived(endpoint.key(), maskedResponse);
        log.info("      Saved to: {}", snapshotManager.getReceivedPath(endpoint.key()));

        // 6. Compare or approve
        log.info("[6/6] Checking snapshot status...");

        if (isUpdateMode) {
            log.info("      Mode: UPDATE (capturing from BASELINE environment)");
            snapshotManager.saveRaw(endpoint.key(), actualResponse);
            snapshotManager.approve(endpoint.key());
            log.info("      ✓ Raw snapshot saved to: {}", snapshotManager.getRawPath(endpoint.key()));
            log.info("      ✓ Masked snapshot saved to: {}", snapshotManager.getApprovedPath(endpoint.key()));
            Allure.step("Snapshot UPDATED (baseline captured)");
            logSuccess(endpoint);
            return;
        }

        if (noBaselineExists) {
            log.info("      No approved snapshot exists - creating initial baseline");
            snapshotManager.saveRaw(endpoint.key(), actualResponse);
            snapshotManager.approve(endpoint.key());
            log.info("      ✓ Raw snapshot saved to: {}", snapshotManager.getRawPath(endpoint.key()));
            log.info("      ✓ Masked snapshot saved to: {}", snapshotManager.getApprovedPath(endpoint.key()));
            Allure.step("Initial snapshot CREATED");
            logSuccess(endpoint);
            return;
        }

        log.info("      Mode: VERIFY (comparing CURRENT against BASELINE snapshot)");
        log.info("      Approved snapshot: {}", snapshotManager.getApprovedPath(endpoint.key()));

        String approvedResponse = snapshotManager.loadApproved(endpoint.key());
        attachJson("Approved Snapshot", approvedResponse);

        SnapshotComparator.ComparisonResult result = comparator.compare(approvedResponse, maskedResponse);

        if (result.matches()) {
            snapshotManager.deleteReceived(endpoint.key());
            log.info("      ✓ Snapshot matches!");
            Allure.step("Snapshot MATCHED");
            logSuccess(endpoint);
        } else {
            log.error("      ✗ Snapshot mismatch detected!");
            log.error("      Received snapshot kept at: {}", snapshotManager.getReceivedPath(endpoint.key()));
            log.error("      Run with -Dsnapshot.update=true to approve changes");
            attachText("Diff Report", result.formatDiffReport());
            Allure.step("Snapshot MISMATCH - see diff report");
            logFailure(endpoint);
            fail("Snapshot mismatch for: " + endpoint.key() + "\n" + result.formatDiffReport());
        }
    }

    @Attachment(value = "{name}", type = "application/json")
    private String attachJson(String name, String content) {
        return content;
    }

    @Attachment(value = "{name}", type = "text/plain")
    private String attachText(String name, String content) {
        return content;
    }

    private void logSuccess(EndpointConfig endpoint) {
        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ ✓ PASSED: {}", endpoint.key());
        log.info("└─────────────────────────────────────────────────────────────");
        log.info("");
    }

    private void logFailure(EndpointConfig endpoint) {
        log.error("");
        log.error("┌─────────────────────────────────────────────────────────────");
        log.error("│ ✗ FAILED: {}", endpoint.key());
        log.error("└─────────────────────────────────────────────────────────────");
        log.error("");
    }

    static Stream<EndpointConfig> endpointProvider() throws IOException {
        return new SnapshotConfig().loadAllEndpointConfigs().stream();
    }
}
