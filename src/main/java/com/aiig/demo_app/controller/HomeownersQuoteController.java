package com.aiig.demo_app.controller;

import com.aiig.demo_app.dto.request.HomeownersQuoteRequest;
import com.aiig.demo_app.dto.response.HomeownersQuoteResponse;
import com.aiig.demo_app.service.HomeownersQuoteService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Homeowners Quote operations.
 *
 * This is one of our ~10 stable external endpoints.
 * The external contract (URL, request/response structure) remains unchanged
 * even as we migrate the underlying INOW API integrations.
 */
@RestController
@RequestMapping("/api/v1/quotes/homeowners")
public class HomeownersQuoteController {

    private final HomeownersQuoteService quoteService;

    public HomeownersQuoteController(HomeownersQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /**
     * Creates a new homeowners insurance quote.
     *
     * POST /api/v1/quotes/homeowners
     *
     * This endpoint internally calls multiple INOW APIs:
     * - Customer lookup/creation
     * - Property valuation
     * - Coverage rating
     * - Discount calculation
     *
     * During INOW migration, the internal calls change but this
     * endpoint's response structure must remain identical.
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<HomeownersQuoteResponse> createQuote(
            @RequestBody HomeownersQuoteRequest request) {

        HomeownersQuoteResponse response = quoteService.createQuote(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an existing quote by ID.
     *
     * GET /api/v1/quotes/homeowners/{quoteId}
     *
     * Placeholder for demonstrating multiple endpoint types.
     */
    @GetMapping(
        path = "/{quoteId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<HomeownersQuoteResponse> getQuote(
            @PathVariable String quoteId) {

        // In production, this would retrieve from INOW
        // For demo, we return a not-found response
        return ResponseEntity.notFound().build();
    }
}
