package com.aiig.demo_app.service;

import com.aiig.demo_app.dto.request.HomeownersQuoteRequest;
import com.aiig.demo_app.dto.response.HomeownersQuoteResponse;
import com.aiig.demo_app.dto.response.HomeownersQuoteResponse.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service that integrates with Guidewire INOW to create homeowners quotes.
 * In production, this calls actual INOW APIs.
 * For this demo, it generates realistic responses with dynamic values.
 */
@Service
public class HomeownersQuoteService {

    /**
     * Creates a homeowners insurance quote by calling INOW APIs.
     * This is where the INOW API migration happens - the internal implementation
     * changes but the response structure must remain stable.
     */
    public HomeownersQuoteResponse createQuote(HomeownersQuoteRequest request) {
        // Simulate INOW API call latency
        Instant now = Instant.now();

        // Generate dynamic IDs (these change every request)
        String responseId = generateId("RSP");
        String correlationId = UUID.randomUUID().toString();
        String transactionId = generateId("TXN");
        String quoteId = generateId("QUO");
        String quoteNumber = generateQuoteNumber();
        String customerId = generateId("CUS");
        String propertyId = generateId("PRP");

        // Build customer from request
        Customer customer = buildCustomer(request.customer(), customerId, now);

        // Build property with valuation
        Property property = buildProperty(request.property(), propertyId, now);

        // Build coverages from request
        List<Coverage> coverages = buildCoverages(request.requestedCoverages());

        // Calculate applicable discounts
        List<Discount> discounts = calculateDiscounts(request, property, now);

        // Calculate pricing
        PricingSummary pricing = calculatePricing(coverages, discounts);

        // Build the quote
        Quote quote = new Quote(
            quoteId,
            quoteNumber,
            now,
            now.plusSeconds(30L * 24 * 60 * 60), // expires in 30 days
            "QUOTED",
            request.effectiveDate(),
            request.effectiveDate().plusYears(1),
            12,
            customer,
            property,
            coverages,
            discounts,
            pricing
        );

        // Build response messages
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("INFO_001", "INFO", "Quote created successfully"));

        if (property.hasPool()) {
            messages.add(new Message("WARN_POOL", "WARNING",
                "Pool liability coverage recommended"));
        }

