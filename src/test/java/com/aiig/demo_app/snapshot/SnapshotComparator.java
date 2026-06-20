package com.aiig.demo_app.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;

import java.util.*;

/**
 * Compares JSON snapshots and produces readable diff reports.
 *
 * Features:
 * - Structural comparison (missing/extra fields)
 * - Value comparison with path context
 * - Array element comparison with index tracking
 * - Human-readable diff output
 */
public class SnapshotComparator {

    private final ObjectMapper objectMapper;

    public SnapshotComparator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Compares two JSON strings and returns a detailed result.
     */
    public ComparisonResult compare(String expected, String actual) {
        try {
            JSONCompareResult jsonResult = JSONCompare.compareJSON(
                expected, actual, JSONCompareMode.STRICT);

            if (jsonResult.passed()) {
                return ComparisonResult.match();
            }

            // Build detailed diff
            JsonNode expectedNode = objectMapper.readTree(expected);
            JsonNode actualNode = objectMapper.readTree(actual);

            List<Difference> differences = findDifferences(
                expectedNode, actualNode, "$");

            return ComparisonResult.mismatch(differences, jsonResult.getMessage());

        } catch (Exception e) {
            return ComparisonResult.error(
                "Failed to compare JSON: " + e.getMessage());
        }
    }

    /**
     * Recursively finds all differences between two JSON nodes.
     */
    private List<Difference> findDifferences(JsonNode expected, JsonNode actual,
                                              String path) {
        List<Difference> differences = new ArrayList<>();

        if (expected == null && actual == null) {
            return differences;
        }

        if (expected == null) {
            differences.add(new Difference(path, DifferenceType.ADDED,
                null, nodeToString(actual)));
            return differences;
        }

        if (actual == null) {
            differences.add(new Difference(path, DifferenceType.REMOVED,
                nodeToString(expected), null));
            return differences;
        }

        // Type mismatch
        if (!expected.getNodeType().equals(actual.getNodeType())) {
            differences.add(new Difference(path, DifferenceType.TYPE_CHANGED,
                expected.getNodeType().toString(),
                actual.getNodeType().toString()));
            return differences;
        }

        // Object comparison
        if (expected.isObject()) {
            differences.addAll(compareObjects(
                (ObjectNode) expected, (ObjectNode) actual, path));
        }
        // Array comparison
        else if (expected.isArray()) {
            differences.addAll(compareArrays(
                (ArrayNode) expected, (ArrayNode) actual, path));
        }
        // Value comparison
        else if (!expected.equals(actual)) {
            differences.add(new Difference(path, DifferenceType.VALUE_CHANGED,
                nodeToString(expected), nodeToString(actual)));
        }

        return differences;
    }

    private List<Difference> compareObjects(ObjectNode expected, ObjectNode actual,
                                             String path) {
        List<Difference> differences = new ArrayList<>();

        Set<String> allKeys = new TreeSet<>();
        expected.fieldNames().forEachRemaining(allKeys::add);
        actual.fieldNames().forEachRemaining(allKeys::add);

        for (String key : allKeys) {
            String childPath = path + "." + key;
            JsonNode expectedChild = expected.get(key);
            JsonNode actualChild = actual.get(key);

            if (expectedChild == null) {
                differences.add(new Difference(childPath, DifferenceType.ADDED,
                    null, nodeToString(actualChild)));
            } else if (actualChild == null) {
                differences.add(new Difference(childPath, DifferenceType.REMOVED,
                    nodeToString(expectedChild), null));
            } else {
                differences.addAll(findDifferences(expectedChild, actualChild, childPath));
            }
        }

        return differences;
    }

    private List<Difference> compareArrays(ArrayNode expected, ArrayNode actual,
                                            String path) {
        List<Difference> differences = new ArrayList<>();

        int maxSize = Math.max(expected.size(), actual.size());

        for (int i = 0; i < maxSize; i++) {
            String indexPath = path + "[" + i + "]";

            if (i >= expected.size()) {
                differences.add(new Difference(indexPath, DifferenceType.ADDED,
                    null, nodeToString(actual.get(i))));
            } else if (i >= actual.size()) {
                differences.add(new Difference(indexPath, DifferenceType.REMOVED,
                    nodeToString(expected.get(i)), null));
            } else {
                differences.addAll(findDifferences(
                    expected.get(i), actual.get(i), indexPath));
            }
        }

        return differences;
    }

