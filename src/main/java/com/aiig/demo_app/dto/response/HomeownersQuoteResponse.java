package com.aiig.demo_app.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for a homeowners insurance quote.
 * Contains deeply nested objects, collections, and dynamic fields.
 *
 * Dynamic fields that change per request (will need masking):
 * - responseId, correlationId, transactionId at root level
 * - quote.quoteId, quote.quoteNumber
 * - quote.customer.customerId
 * - quote.property.propertyId
 * - quote.coverages[*].coverageId
 * - quote.discounts[*].discountId
 * - timestamps throughout
 */
public record HomeownersQuoteResponse(
    // Dynamic: changes every request
    String responseId,
    String correlationId,
    String transactionId,
    Instant timestamp,

    // Stable structure
    String status,
    Quote quote,
    List<Message> messages
) {
    public record Quote(
        // Dynamic fields
        String quoteId,
        String quoteNumber,
        Instant createdAt,
        Instant expiresAt,

        // Stable fields
        String status,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        int termMonths,

        // Nested objects
        Customer customer,
        Property property,

        // Collections with dynamic IDs
        List<Coverage> coverages,
        List<Discount> discounts,

        // Pricing summary
        PricingSummary pricing
    ) {}

    public record Customer(
        // Dynamic
        String customerId,
        Instant createdAt,

        // Stable
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,

        // Nested
        Address mailingAddress,
        CreditInfo creditInfo
    ) {}

    public record CreditInfo(
        String creditTier,
        int creditScoreRange,
        Instant lastChecked
    ) {}

    public record Property(
        // Dynamic
        String propertyId,
        Instant createdAt,

        // Stable
        String propertyType,
        int yearBuilt,
        int squareFootage,
        String constructionType,
        String roofType,
        int roofAge,
        int numberOfStories,
        boolean hasPool,
        boolean hasAlarmSystem,
        String fireProtectionClass,
        BigDecimal distanceToFireStation,
        BigDecimal distanceToHydrant,

        // Nested
        Address address,
        PropertyValuation valuation
    ) {}

    public record PropertyValuation(
        // Dynamic
        String valuationId,
        Instant valuationDate,

        // Stable
        BigDecimal dwellingValue,
        BigDecimal landValue,
        BigDecimal totalValue,
        String valuationSource
    ) {}

    public record Address(
        String street,
        String city,
        String state,
        String zipCode,
        String county,
        BigDecimal latitude,
        BigDecimal longitude
    ) {}

    public record Coverage(
        // Dynamic
        String coverageId,

        // Stable
        String coverageCode,
        String coverageName,
        String coverageDescription,
        BigDecimal limitAmount,
        BigDecimal deductible,
        BigDecimal premium,
        boolean isRequired,
        List<SubCoverage> subCoverages
    ) {}

    public record SubCoverage(
        // Dynamic
        String subCoverageId,

        // Stable
        String code,
        String name,
        BigDecimal limitAmount,
        BigDecimal premium
    ) {}

    public record Discount(
        // Dynamic
        String discountId,
        Instant appliedAt,

        // Stable
        String discountCode,
        String discountName,
        String discountType,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String reason
    ) {}

    public record PricingSummary(
        BigDecimal basePremium,
        BigDecimal totalDiscounts,
        BigDecimal surcharges,
        BigDecimal fees,
        BigDecimal totalPremium,
        BigDecimal monthlyPayment,
        String paymentFrequency,
        List<PremiumBreakdown> breakdown
    ) {}

    public record PremiumBreakdown(
        String category,
        String description,
        BigDecimal amount
    ) {}

    public record Message(
        String code,
        String severity,
        String text
    ) {}
}
