package com.aiig.demo_app.dto.response;

import java.util.Map;

/**
 * INOW-style response DTO for dwelling quote.
 * Wraps the flexible INOW DTO structure as returned from the INOW API.
 *
 * Dynamic fields that change per request (need masking):
 * - quoteResponse.quoteNumber
 * - quoteResponse.spinUrl (contains SystemId and tokens)
 * - All $..id fields (DTOApplication-*, DTOLine-*, etc.)
 * - All $..SystemId fields
 * - Timestamps: $..AddDt, $..AddTm, $..UpdateDt, $..UpdateTm
 * - $..DataReportRef numbers
 */
public record DwellingQuoteResponse(
    QuoteResponse quoteResponse
) {
    public record QuoteResponse(
        String quoteNumber,
        String premium,
        String ncrbPremium,
        String spinUrl,
        Map<String, Object> dtoApplication
    ) {}
}