    private String nodeToString(JsonNode node) {
        if (node == null) {
            return "null";
        }
        if (node.isTextual()) {
            return "\"" + node.asText() + "\"";
        }
        return node.toString();
    }

    /**
     * Result of a snapshot comparison.
     */
    public static class ComparisonResult {
        private final boolean matches;
        private final List<Difference> differences;
        private final String summary;
        private final String error;

        private ComparisonResult(boolean matches, List<Difference> differences,
                                  String summary, String error) {
            this.matches = matches;
            this.differences = differences != null ? differences : Collections.emptyList();
            this.summary = summary;
            this.error = error;
        }

        public static ComparisonResult match() {
            return new ComparisonResult(true, null, null, null);
        }

        public static ComparisonResult mismatch(List<Difference> differences,
                                                 String summary) {
            return new ComparisonResult(false, differences, summary, null);
        }

        public static ComparisonResult error(String error) {
            return new ComparisonResult(false, null, null, error);
        }

        public boolean matches() {
            return matches;
        }

        public List<Difference> getDifferences() {
            return differences;
        }

        public String getSummary() {
            return summary;
        }

        public String getError() {
            return error;
        }

        public boolean hasError() {
            return error != null;
        }

        /**
         * Formats the differences as a readable report.
         */
        public String formatDiffReport() {
            if (matches) {
                return "Snapshots match.";
            }

            if (hasError()) {
                return "Error: " + error;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Snapshot comparison failed with ")
              .append(differences.size())
              .append(" difference(s):\n\n");

            // Group by difference type
            Map<DifferenceType, List<Difference>> byType = new LinkedHashMap<>();
            for (DifferenceType type : DifferenceType.values()) {
                byType.put(type, new ArrayList<>());
            }
            for (Difference diff : differences) {
                byType.get(diff.type()).add(diff);
            }

            // Report structural changes first (most important)
            appendDifferenceGroup(sb, "REMOVED FIELDS", byType.get(DifferenceType.REMOVED));
            appendDifferenceGroup(sb, "ADDED FIELDS", byType.get(DifferenceType.ADDED));
            appendDifferenceGroup(sb, "TYPE CHANGES", byType.get(DifferenceType.TYPE_CHANGED));
            appendDifferenceGroup(sb, "VALUE CHANGES", byType.get(DifferenceType.VALUE_CHANGED));

            sb.append("\n--- JSONAssert Summary ---\n");
            sb.append(summary);

            return sb.toString();
        }

        private void appendDifferenceGroup(StringBuilder sb, String header,
                                            List<Difference> diffs) {
            if (diffs.isEmpty()) {
                return;
            }

            sb.append("=== ").append(header).append(" ===\n");
            for (Difference diff : diffs) {
                sb.append("  ").append(diff.path()).append("\n");
                if (diff.expected() != null) {
                    sb.append("    Expected: ").append(truncate(diff.expected(), 100)).append("\n");
                }
                if (diff.actual() != null) {
                    sb.append("    Actual:   ").append(truncate(diff.actual(), 100)).append("\n");
                }
            }
            sb.append("\n");
        }

        private String truncate(String value, int maxLength) {
            if (value.length() <= maxLength) {
                return value;
            }
            return value.substring(0, maxLength) + "...";
        }
    }

    /**
     * Represents a single difference between expected and actual JSON.
     */
    public record Difference(
        String path,
        DifferenceType type,
        String expected,
        String actual
    ) {}

    /**
     * Types of differences that can be detected.
     */
    public enum DifferenceType {
        REMOVED,        // Field exists in expected but not in actual
        ADDED,          // Field exists in actual but not in expected
        TYPE_CHANGED,   // Field type changed (e.g., string -> number)
        VALUE_CHANGED   // Field value changed
    }
}
