package com.aiig.demo_app.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages snapshot file storage and retrieval for approval testing.
 *
 * Directory structure:
 * src/test/resources/snapshots/
 *   ├── approved/           <- Committed to Git (source of truth)
 *   │   ├── {endpoint-key}.json       <- Masked response (used for comparison)
 *   │   └── {endpoint-key}.raw.json   <- Original unmasked response (for records)
 *   └── received/           <- Git-ignored (transient comparison files)
 *       └── {endpoint-key}.json
 *
 * The approved directory contains the "golden master" responses.
 * The received directory contains the latest test responses for comparison.
 */
public class SnapshotManager {

    private static final String SNAPSHOTS_BASE = "src/test/resources/snapshots";
    private static final String APPROVED_DIR = "approved";
    private static final String RECEIVED_DIR = "received";

    private final ObjectMapper objectMapper;
    private final Path basePath;

    public SnapshotManager() {
        this(Paths.get(SNAPSHOTS_BASE));
    }

    public SnapshotManager(Path basePath) {
        this.basePath = basePath;
        this.objectMapper = createObjectMapper();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }

    /**
     * Normalizes JSON to a consistent, comparable format.
     * - Pretty-printed with consistent indentation
     * - Keys sorted alphabetically for stable comparison
     */
    public String normalizeJson(String json) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        return objectMapper.writeValueAsString(node);
    }

    /**
     * Saves the received (actual) response for an endpoint.
     * This is the response from the current test run.
     */
    public void saveReceived(String endpointKey, String jsonContent) throws IOException {
        Path receivedPath = getReceivedPath(endpointKey);
        Files.createDirectories(receivedPath.getParent());
        Files.writeString(receivedPath, normalizeJson(jsonContent));
    }

    /**
     * Saves the raw (unmasked) response for an endpoint.
     * This is kept for records alongside the masked version.
     */
    public void saveRaw(String endpointKey, String jsonContent) throws IOException {
        Path rawPath = getRawPath(endpointKey);
        Files.createDirectories(rawPath.getParent());
        Files.writeString(rawPath, normalizeJson(jsonContent));
    }

    /**
     * Loads the approved (expected) snapshot for an endpoint.
     * Returns null if no approved snapshot exists yet.
     */
    public String loadApproved(String endpointKey) throws IOException {
        Path approvedPath = getApprovedPath(endpointKey);
        if (!Files.exists(approvedPath)) {
            return null;
        }
        return Files.readString(approvedPath);
    }

    /**
     * Approves the current received snapshot, making it the new baseline.
     * This copies the received file to the approved directory.
     */
    public void approve(String endpointKey) throws IOException {
        Path receivedPath = getReceivedPath(endpointKey);
        Path approvedPath = getApprovedPath(endpointKey);

        if (!Files.exists(receivedPath)) {
            throw new IllegalStateException(
                "No received snapshot to approve for: " + endpointKey);
        }

        Files.createDirectories(approvedPath.getParent());
        Files.copy(receivedPath, approvedPath,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Checks if an approved snapshot exists for the endpoint.
     */
    public boolean hasApprovedSnapshot(String endpointKey) {
        return Files.exists(getApprovedPath(endpointKey));
    }

    /**
     * Deletes the received snapshot (cleanup after successful test).
     */
    public void deleteReceived(String endpointKey) throws IOException {
        Path receivedPath = getReceivedPath(endpointKey);
        Files.deleteIfExists(receivedPath);
    }

    /**
     * Returns the path to the approved snapshot file.
     */
    public Path getApprovedPath(String endpointKey) {
        return basePath.resolve(APPROVED_DIR).resolve(endpointKey + ".json");
    }

    /**
     * Returns the path to the received snapshot file.
     */
    public Path getReceivedPath(String endpointKey) {
        return basePath.resolve(RECEIVED_DIR).resolve(endpointKey + ".json");
    }

    /**
     * Returns the path to the raw (unmasked) snapshot file.
     */
    public Path getRawPath(String endpointKey) {
        return basePath.resolve(APPROVED_DIR).resolve(endpointKey + ".raw.json");
    }

    /**
     * Checks if we're in "update mode" where snapshots should be auto-approved.
     * Enabled via system property: -Dsnapshot.update=true
     */
    public boolean isUpdateMode() {
        String updateMode = System.getProperty("snapshot.update", "false");
        return "true".equalsIgnoreCase(updateMode);
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
