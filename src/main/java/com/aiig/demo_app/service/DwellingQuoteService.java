package com.aiig.demo_app.service;

import com.aiig.demo_app.dto.request.DwellingQuoteRequest;
import com.aiig.demo_app.dto.response.DwellingQuoteResponse;
import com.aiig.demo_app.dto.response.DwellingQuoteResponse.QuoteResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service that integrates with Guidewire INOW to create dwelling quotes.
 * In production, this calls actual INOW APIs.
 * For this demo, it generates realistic INOW-style responses with dynamic values.
 */
@Service
public class DwellingQuoteService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Creates a dwelling insurance quote by calling INOW APIs.
     * Returns an INOW-style response with nested DTOs.
     */
    public DwellingQuoteResponse createQuote(DwellingQuoteRequest request) {
        // Generate dynamic identifiers
        String quoteNumber = "QT-" + String.format("%08d", ThreadLocalRandom.current().nextInt(10000000));
        String systemId = UUID.randomUUID().toString();
        long appId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);
        long timestamp = System.currentTimeMillis();

        // Build the INOW-style dtoApplication response
        Map<String, Object> dtoApplication = buildDtoApplication(request, appId, timestamp, systemId);

        // Generate spin URL with dynamic tokens
        String spinUrl = String.format(
            "http://inow-dev.example.com/pc/PolicyCenter.do?SystemId=%s&QuoteNumber=%s&Token=%s",
            systemId, quoteNumber, UUID.randomUUID().toString()
        );

        QuoteResponse quoteResponse = new QuoteResponse(
            quoteNumber,
            "2450.00",
            null,
            spinUrl,
            dtoApplication
        );

        return new DwellingQuoteResponse(quoteResponse);
    }

    private Map<String, Object> buildDtoApplication(DwellingQuoteRequest request,
                                                     long appId, long timestamp, String systemId) {
        Map<String, Object> dto = new LinkedHashMap<>();

        // Dynamic application identifiers
        dto.put("id", "DTOApplication-" + appId + "-" + timestamp);
        dto.put("SystemId", systemId);
        dto.put("AppNumber", "APP-" + String.format("%08d", appId));
        dto.put("QuoteNumber", "QT-" + String.format("%08d", ThreadLocalRandom.current().nextInt(10000000)));
        dto.put("TransactionNumber", ThreadLocalRandom.current().nextInt(100000, 999999));

        // Timestamps
        String nowDate = LocalDate.now().format(DATE_FORMAT);
        String nowTime = java.time.LocalTime.now().format(TIME_FORMAT);
        dto.put("AddDt", nowDate);
        dto.put("AddTm", nowTime);
        dto.put("UpdateDt", nowDate);
        dto.put("UpdateTm", nowTime);
        dto.put("EffDt", LocalDate.now().plusDays(30).format(DATE_FORMAT));
        dto.put("ExpDt", LocalDate.now().plusDays(395).format(DATE_FORMAT));

        // Build nested DTOs
        dto.put("DTOBasicPolicy", List.of(buildDTOBasicPolicy(timestamp)));
        dto.put("DTOInsured", List.of(buildDTOInsured(timestamp)));
        dto.put("DTOLine", List.of(buildDTOLine(timestamp)));

        // Static fields
        dto.put("StatusCd", "Quoted");
        dto.put("SubStatusCd", "Active");
        dto.put("TypeCd", "Dwelling");

        return dto;
    }

    private Map<String, Object> buildDTOBasicPolicy(long timestamp) {
        Map<String, Object> policy = new LinkedHashMap<>();
        long policyId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        policy.put("id", "DTOBasicPolicy-" + policyId + "-" + timestamp);
        policy.put("SystemId", UUID.randomUUID().toString());
        policy.put("PolicyNumber", "DWL-" + String.format("%08d", policyId));
        policy.put("QuoteNumber", "QT-" + String.format("%08d", ThreadLocalRandom.current().nextInt(10000000)));
        policy.put("TypeCd", "Dwelling");
        policy.put("PolicyTypeCd", "HO3");
        policy.put("TermCd", "Annual");
        policy.put("BillTypeCd", "Direct");

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        policy.put("AddDt", nowDate);
        policy.put("UpdateDt", nowDate);

        return policy;
    }

    private Map<String, Object> buildDTOInsured(long timestamp) {
        Map<String, Object> insured = new LinkedHashMap<>();
        long insuredId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        insured.put("id", "DTOInsured-" + insuredId + "-" + timestamp);
        insured.put("SystemId", UUID.randomUUID().toString());
        insured.put("InsuredNumber", "INS-" + String.format("%08d", insuredId));
        insured.put("PartyNumber", "PTY-" + String.format("%08d", ThreadLocalRandom.current().nextInt(10000000)));
        insured.put("InsuredTypeCd", "Primary");
        insured.put("EntityTypeCd", "Individual");
        insured.put("GivenName", "John");
        insured.put("Surname", "Smith");

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        insured.put("AddDt", nowDate);
        insured.put("UpdateDt", nowDate);

        // Add address
        insured.put("Addr", List.of(buildAddress(timestamp)));

        return insured;
    }

    private Map<String, Object> buildAddress(long timestamp) {
        Map<String, Object> addr = new LinkedHashMap<>();
        long addrId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        addr.put("id", "Addr-" + addrId + "-" + timestamp);
        addr.put("SystemId", UUID.randomUUID().toString());
        addr.put("AddrNumber", "ADR-" + String.format("%08d", addrId));
        addr.put("AddrTypeCd", "Mailing");
        addr.put("Addr1", "123 Main Street");
        addr.put("City", "Austin");
        addr.put("StateProvCd", "TX");
        addr.put("PostalCode", "78701");
        addr.put("CountryCd", "USA");

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        addr.put("AddDt", nowDate);
        addr.put("UpdateDt", nowDate);

        return addr;
    }

    private Map<String, Object> buildDTOLine(long timestamp) {
        Map<String, Object> line = new LinkedHashMap<>();
        long lineId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        line.put("id", "DTOLine-" + lineId + "-" + timestamp);
        line.put("SystemId", UUID.randomUUID().toString());
        line.put("LineNumber", "LN-" + String.format("%08d", lineId));
        line.put("LineCd", "Dwelling");

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        line.put("AddDt", nowDate);
        line.put("UpdateDt", nowDate);

        // Add risk
        line.put("DTORisk", List.of(buildDTORisk(timestamp)));

        return line;
    }

    private Map<String, Object> buildDTORisk(long timestamp) {
        Map<String, Object> risk = new LinkedHashMap<>();
        long riskId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        risk.put("id", "DTORisk-" + riskId + "-" + timestamp);
        risk.put("SystemId", UUID.randomUUID().toString());
        risk.put("RiskNumber", "RSK-" + String.format("%08d", riskId));
        risk.put("RiskTypeCd", "DwellingRisk");

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        risk.put("AddDt", nowDate);
        risk.put("UpdateDt", nowDate);

        // Add building and address
        risk.put("DTOBuilding", List.of(buildDTOBuilding(timestamp)));
        risk.put("Addr", List.of(buildAddress(timestamp)));

        return risk;
    }

    private Map<String, Object> buildDTOBuilding(long timestamp) {
        Map<String, Object> building = new LinkedHashMap<>();
        long buildingId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        building.put("id", "DTOBuilding-" + buildingId + "-" + timestamp);
        building.put("SystemId", UUID.randomUUID().toString());
        building.put("BuildingNumber", "BLD-" + String.format("%08d", buildingId));
        building.put("LocationNumber", "LOC-" + String.format("%08d", ThreadLocalRandom.current().nextInt(10000000)));
        building.put("BuildingTypeCd", "SingleFamily");
        building.put("YearBuilt", 2015);
        building.put("SquareFootage", 2500);
        building.put("ConstructionCd", "Frame");
        building.put("RoofTypeCd", "CompositionShingle");
        building.put("NumberOfStories", 2);

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        building.put("AddDt", nowDate);
        building.put("UpdateDt", nowDate);

        // Add coverages
        building.put("DTOCoverage", buildCoverages(timestamp));

        // Add data report reference (dynamic)
        building.put("DataReportRef", ThreadLocalRandom.current().nextInt(100000, 999999));

        return building;
    }

    private List<Map<String, Object>> buildCoverages(long timestamp) {
        List<Map<String, Object>> coverages = new ArrayList<>();

        coverages.add(buildCoverage("DWELL", "Dwelling Coverage", 350000, 2500, timestamp));
        coverages.add(buildCoverage("OTHSTR", "Other Structures", 35000, 2500, timestamp));
        coverages.add(buildCoverage("PERSP", "Personal Property", 175000, 2500, timestamp));
        coverages.add(buildCoverage("LOSSUSE", "Loss of Use", 70000, 0, timestamp));
        coverages.add(buildCoverage("PERLIAB", "Personal Liability", 300000, 0, timestamp));
        coverages.add(buildCoverage("MEDPAY", "Medical Payments", 5000, 0, timestamp));

        return coverages;
    }

    private Map<String, Object> buildCoverage(String code, String name, int limit, int deductible, long timestamp) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        long coverageId = ThreadLocalRandom.current().nextLong(100000000L, 999999999L);

        coverage.put("id", "DTOCoverage-" + coverageId + "-" + timestamp);
        coverage.put("SystemId", UUID.randomUUID().toString());
        coverage.put("CoverageNumber", "COV-" + String.format("%08d", coverageId));
        coverage.put("CoverageCd", code);
        coverage.put("CoverageName", name);
        coverage.put("LimitAmt", limit);
        coverage.put("DeductibleAmt", deductible);

        String nowDate = LocalDate.now().format(DATE_FORMAT);
        coverage.put("AddDt", nowDate);
        coverage.put("UpdateDt", nowDate);

        return coverage;
    }
}
