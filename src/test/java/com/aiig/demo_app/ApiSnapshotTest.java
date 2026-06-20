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

    @Value("${spring.security.user.name}")
    private String username;

    @Value("${spring.security.user.password}")
    private String password;

    private RestClient restClient;
    private SnapshotManager snapshotManager;
    private SnapshotComparator comparator;
    private SnapshotConfig config;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Create Basic Auth header
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
            .build();
        snapshotManager = new SnapshotManager();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        comparator = new SnapshotComparator(objectMapper);
        config = new SnapshotConfig();

        log.info("========================================");
        log.info("Test server running on port: {}", port);
        log.info("========================================");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointProvider")
    @DisplayName("Snapshot verification")
    @Severity(SeverityLevel.CRITICAL)
    void verifyEndpointSnapshot(EndpointConfig endpoint) throws Exception {
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

        // 2. Make real HTTP call
        String url = endpoint.url();
        log.info("[2/6] Making HTTP request: {} http://localhost:{}{}", endpoint.method(), port, url);

        long startTime = System.currentTimeMillis();
        String actualResponse = restClient.method(HttpMethod.valueOf(endpoint.method()))
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(actualResponse, "Response body should not be null");
        log.info("      Response received: {} characters in {} ms", actualResponse.length(), duration);

        Allure.step("HTTP " + endpoint.method() + " " + endpoint.url() + " (" + duration + "ms)");

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

        if (snapshotManager.isUpdateMode()) {
            log.info("      Mode: UPDATE (approved snapshot will be overwritten)");
            snapshotManager.approve(endpoint.key());
            log.info("      ✓ Approved snapshot saved to: {}", snapshotManager.getApprovedPath(endpoint.key()));
            Allure.step("Snapshot UPDATED (baseline captured)");
            logSuccess(endpoint);
            return;
        }

        if (!snapshotManager.hasApprovedSnapshot(endpoint.key())) {
            log.info("      No approved snapshot exists - creating initial baseline");
            snapshotManager.approve(endpoint.key());
            log.info("      ✓ Initial snapshot saved to: {}", snapshotManager.getApprovedPath(endpoint.key()));
            Allure.step("Initial snapshot CREATED");
            logSuccess(endpoint);
            return;
        }

        log.info("      Mode: VERIFY (comparing against approved snapshot)");
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
