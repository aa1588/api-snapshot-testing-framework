package com.aiig.demo_app.dto.request;

import java.util.List;
import java.util.Map;

/**
 * INOW-style request DTO for dwelling quote.
 * Accepts the flexible INOW DTO structure.
 */
public record DwellingQuoteRequest(
    Map<String, Object> dtoApplication
) {}
