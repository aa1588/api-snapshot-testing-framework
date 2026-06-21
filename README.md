# API Snapshot Testing Framework

Captures JSON API responses as baselines and compares future responses against them.

## Commands

| Command | Purpose |
|---------|---------|
| `mvn test -Dsnapshot.update=true` | Create/update baseline snapshots |
| `mvn test` | Verify responses match baselines |
| `mvn test -Dsnapshot.endpoint=<key>` | Test single endpoint |
| `mvn allure:serve` | View HTML test report |

## Add New Endpoint

**1. Create endpoint config** `src/test/resources/snapshot-config/endpoints/{key}.yml`:

```yaml
endpoint:
  key: order
  method: POST
  url: /api/demo/order
  requestFile: requests/order.json

masking:
  paths:
    - $.timestamp           # root level
    - $.metadata.requestId  # nested
    - $.items[*].id         # array elements
    - $..createdAt          # recursive (any depth)
```

**2. Create request payload** `src/test/resources/requests/{key}.json`

**3. Capture baseline:**
```bash
mvn test -Dsnapshot.update=true
```

## Project Structure

```
src/test/resources/
├── snapshot-config/
│   ├── global.yml              # Masking rules for ALL endpoints
│   └── endpoints/
│       └── order.yml           # Per-endpoint config
├── requests/
│   └── order.json              # Request payloads
└── snapshots/
    ├── approved/               # Baselines (commit to git)
    │   ├── order.json          # Masked response (used for comparison)
    │   └── order.raw.json      # Original unmasked response (for records)
    └── received/               # Current responses (gitignored)
```

## Masking Dynamic Fields

Fields that change every request (IDs, timestamps) must be masked:

| Pattern | Example | Matches |
|---------|---------|---------|
| `$.field` | `$.orderId` | Root level field |
| `$.a.b` | `$.metadata.requestId` | Nested field |
| `$.arr[*].field` | `$.items[*].processedAt` | All array elements |
| `$..field` | `$..timestamp` | Any depth (recursive) |

Add patterns to `global.yml` (all endpoints) or endpoint-specific `.yml` files.

**Note:** When you change masking paths, re-capture the baseline:

```bash
mvn test -Dsnapshot.update=true
```

## Complete Workflow

### Step 1: Setup (once per endpoint)

Create the endpoint config and request file:

```
src/test/resources/
├── snapshot-config/endpoints/order.yml    # endpoint config
└── requests/order.json                     # request payload
```

Start with **empty masking** to discover dynamic fields:

```yaml
masking:
  paths: []
```

Capture initial baseline:

```bash
mvn test -Dsnapshot.update=true
```

### Step 2: Discover Dynamic Fields

Run the test again:

```bash
mvn test
```

Test will fail. The diff report shows exactly which fields changed:

```
=== MISMATCHES ===
  $.orderId
    Baseline: "ORD-353628"
    Current:  "ORD-864417"
  $.createdAt
    Baseline: "2026-06-21T21:47:51Z"
    Current:  "2026-06-21T22:01:28Z"
  $.metadata.requestId
    Baseline: "req-da079692"
    Current:  "req-f743f4fe"
```

Add those paths to your masking config:

```yaml
masking:
  paths:
    - $.orderId
    - $.createdAt
    - $.metadata.requestId
```

Update the baseline with masking applied:

```bash
mvn test -Dsnapshot.update=true
```

Repeat until `mvn test` passes.

### Step 3: Commit Baseline

```bash
git add src/test/resources/snapshot-config/endpoints/order.yml
git add src/test/resources/requests/order.json
git add src/test/resources/snapshots/approved/order.json
git commit -m "Add order endpoint snapshot"
```

### Step 4: Daily Development

After making code changes:

```bash
mvn test
```

| Result | Meaning | Action |
|--------|---------|--------|
| Pass | Response structure unchanged | Nothing to do |
| Fail (unexpected) | You broke something | Fix your code |
| Fail (expected) | Intentional change | Run `mvn test -Dsnapshot.update=true` |

### Example: Catching a Bug

```
1. You refactor OrderService.java
2. Run: mvn test
3. Test fails:

   === MISMATCHES ===
     $.total
       Baseline: 109.97
       Current:  99.97

4. Your refactor broke the price calculation - fix your code
5. Run: mvn test
6. ✓ PASSED
```

## Sample Request

```bash
curl -X POST http://localhost:8080/api/demo/order \
    -u api-user:api-secret \
    -H "Content-Type: application/json" \
    -d '{
      "customerId": "CUST-001",
      "items": [
        {"productId": "PROD-A1", "quantity": 2},
        {"productId": "PROD-B2", "quantity": 1}
      ]
    }'
```