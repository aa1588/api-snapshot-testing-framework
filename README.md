# INOW Integration Snapshot Testing Framework

A production-quality snapshot (Golden Master / Approval) testing framework for Spring Boot applications that integrate with Guidewire InsuranceNow (INOW) APIs.

## Table of Contents

- [Overview](#overview)
- [The Problem](#the-problem)
- [The Solution](#the-solution)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Adding a New Endpoint](#adding-a-new-endpoint)
- [Masking Dynamic Fields](#masking-dynamic-fields)
- [Commands Reference](#commands-reference)
- [Allure Reports](#allure-reports)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Overview

This framework enables regression testing for REST APIs during backend migrations. It captures "approved" JSON responses as baselines, then compares future responses against them to detect unintended changes.

**Key Features:**
- Single parameterized test for all endpoints (no code duplication)
- Configuration-driven endpoint registration via YAML
- JSONPath-based masking for dynamic fields
- Global and endpoint-specific masking rules
- Readable diff output on failures
- Git-friendly snapshot storage

---

## The Problem

When migrating ~35 INOW APIs to a new specification:

1. **Response contracts must remain stable** - External consumers expect identical JSON structures
2. **Responses are complex** - Often 1000+ lines of deeply nested JSON
3. **Some fields are dynamic** - IDs, timestamps, and generated numbers change every request
4. **Manual verification is error-prone** - Easy to miss subtle structural changes

### Example of Dynamic Fields

```json
{
  "responseId": "RSP-A1B2C3D4",        // Changes every request
  "timestamp": "2025-01-15T10:30:00Z", // Changes every request
  "quote": {
    "quoteNumber": "HO-1705312200-4521", // Generated, changes every request
    "customer": {
      "customerId": "CUS-X9Y8Z7W6"       // Changes every request
    }
  }
}
```

If we simply compare JSON responses, tests would fail on every run due to these dynamic fields.

---

## The Solution

### Masking Instead of Removing

Rather than removing dynamic fields before comparison, we **mask** them:

```json
{
  "responseId": "<MASKED>",
  "timestamp": "<MASKED>",
  "quote": {
    "quoteNumber": "<MASKED>",
    "customer": {
      "customerId": "<MASKED>"
    }
  }
}
```

**Why masking is better than removal:**

| Scenario | Remove Fields | Mask Fields |
|----------|--------------|-------------|
| Field deleted | ❌ Not detected | ✅ Detected |
| Field renamed | ❌ Not detected | ✅ Detected |
| Type changed | ❌ Not detected | ✅ Detected |
| Field moved | ❌ Not detected | ✅ Detected |
| Value changed | ✅ Detected | ✅ Ignored (intentional) |

Masking preserves **structure** while ignoring **dynamic values**.

---

## Architecture

```
src/
├── main/java/com/aiig/demo_app/
│   ├── controller/              # REST endpoints (stable external contract)
│   ├── dto/                     # Request/Response DTOs
│   └── service/                 # INOW integration (changes during migration)
│
└── test/
    ├── java/com/aiig/demo_app/
    │   ├── ApiSnapshotTest.java           # ONE test class for ALL endpoints
    │   └── snapshot/
    │       ├── JsonMasker.java            # JSONPath-based field masking
    │       ├── SnapshotComparator.java    # JSON comparison with diff output
    │       ├── SnapshotConfig.java        # YAML configuration loader
    │       └── SnapshotManager.java       # File I/O for snapshots
    │
    └── resources/
        ├── requests/                      # Fixed request payloads
        │   └── homeowners-quote.json
        │
        ├── snapshot-config/               # YAML configuration
        │   ├── global.yml                 # Global masking rules
        │   └── endpoints/                 # Per-endpoint configs
        │       └── homeowners-quote.yml
        │
        └── snapshots/
            ├── approved/                  # ✅ COMMITTED to Git
            │   └── homeowners-quote.json
            └── received/                  # ❌ GIT-IGNORED (transient)
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| `ApiSnapshotTest` | JUnit 5 parameterized test that starts a real server and makes HTTP calls |
| `SnapshotConfig` | Loads YAML configs, merges global + endpoint masking paths |
| `JsonMasker` | Applies JSONPath expressions to replace dynamic values with `<MASKED>` |
| `SnapshotComparator` | Compares JSON and produces human-readable diff reports |
| `SnapshotManager` | Handles file storage for approved/received snapshots |

---

## Quick Start

### 1. Run Tests

```bash
# Verify all endpoints match their approved snapshots
mvn test

# Run only snapshot tests
mvn test -Dtest=ApiSnapshotTest
```

### 2. Approve New Snapshots

```bash
# Capture or update approved snapshots
mvn test -Dsnapshot.update=true
```

### 3. Review Changes

When tests fail, you'll see a detailed diff:

```
═══════════════════════════════════════════════════════════════
SNAPSHOT MISMATCH: homeowners-quote
═══════════════════════════════════════════════════════════════

Endpoint: POST /api/v1/quotes/homeowners

Snapshot comparison failed with 2 difference(s):

=== REMOVED FIELDS ===
  $.quote.pricing.fees
    Expected: 35

=== VALUE CHANGES ===
  $.quote.pricing.totalPremium
    Expected: 2490.0
    Actual:   2455.0

═══════════════════════════════════════════════════════════════
To approve the new response:
  mvn test -Dsnapshot.update=true
═══════════════════════════════════════════════════════════════
```

---

## How It Works

### Test Execution Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     ApiSnapshotTest                              │
│  Starts real Tomcat server on random port                       │
│  @ParameterizedTest iterates over all endpoint configurations   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. Load Endpoint Configuration                                  │
│     - Read from snapshot-config/endpoints/{key}.yml             │
│     - Get HTTP method, URL, request file path                   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. Execute Real HTTP Request                                    │
│     - Load request body from requests/{file}.json               │
│     - Make actual HTTP call via RestClient to localhost:port    │
│     - Capture response JSON                                      │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. Apply Masking                                                │
│     - Load global masking paths from global.yml                 │
│     - Load endpoint masking paths from {key}.yml                │
│     - Apply JSONPath masking to response                        │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. Compare or Approve                                           │
│                                                                  │
│     If -Dsnapshot.update=true OR no approved snapshot exists:   │
│       → Save masked response as new approved snapshot            │
│                                                                  │
│     Otherwise:                                                   │
│       → Compare masked response against approved snapshot        │
│       → PASS if identical                                        │
│       → FAIL with diff report if different                       │
└─────────────────────────────────────────────────────────────────┘
```

### Masking Flow

```
Original Response                    Masked Response
─────────────────                    ───────────────
{                                    {
  "responseId": "RSP-ABC123",   →      "responseId": "<MASKED>",
  "status": "SUCCESS",          →      "status": "SUCCESS",
  "quote": {                           "quote": {
    "quoteId": "QUO-XYZ789",    →        "quoteId": "<MASKED>",
    "premium": 1500.00          →        "premium": 1500.00
  }                                    }
}                                    }
```

The masker:
1. Parses the JSON into a tree structure
2. Evaluates each JSONPath expression
3. Replaces matched values with `<MASKED>`
4. Serializes back to JSON string

---

## Configuration

### Global Configuration (`snapshot-config/global.yml`)

Applied to **ALL** endpoints. Use for common dynamic fields:

```yaml
# Global Snapshot Testing Configuration
# These rules apply to ALL endpoints

masking:
  paths:
    # Standard response envelope fields
    - $.responseId
    - $.correlationId
    - $.transactionId
    - $.timestamp

    # Recursive: matches at any depth
    - $..createdAt
    - $..updatedAt
```

### Endpoint Configuration (`snapshot-config/endpoints/{key}.yml`)

Specific to one endpoint. Developers modify only their endpoint's config:

```yaml
# Homeowners Quote Endpoint Configuration

endpoint:
  key: homeowners-quote              # Unique identifier
  method: POST                       # HTTP method
  url: /api/v1/quotes/homeowners     # Endpoint URL
  requestFile: requests/homeowners-quote.json  # Fixed request payload
  description: Creates a new homeowners insurance quote

# Endpoint-specific masking rules
# These are ADDED to global rules, not replacing them
masking:
  paths:
    # Quote identifiers
    - $.quote.quoteId
    - $.quote.quoteNumber

    # Array wildcards: mask all elements
    - $.quote.coverages[*].coverageId
    - $.quote.discounts[*].discountId
    - $.quote.discounts[*].appliedAt
```

---

## Adding a New Endpoint

Adding a new endpoint requires **zero Java code changes**. Just create configuration files:

### Step 1: Create Endpoint Configuration

Create `src/test/resources/snapshot-config/endpoints/auto-quote.yml`:

```yaml
endpoint:
  key: auto-quote
  method: POST
  url: /api/v1/quotes/auto
  requestFile: requests/auto-quote.json
  description: Creates a new auto insurance quote

masking:
  paths:
    - $.quote.quoteId
    - $.quote.quoteNumber
    - $.quote.vehicles[*].vehicleId
    - $.quote.drivers[*].driverId
```

### Step 2: Create Request Payload

Create `src/test/resources/requests/auto-quote.json`:

```json
{
  "customer": {
    "firstName": "Jane",
    "lastName": "Doe"
  },
  "vehicles": [
    {
      "year": 2022,
      "make": "Toyota",
      "model": "Camry"
    }
  ],
  "effectiveDate": "2025-07-01"
}
```

### Step 3: Capture Initial Snapshot

```bash
mvn test -Dsnapshot.update=true
```

This creates `src/test/resources/snapshots/approved/auto-quote.json`.

### Step 4: Commit All Files

```bash
git add src/test/resources/snapshot-config/endpoints/auto-quote.yml
git add src/test/resources/requests/auto-quote.json
git add src/test/resources/snapshots/approved/auto-quote.json
git commit -m "Add snapshot test for auto-quote endpoint"
```

---

## Masking Dynamic Fields

### Supported JSONPath Patterns

| Pattern | Description | Example |
|---------|-------------|---------|
| `$.field` | Root-level field | `$.responseId` |
| `$.parent.child` | Nested field | `$.quote.quoteId` |
| `$.parent.child.grandchild` | Deeply nested | `$.quote.customer.customerId` |
| `$.array[*].field` | All array elements | `$.coverages[*].coverageId` |
| `$.array[0].field` | Specific index | `$.coverages[0].coverageId` |
| `$.a[*].b[*].c` | Nested arrays | `$.coverages[*].subCoverages[*].id` |
| `$..field` | Recursive (any depth) | `$..timestamp` |

### Common Dynamic Fields

Here are typical fields that need masking in insurance APIs:

```yaml
masking:
  paths:
    # IDs and References
    - $.responseId
    - $.correlationId
    - $.transactionId
    - $.quote.quoteId
    - $.quote.quoteNumber
    - $.quote.customer.customerId
    - $.quote.property.propertyId

    # Timestamps
    - $..createdAt
    - $..updatedAt
    - $..timestamp
    - $..expiresAt
    - $..lastChecked

    # Generated Values
    - $.quote.policyNumber
    - $.quote.confirmationNumber

    # Array Elements with IDs
    - $.quote.coverages[*].coverageId
    - $.quote.discounts[*].discountId
    - $.quote.vehicles[*].vehicleId
```

---

## Commands Reference

| Command | Purpose |
|---------|---------|
| `mvn test` | Run all tests, verify snapshots match |
| `mvn test -Dtest=ApiSnapshotTest` | Run only snapshot tests |
| `mvn test -Dsnapshot.update=true` | Approve new/changed snapshots |
| `mvn test -Dtest=ApiSnapshotTest#verifyEndpointSnapshot[homeowners-quote]` | Run single endpoint test |

### CI/CD Integration

```yaml
# GitHub Actions example
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn test
```

**Important:** Never run with `-Dsnapshot.update=true` in CI. Snapshots should only be updated locally and committed after review.

---

## Allure Reports

This framework includes **Allure Report** integration for beautiful, interactive HTML test reports.

### Generating Reports

| Command | Description |
|---------|-------------|
| `mvn test` | Run tests and generate results in `target/allure-results/` |
| `mvn allure:serve` | Generate report and open in browser automatically |
| `mvn allure:report` | Generate static HTML report in `target/site/allure-maven-plugin/` |

### Quick Start

```bash
# 1. Run tests
mvn test

# 2. View report in browser
mvn allure:serve
```

### What the Report Shows

```
┌─────────────────────────────────────────────────────────────┐
│  ALLURE REPORT                                              │
├─────────────────────────────────────────────────────────────┤
│  Overview    │ Pie chart: passed/failed, total duration    │
│  Suites      │ Tests grouped by endpoint                   │
│  Graphs      │ Timeline, duration trends, severity         │
│  Timeline    │ Visual execution timeline                   │
│  Categories  │ Failure categories (if any)                 │
└─────────────────────────────────────────────────────────────┘
```

### Report Features

**For each test, the report includes:**

| Attachment | Description |
|------------|-------------|
| Request Body | The JSON payload sent to the API |
| Masked Response | The response after masking dynamic fields |
| Approved Snapshot | The expected baseline snapshot |
| Diff Report | Detailed differences (on failure only) |

**Test metadata:**

- Endpoint URL and HTTP method
- Response time in milliseconds
- Pass/fail status with severity level
- Step-by-step execution trace

### Viewing Failed Tests

When a test fails, click on it in the report to see:

1. **Diff Report** attachment showing exactly what changed:
   ```
   === VALUE CHANGES ===
     $.quoteResponse.quoteNumber
       Expected: "<MASKED>"
       Actual:   "QT-01234567"
   ```

2. **Side-by-side comparison** of approved vs received snapshots

3. **Stack trace** with failure details

### Report Screenshots

**Overview Dashboard:**
- Pass/fail ratio pie chart
- Test duration statistics
- Trend graphs (if history available)

**Test Details:**
- Click any test to expand
- View all JSON attachments
- Download attachments for debugging

### CI/CD Integration

**GitHub Actions example with Allure:**

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run tests
        run: mvn test
        continue-on-error: true

      - name: Generate Allure Report
        run: mvn allure:report

      - name: Upload Allure Report
        uses: actions/upload-artifact@v4
        with:
          name: allure-report
          path: target/site/allure-maven-plugin/
```

### Allure Dependencies

The following dependencies are required in `pom.xml`:

```xml
<!-- Allure Reporting -->
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-junit5</artifactId>
    <version>2.29.0</version>
    <scope>test</scope>
</dependency>
```

And the Allure Maven plugin:

```xml
<plugin>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-maven</artifactId>
    <version>2.13.0</version>
    <configuration>
        <reportVersion>2.29.0</reportVersion>
    </configuration>
</plugin>
```

### Report History

To track trends across multiple test runs:

1. Copy `target/allure-results/history/` after each run
2. Place it in `target/allure-results/` before generating the next report
3. The report will show pass/fail trends over time

---

## Best Practices

### 1. Review Snapshot Changes Carefully

Before approving a new snapshot, always:
- Run `git diff` on the snapshot file
- Verify changes are expected due to your code changes
- Check for unintended structural changes

### 2. Keep Requests Realistic but Stable

Request payloads should:
- Use realistic data that exercises all code paths
- Avoid dates or values that cause different behavior over time
- Be committed and never modified without reason

### 3. Mask Conservatively

Only mask fields that **actually** change:
- Don't mask static fields "just in case"
- If a field becomes dynamic later, you'll catch it when tests fail

### 4. Use Global Rules for Common Patterns

If a field like `correlationId` appears in every response:
- Add it to `global.yml` once
- Don't repeat it in every endpoint config

### 5. One Endpoint Per Config File

This enables:
- Parallel development without merge conflicts
- Clear ownership of each endpoint's configuration
- Easy code review of endpoint-specific changes

---

## Troubleshooting

### Test Fails with "No configuration found for endpoint"

**Cause:** Missing YAML config file.

**Solution:** Create `snapshot-config/endpoints/{key}.yml` with required fields.

### Test Fails with "Request file not found"

**Cause:** Missing request JSON file.

**Solution:** Create `requests/{file}.json` as specified in endpoint config.

### Snapshot Comparison Shows Unexpected Differences

**Possible Causes:**
1. **New dynamic field:** Add it to masking paths
2. **Actual code change:** Review and approve if correct
3. **Environment difference:** Ensure tests run in consistent environment

### JSONPath Not Matching

**Debugging tips:**
1. Check for typos in path expressions
2. Verify the JSON structure matches your path
3. Use `$..field` for recursive matching if unsure of exact path
4. Array wildcards use `[*]` not `[]`

### Line Ending Issues on Windows

**Cause:** Git converting line endings.

**Solution:** The `.gitattributes` file should handle this:
```
*.json text eol=lf
*.yml text eol=lf
```

If issues persist, run:
```bash
git config core.autocrlf false
```

---

## Project Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies including JsonPath, JSONAssert, SnakeYAML |
| `src/main/resources/application.yml` | Spring Boot application configuration |
| `.gitignore` | Ignores `snapshots/received/` directory |
| `.gitattributes` | Ensures LF line endings for JSON/YAML files |

---

## Dependencies

```xml
<!-- JSONPath for field masking -->
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
</dependency>

<!-- JSON comparison with readable diffs -->
<dependency>
    <groupId>org.skyscreamer</groupId>
    <artifactId>jsonassert</artifactId>
    <scope>test</scope>
</dependency>

<!-- YAML configuration parsing -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
</dependency>

<!-- JUnit 5 parameterized tests -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-params</artifactId>
    <scope>test</scope>
</dependency>
```

---

## License

Internal use only. Not for distribution.



## How to use?
To use it for any API:

1. Create endpoint config in snapshot-config/endpoints/your-api.yml:
   endpoint:
   key: your-api
   method: POST          # or GET, PUT, DELETE
   url: /api/whatever
   requestFile: requests/your-api.json

masking:
paths:
- $.id              # root level
- $.data.userId     # nested
- $.items[*].uuid   # arrays
- $..timestamp      # recursive (any depth)

2. Create request payload (if POST/PUT)
3. Run mvn test -Dsnapshot.update=true

Works with:
- Any REST API returning JSON
- GraphQL responses
- Third-party integrations
- Microservice responses
- Any nesting depth or array structure

The only requirement is that responses are JSON. If you have XML APIs, you'd need to add an XML comparison component, but the same pattern would apply.


## Dev workflow

```aiignore
Developer Workflow:

  FIRST TIME (setup):
  ───────────────────
  mvn test -Dsnapshot.update=true
      │
      ├── Calls POST /api/v1/quotes/dwelling
      ├── Calls POST /api/v1/quotes/homeowners
      │
      └── Saves responses to:
          └── snapshots/approved/dwelling-quote.json
          └── snapshots/approved/homeowners-quote.json


  EVERY TIME AFTER (validation):
  ──────────────────────────────
  mvn test
      │
      ├── Calls POST /api/v1/quotes/dwelling
      ├── Compares response vs approved/dwelling-quote.json
      │   └── ✓ Match = PASS
      │   └── ✗ Different = FAIL
      │
      ├── Calls POST /api/v1/quotes/homeowners
      ├── Compares response vs approved/homeowners-quote.json
      │   └── ✓ Match = PASS
      │   └── ✗ Different = FAIL

  Summary:
  ┌──────────────────────────┬─────────────────────────────────┬─────────────────────────────────────┐
  │           Run            │             Command             │            What happens             │
  ├──────────────────────────┼─────────────────────────────────┼─────────────────────────────────────┤
  │ First time               │ mvn test -Dsnapshot.update=true │ Takes snapshots, saves to approved/ │
  ├──────────────────────────┼─────────────────────────────────┼─────────────────────────────────────┤
  │ Every time after         │ mvn test                        │ Compares against saved snapshots    │
  ├──────────────────────────┼─────────────────────────────────┼─────────────────────────────────────┤
  │ After intentional change │ mvn test -Dsnapshot.update=true │ Updates snapshots with new response │
  └──────────────────────────┴─────────────────────────────────┴─────────────────────────────────────┘
  That's the whole workflow.

```# -api-snapshot-testing-framework
