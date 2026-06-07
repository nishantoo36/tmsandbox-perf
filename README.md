# TM Sandbox Categories API Performance Test

Gatling Java DSL performance test suite for:

`https://api.tmsandbox.co.nz/v1/Categories/{categoryId}/Details.json?catalogue=false`

The test covers the supplied category IDs `6327` through `6336`, validates the required response fields, writes promotion details to CSV, and produces a non-GUI Gatling HTML report.

## Project Structure

```text
tmsandbox-perf/
|-- pom.xml
|-- README.md
|-- src/test/java/
|   |-- assertions/ResponseValidator.java
|   |-- config/TestConfig.java
|   |-- feeders/CategoryIdFeeder.java
|   |-- model/CsvWriter.java
|   |-- model/PromotionRecord.java
|   |-- scenarios/CategoryDetailsScenario.java
|   |-- simulations/CategoriesApiSimulation.java
|   `-- support/
|       |-- RequestBudget.java
|       `-- StartupLog.java
|-- src/test/resources/
|   |-- logback-test.xml
|   `-- performance.properties
|-- results/category_results.csv
`-- reports/
    |-- lastRun.txt
    `-- categoriesapisimulation-20260607185556252/
        |-- index.html
        `-- simulation.log
```

## Prerequisites

| Requirement | Version |
|---|---:|
| Java JDK | 17+ |
| Maven | 3.8+ |
| Network | HTTPS access to `api.tmsandbox.co.nz` |

## Quick Start

Run the test headlessly:

```bash
mvn gatling:test
```

Open the latest HTML report:

```bash
open "reports/$(cat reports/lastRun.txt)/index.html"
```

Review extracted promotion data:

```bash
cat results/category_results.csv
```

## Configuration

Default execution data lives in `src/test/resources/performance.properties`. Edit that file when you want to change the endpoint, category IDs, VUser count, timing, throughput target, SLA, or CSV path for a normal local run.

Any value in the file can still be overridden from the command line with `-D`, which is useful for CI or one-off experiments.

| Property | Default | Purpose |
|---|---:|---|
| `perf.baseUrl` | `https://api.tmsandbox.co.nz` | API host under test. |
| `perf.vUsers` | `5` | VUsers/threads. Default is half of the 10 supplied category IDs. |
| `perf.rampUpSeconds` | `5` | Ramp duration. With 5 users, this ramps at 1 VUser per second. |
| `perf.steadyStateSeconds` | `60` | Window used to pace the configured request count. |
| `perf.totalRequests` | `10` | Total API calls made during the paced steady-state window. |
| `perf.p90ThresholdMs` | `500` | p90 response-time SLA assertion. |
| `perf.categoryIds` | `6327,6328,6329,6330,6331,6332,6333,6334,6335,6336` | Category IDs under test. |
| `perf.csvOutput` | `results/category_results.csv` | CSV output path. |

Example:

```bash
mvn gatling:test \
  -Dperf.baseUrl=https://api.tmsandbox.co.nz \
  -Dperf.categoryIds=6327,6328,6329,6330,6331,6332,6333,6334,6335,6336 \
  -Dperf.vUsers=5 \
  -Dperf.rampUpSeconds=5 \
  -Dperf.steadyStateSeconds=60 \
  -Dperf.totalRequests=10 \
  -Dperf.p90ThresholdMs=500
```

## Requirement Mapping

| Requirement | Implementation |
|---|---|
| Check response status | Gatling `status().is(200)` check. |
| Validate `CategoryId` | Gatling JSONPath check compares `$.CategoryId` to the requested category ID. |
| Validate `"CanRelist": true` | Gatling JSONPath check requires `$.CanRelist == true`; the Java validator also checks it before writing CSV rows. |
| Print Category ID, Name, Path, Promotion ID, Price | `ResponseValidator` extracts all promotions and `CsvWriter` writes one CSV row per promotion. |
| Print all Promotion IDs and Prices per Category ID | The validator iterates the full `Promotions` array for every successful category response. |
| NFR-01: VUsers half the category count | Default `perf.vUsers=5` for 10 category IDs. |
| NFR-02: 1 VUser per second ramp | Default `rampUsers(5).during(5 seconds)`. |
| NFR-03: 10 API calls in 1-minute steady state | Default `perf.totalRequests=10`; each VUser is paced at 30 seconds and a global counter caps total calls at exactly 10. |
| NFR-04: p90 within 500 ms | Gatling assertion `global().responseTime().percentile(90).lt(500)`. |

## Test Design

The simulation uses a small layered design:

```text
CategoriesApiSimulation
  |-- CategoryDetailsScenario: HTTP flow and Gatling checks
  |-- CategoryIdFeeder: round-robin category ID data
  |-- TestConfig: reads performance.properties and command-line overrides
  |-- RequestBudget: caps the run at the configured request count
  |-- ResponseValidator: scripted response validation and extraction
  `-- CsvWriter: thread-safe CSV output
