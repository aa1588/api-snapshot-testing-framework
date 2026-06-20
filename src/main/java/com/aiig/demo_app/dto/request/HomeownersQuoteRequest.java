package com.aiig.demo_app.dto.request;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a homeowners insurance quote.
 * This represents the stable external contract of our API.
 */
public record HomeownersQuoteRequest(
    CustomerInfo customer,
    PropertyInfo property,
    List<CoverageSelection> requestedCoverages,
    LocalDate effectiveDate
) {
    public record CustomerInfo(
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth
    ) {}

    public record PropertyInfo(
        Address address,
        String propertyType,
        int yearBuilt,
        int squareFootage,
        String constructionType,
        String roofType,
        int numberOfStories,
        boolean hasPool,
        boolean hasAlarmSystem
    ) {}

    public record Address(
        String street,
        String city,
        String state,
        String zipCode
    ) {}

    public record CoverageSelection(
        String coverageCode,
        int limitAmount,
        int deductible
    ) {}
}
