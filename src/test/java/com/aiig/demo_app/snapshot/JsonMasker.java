package com.aiig.demo_app.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks dynamic fields in JSON using JSONPath expressions.
 *
 * Masking replaces field VALUES with a placeholder like "<MASKED>",
 * while preserving the field structure. This ensures:
 *
 * 1. The field still exists in the comparison
 * 2. Structural changes (missing field, renamed field) are detected
 * 3. Type changes are detected (if masking fails due to type mismatch)
 * 4. Only the dynamic value is ignored
 *
 * Supported JSONPath patterns:
 * - $.responseId                          (single field)
 * - $.quote.quoteNumber                   (nested field)
 * - $.quote.coverages[*].coverageId       (all array elements)
 * - $.quote.coverages[0].coverageId       (specific array index)
 * - $..timestamp                          (recursive descent)
 */
public class JsonMasker {

    public static final String MASKED_VALUE = "<MASKED>";

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;
    private final List<String> maskingPaths;

    public JsonMasker(ObjectMapper objectMapper, List<String> maskingPaths) {
        this.objectMapper = objectMapper;
        this.maskingPaths = maskingPaths != null ? maskingPaths : new ArrayList<>();

        // Configure JsonPath to work with Jackson JsonNode
        this.jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper))
            .mappingProvider(new JacksonMappingProvider(objectMapper))
            .options(Option.SUPPRESS_EXCEPTIONS) // Don't throw if path not found
            .build();
    }

    /**
     * Creates a new JsonMasker with additional paths added.
     */
    public JsonMasker withAdditionalPaths(List<String> additionalPaths) {
        if (additionalPaths == null || additionalPaths.isEmpty()) {
            return this;
        }
        List<String> combined = new ArrayList<>(this.maskingPaths);
        combined.addAll(additionalPaths);
        return new JsonMasker(objectMapper, combined);
    }

    /**
     * Masks dynamic fields in the JSON string.
     * Returns the masked JSON string.
     */
    public String mask(String json) throws JsonProcessingException {
        if (maskingPaths.isEmpty()) {
            return json;
        }

        JsonNode rootNode = objectMapper.readTree(json);

        for (String path : maskingPaths) {
            rootNode = maskPath(rootNode, path);
        }

        return objectMapper.writeValueAsString(rootNode);
    }

    /**
     * Masks a single JSONPath in the JSON tree.
     */
    private JsonNode maskPath(JsonNode rootNode, String jsonPath) {
        try {
            // Use JsonPath to find matching nodes and their paths
            // We need to find the actual locations and mask them
            Object result = JsonPath.using(jsonPathConfig)
                .parse(rootNode)
                .read(jsonPath);

            if (result == null) {
                return rootNode; // Path not found, nothing to mask
            }

            // For array wildcards like $.items[*].id, we need to find each element
            // and mask it individually
            if (jsonPath.contains("[*]")) {
                return maskWildcardPath(rootNode, jsonPath);
            }

            // For recursive descent like $..timestamp
            if (jsonPath.contains("..")) {
                return maskRecursivePath(rootNode, jsonPath);
            }

            // Simple path - mask directly
            return maskSimplePath(rootNode, jsonPath);

        } catch (PathNotFoundException e) {
            // Path doesn't exist in this JSON - that's fine
            return rootNode;
        }
    }

    /**
     * Masks a simple path like $.quote.quoteId
     */
    private JsonNode maskSimplePath(JsonNode rootNode, String jsonPath) {
        // Parse the path to get parent and field name
        String[] parts = parsePathParts(jsonPath);
        if (parts.length < 2) {
            return rootNode;
        }

        String parentPath = parts[0];
        String fieldName = parts[1];

        try {
            // Navigate to parent
            JsonNode parent;
            if ("$".equals(parentPath)) {
                parent = rootNode;
            } else {
                parent = JsonPath.using(jsonPathConfig)
                    .parse(rootNode)
                    .read(parentPath);
            }

            if (parent != null && parent.isObject()) {
                ObjectNode parentObject = (ObjectNode) parent;
                if (parentObject.has(fieldName)) {
                    parentObject.set(fieldName, TextNode.valueOf(MASKED_VALUE));
                }
            }
        } catch (Exception e) {
            // Path navigation failed - ignore
        }

        return rootNode;
    }

    /**
     * Masks paths with array wildcards like $.items[*].id
     * Handles multiple wildcards like $.items[*].subItems[*].id
     */
    private JsonNode maskWildcardPath(JsonNode rootNode, String jsonPath) {
        // Split at the first wildcard
        Pattern wildcardPattern = Pattern.compile("(.+?)\\[\\*\\](.*)");
        Matcher matcher = wildcardPattern.matcher(jsonPath);

        if (!matcher.matches()) {
            return rootNode;
        }

        String arrayPath = matcher.group(1);
        String remainder = matcher.group(2);

        try {
            JsonNode arrayNode = JsonPath.using(jsonPathConfig)
                .parse(rootNode)
                .read(arrayPath);

            if (arrayNode != null && arrayNode.isArray()) {
                ArrayNode array = (ArrayNode) arrayNode;
                for (int i = 0; i < array.size(); i++) {
                    String indexedPath = arrayPath + "[" + i + "]" + remainder;
                    // If remainder still contains wildcards, recursively handle them
                    if (indexedPath.contains("[*]")) {
                        maskWildcardPath(rootNode, indexedPath);
                    } else {
                        maskPathInNode(rootNode, indexedPath);
                    }
                }
            }
        } catch (Exception e) {
            // Array not found - ignore
        }

        return rootNode;
    }

    /**
     * Masks paths with recursive descent like $..timestamp
     */
    private JsonNode maskRecursivePath(JsonNode rootNode, String jsonPath) {
        // Extract the field name after ..
        String fieldName = jsonPath.substring(jsonPath.lastIndexOf("..") + 2);

        // Handle cases like $..items[*].timestamp
        if (fieldName.contains("[*]")) {
            // This is complex - we'd need to handle nested wildcards
            // For now, just find all matches and mask them
            maskFieldRecursively(rootNode, fieldName.split("\\[")[0]);
        } else if (fieldName.contains(".")) {
            // Handle $..nested.field patterns
            String[] parts = fieldName.split("\\.", 2);
            maskNestedFieldRecursively(rootNode, parts[0], parts[1]);
        } else {
            maskFieldRecursively(rootNode, fieldName);
        }

        return rootNode;
    }

    /**
     * Recursively masks all occurrences of a field name throughout the tree.
     */
    private void maskFieldRecursively(JsonNode node, String fieldName) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (obj.has(fieldName)) {
                obj.set(fieldName, TextNode.valueOf(MASKED_VALUE));
            }
            // Recurse into children
            obj.fields().forEachRemaining(entry -> {
                maskFieldRecursively(entry.getValue(), fieldName);
            });
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                maskFieldRecursively(element, fieldName);
            }
        }
    }

    /**
     * Recursively masks nested field patterns like $..valuation.valuationId
     */
    private void maskNestedFieldRecursively(JsonNode node, String parentField, String childField) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (obj.has(parentField)) {
                JsonNode parentNode = obj.get(parentField);
                if (parentNode.isObject()) {
                    ObjectNode parentObj = (ObjectNode) parentNode;
                    if (parentObj.has(childField)) {
                        parentObj.set(childField, TextNode.valueOf(MASKED_VALUE));
                    }
                }
            }
            // Recurse into all children
            obj.fields().forEachRemaining(entry -> {
                maskNestedFieldRecursively(entry.getValue(), parentField, childField);
            });
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                maskNestedFieldRecursively(element, parentField, childField);
            }
        }
    }

    /**
     * Masks a path with a specific array index.
     */
    private void maskPathInNode(JsonNode rootNode, String jsonPath) {
        String[] parts = parsePathParts(jsonPath);
        if (parts.length < 2) {
            return;
        }

        String parentPath = parts[0];
        String fieldName = parts[1];

        try {
            JsonNode parent = JsonPath.using(jsonPathConfig)
                .parse(rootNode)
                .read(parentPath);

            if (parent != null && parent.isObject()) {
                ObjectNode parentObject = (ObjectNode) parent;
                if (parentObject.has(fieldName)) {
                    parentObject.set(fieldName, TextNode.valueOf(MASKED_VALUE));
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Parses a JSONPath into parent path and field name.
     * Example: "$.quote.customer.customerId" -> ["$.quote.customer", "customerId"]
     */
    private String[] parsePathParts(String jsonPath) {
        // Handle array index at the end: $.items[0].field
        Pattern pattern = Pattern.compile("(.+)\\.([^.\\[\\]]+)$");
        Matcher matcher = pattern.matcher(jsonPath);

        if (matcher.matches()) {
            return new String[] { matcher.group(1), matcher.group(2) };
        }

        // Handle root level: $.field
        if (jsonPath.startsWith("$.") && !jsonPath.substring(2).contains(".")) {
            return new String[] { "$", jsonPath.substring(2) };
        }

        return new String[0];
    }

    /**
     * Returns the list of masking paths.
     */
    public List<String> getMaskingPaths() {
        return new ArrayList<>(maskingPaths);
    }
}