```

The CSV writer is thread-safe, using a lock around appends so concurrent virtual users cannot interleave rows.

## Output Files

`results/category_results.csv` contains one row per promotion for every HTTP 200 category response. Category `6331` failed the `CanRelist=true` assertion in the latest run, but its category data is still included with blank promotion fields because the response had no promotions.

```csv
CategoryID,Name,Path,PromotionID,Price
6327,Carbon credits,/Business-farming-industry/Carbon-credits,1,0.0
6327,Carbon credits,/Business-farming-industry/Carbon-credits,2,2.0
6327,Carbon credits,/Business-farming-industry/Carbon-credits,3,10.0
6327,Carbon credits,/Business-farming-industry/Carbon-credits,4,15.0
```

The full current CSV is committed at `results/category_results.csv`.

Raw Gatling evidence is included at:

```text
reports/categoriesapisimulation-20260607185556252/simulation.log
reports/categoriesapisimulation-20260607185556252/index.html
```

## Performance Test Report

### Purpose

The purpose of this test is to confirm that the TM Sandbox Categories Details API returns valid category data and promotion details under the requested lightweight load profile: 5 VUsers, 1 VUser per second ramp-up, 10 total requests during a 60-second paced window, and p90 response time below 500 ms.

### Latest Local Execution

| Item | Value |
|---|---|
| Run folder | `reports/categoriesapisimulation-20260607185556252` |
| Execution mode | Non-GUI Maven/Gatling run |
| VUsers | 5 |
| Ramp-up | 5 seconds |
| Steady-state pacing window | 60 seconds |
| Total API calls | 10 |
| Target request rate | 10 requests/minute |
| Actual request rate | 0.15 requests/second |

### Observations

The test executed exactly 10 HTTP requests against the 10 supplied category IDs.

Nine requests passed the HTTP status, content type, `CategoryId`, and `CanRelist` checks. Category `6331` returned a successful HTTP response, but failed the required text check because the live response currently contains `CanRelist=false`. This is now reported as a Gatling KO:

```text
jsonPath($.CanRelist).find.is(true), but actually found false
```

From the current execution location, the API also did not meet the 500 ms p90 SLA. The run failed the performance gate with p90 `1268 ms`.

### Results Summary

| Metric | Result |
|---|---:|
| Total requests | 10 |
| Passed requests | 9 |
| Failed requests | 1 |
| Success rate | 90% |
| Min response time | 328 ms |
| Mean response time | 824 ms |
| p50 response time | 809 ms |
| p75 response time | 1192 ms |
| p90 response time | 1268 ms |
| p95 response time | 1370 ms |
| p99 response time | 1452 ms |
| Max response time | 1473 ms |

### NFR Outcome

| NFR | Requirement | Result | Status |
|---|---|---|---|
| NFR-01 | VUsers half the category count | 5 VUsers for 10 category IDs | PASS |
| NFR-02 | Ramp at 1 VUser per second | 5 users over 5 seconds | PASS |
| NFR-03 | 10 API calls in 60-second steady state | 10 calls executed | PASS |
| NFR-04 | 90% of responses within 500 ms | p90 was 1268 ms | FAIL |

### Assertion Outcome

| Assertion | Expected | Latest result |
|---|---|---|
| HTTP status | 200 | PASS for all 10 requests |
| Content type | contains `application/json` | PASS for all 10 requests |
| CategoryId | response matches requested ID | PASS for all 10 requests |
| CanRelist | `true` | FAIL for category `6331` |
| p90 | `< 500 ms` | FAIL, actual `1268 ms` |
| Success rate | `100%` | FAIL, actual `90%` |
| Mean response time | `< 500 ms` | FAIL, actual `824 ms` |

### Commentary

The script meets the requested test design and reporting requirements, but the latest live run does not pass all assertions. That is useful evidence rather than a scripting failure: the test caught both a functional mismatch in the current API data for category `6331` and a response-time SLA miss from this execution environment.

The assignment uses a very low request volume, so this is best treated as a baseline/SLA validation test rather than a capacity or stress test. For a production engagement, I would add a separate load-step scenario, a soak test, and a regional execution comparison so network latency can be separated from API processing time.

## Assumptions

| # | Assumption |
|---|---|
| A1 | The endpoint is public and does not require authentication. |
| A2 | The supplied category IDs are the complete required data set for this assignment. |
| A3 | `perf.totalRequests=10` means exactly 10 request attempts for the default run, not 10 requests per user. |
| A4 | A category response that fails `CanRelist=true` should be counted as a failed request. If the response is otherwise parseable, its category/promotion data is still written to CSV for reporting. |
| A5 | The `Promotions` array may contain multiple entries; each entry must produce its own CSV row. |
| A6 | `Price` is written exactly as returned by the API, without currency formatting or rounding. |
| A7 | Results are environment-sensitive because the target is a public internet endpoint. The included report reflects the local run captured in this repository. |
