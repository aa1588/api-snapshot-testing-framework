package com.aiig.demo_app.controller;

import com.aiig.demo_app.dto.request.DwellingQuoteRequest;
import com.aiig.demo_app.dto.response.DwellingQuoteResponse;
import com.aiig.demo_app.service.DwellingQuoteService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Dwelling Quote operations using INOW-style DTOs.
 *
 * This endpoint demonstrates the INOW API integration pattern where:
 * - Requests contain nested DTOApplication structures
 * - Responses contain quoteResponse with nested DTOs
 * - Many fields are dynamically generated (IDs, timestamps, references)
 */
@RestController
@RequestMapping("/api/v1/quotes/dwelling")
public class DwellingQuoteController {

    private final DwellingQuoteService quoteService;

    public DwellingQuoteController(DwellingQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /**
     * Creates a new dwelling insurance quote.
     *
     * POST /api/v1/quotes/dwelling
     *
     * This endpoint internally calls INOW APIs and returns the response
     * in the standard INOW DTO format with nested structures.
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DwellingQuoteResponse> createQuote(
            @RequestBody DwellingQuoteRequest request) {

        DwellingQuoteResponse response = quoteService.createQuote(request);
        return ResponseEntity.ok(response);
    }
}