        return new HomeownersQuoteResponse(
            responseId,
            correlationId,
            transactionId,
            now,
            "SUCCESS",
            quote,
            messages
        );
    }

    private Customer buildCustomer(HomeownersQuoteRequest.CustomerInfo info,
                                    String customerId, Instant now) {
        // Simulate credit check (would call INOW credit service)
        CreditInfo creditInfo = new CreditInfo(
            "PREFERRED",
            750,
            now
        );

        return new Customer(
            customerId,
            now,
            info.firstName(),
            info.lastName(),
            info.email(),
            info.phone(),
            info.dateOfBirth(),
            new Address(
                "123 Mailing St",
                "Austin",
                "TX",
                "78701",
                "Travis",
                new BigDecimal("30.2672"),
                new BigDecimal("-97.7431")
            ),
            creditInfo
        );
    }

    private Property buildProperty(HomeownersQuoteRequest.PropertyInfo info,
                                    String propertyId, Instant now) {
        // Calculate roof age
        int roofAge = LocalDate.now().getYear() - info.yearBuilt();
        if (roofAge > 25) roofAge = 25; // Cap for this demo

        // Property valuation (would call INOW valuation service)
        BigDecimal dwellingValue = BigDecimal.valueOf(info.squareFootage())
            .multiply(BigDecimal.valueOf(150)); // $150/sqft estimate
        BigDecimal landValue = BigDecimal.valueOf(50000);

        PropertyValuation valuation = new PropertyValuation(
            generateId("VAL"),
            now,
            dwellingValue,
            landValue,
            dwellingValue.add(landValue),
            "AUTOMATED_VALUATION_MODEL"
        );

        return new Property(
            propertyId,
            now,
            info.propertyType(),
            info.yearBuilt(),
            info.squareFootage(),
            info.constructionType(),
            info.roofType(),
            roofAge,
            info.numberOfStories(),
            info.hasPool(),
            info.hasAlarmSystem(),
            "CLASS_4",
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(0.3),
            new Address(
                info.address().street(),
                info.address().city(),
                info.address().state(),
                info.address().zipCode(),
                "Travis",
                new BigDecimal("30.2672"),
                new BigDecimal("-97.7431")
            ),
            valuation
        );
    }

    private List<Coverage> buildCoverages(List<HomeownersQuoteRequest.CoverageSelection> selections) {
        List<Coverage> coverages = new ArrayList<>();

        for (var selection : selections) {
            Coverage coverage = createCoverage(selection);
            coverages.add(coverage);
        }

        return coverages;
    }

    private Coverage createCoverage(HomeownersQuoteRequest.CoverageSelection selection) {
        String name = getCoverageName(selection.coverageCode());
        String description = getCoverageDescription(selection.coverageCode());
        boolean isRequired = isRequiredCoverage(selection.coverageCode());

        // Calculate premium based on limit and deductible
        BigDecimal premium = BigDecimal.valueOf(selection.limitAmount())
            .multiply(BigDecimal.valueOf(0.003))
            .subtract(BigDecimal.valueOf(selection.deductible()).multiply(BigDecimal.valueOf(0.01)))
            .setScale(2, RoundingMode.HALF_UP);

        if (premium.compareTo(BigDecimal.ZERO) < 0) {
            premium = BigDecimal.valueOf(50);
        }

        // Build sub-coverages for dwelling coverage
        List<SubCoverage> subCoverages = new ArrayList<>();
        if ("DWELLING".equals(selection.coverageCode())) {
            subCoverages.add(new SubCoverage(
                generateId("SUB"),
                "DWELLING_EXT",
                "Extended Dwelling",
                BigDecimal.valueOf(selection.limitAmount()).multiply(BigDecimal.valueOf(0.1)),
                premium.multiply(BigDecimal.valueOf(0.1)).setScale(2, RoundingMode.HALF_UP)
            ));
            subCoverages.add(new SubCoverage(
                generateId("SUB"),
                "BUILDING_CODE",
                "Building Code Upgrade",
                BigDecimal.valueOf(selection.limitAmount()).multiply(BigDecimal.valueOf(0.05)),
                premium.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return new Coverage(
            generateId("COV"),
            selection.coverageCode(),
            name,
            description,
            BigDecimal.valueOf(selection.limitAmount()),
            BigDecimal.valueOf(selection.deductible()),
            premium,
            isRequired,
            subCoverages
        );
    }

    private List<Discount> calculateDiscounts(HomeownersQuoteRequest request,
                                               Property property, Instant now) {
        List<Discount> discounts = new ArrayList<>();

        // Alarm system discount
        if (property.hasAlarmSystem()) {
            discounts.add(new Discount(
                generateId("DSC"),
                now,
                "ALARM",
                "Alarm System Discount",
                "SAFETY",
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(75),
                "Home has monitored alarm system"
            ));
        }

        // New home discount
        if (property.yearBuilt() >= LocalDate.now().getYear() - 10) {
            discounts.add(new Discount(
                generateId("DSC"),
                now,
                "NEW_HOME",
                "New Home Discount",
                "PROPERTY_AGE",
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(150),
                "Home built within last 10 years"
            ));
        }

        // Multi-policy placeholder (would check INOW for existing policies)
        discounts.add(new Discount(
            generateId("DSC"),
            now,
            "MULTI_POLICY",
            "Multi-Policy Discount",
            "BUNDLING",
            BigDecimal.valueOf(15),
            BigDecimal.valueOf(225),
            "Customer has auto policy"
        ));

        return discounts;
    }

    private PricingSummary calculatePricing(List<Coverage> coverages, List<Discount> discounts) {
        BigDecimal basePremium = coverages.stream()
            .map(Coverage::premium)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscounts = discounts.stream()
            .map(Discount::discountAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal surcharges = BigDecimal.valueOf(25); // Hurricane surcharge
        BigDecimal fees = BigDecimal.valueOf(35); // Policy fee

        BigDecimal totalPremium = basePremium
            .subtract(totalDiscounts)
            .add(surcharges)
            .add(fees)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal monthlyPayment = totalPremium
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        List<PremiumBreakdown> breakdown = new ArrayList<>();
        for (Coverage coverage : coverages) {
            breakdown.add(new PremiumBreakdown(
                "COVERAGE",
                coverage.coverageName(),
                coverage.premium()
            ));
        }
        for (Discount discount : discounts) {
            breakdown.add(new PremiumBreakdown(
                "DISCOUNT",
                discount.discountName(),
                discount.discountAmount().negate()
            ));
        }
        breakdown.add(new PremiumBreakdown("SURCHARGE", "Hurricane Surcharge", surcharges));
        breakdown.add(new PremiumBreakdown("FEE", "Policy Fee", fees));

        return new PricingSummary(
            basePremium,
            totalDiscounts,
            surcharges,
            fees,
            totalPremium,
            monthlyPayment,
            "MONTHLY",
            breakdown
        );
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateQuoteNumber() {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "HO-" + timestamp + "-" + random;
    }

    private String getCoverageName(String code) {
        return switch (code) {
            case "DWELLING" -> "Dwelling Coverage";
            case "OTHER_STRUCTURES" -> "Other Structures";
            case "PERSONAL_PROPERTY" -> "Personal Property";
            case "LOSS_OF_USE" -> "Loss of Use";
            case "LIABILITY" -> "Personal Liability";
            case "MEDICAL" -> "Medical Payments";
            default -> code;
        };
    }

    private String getCoverageDescription(String code) {
        return switch (code) {
            case "DWELLING" -> "Covers the structure of your home";
            case "OTHER_STRUCTURES" -> "Covers detached structures like garages and fences";
            case "PERSONAL_PROPERTY" -> "Covers your belongings inside the home";
            case "LOSS_OF_USE" -> "Covers additional living expenses if home is uninhabitable";
            case "LIABILITY" -> "Covers legal liability for injuries to others";
            case "MEDICAL" -> "Covers medical expenses for guests injured on property";
            default -> "Coverage for " + code;
        };
    }

    private boolean isRequiredCoverage(String code) {
        return "DWELLING".equals(code) || "LIABILITY".equals(code);
    }
}
