package simulations;

import assertions.ResponseValidator;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import model.TestConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * CategoriesApiSimulation
 * <p>
 * Gatling (Java) performance test for the TM Sandbox Categories API.
 *
 * <h3>NFR coverage</h3>
 * <table border="1">
 *   <tr><th>NFR</th><th>Requirement</th><th>Implementation</th></tr>
 *   <tr>
 *     <td>NFR-01</td>
 *     <td>VUsers = half the count of category IDs</td>
 *     <td>{@code TestConfig.V_USERS} (default 5)</td>
 *   </tr>
 *   <tr>
 *     <td>NFR-02</td>
 *     <td>Ramp up at 1 VUser / second</td>
 *     <td>{@code rampUsers(...).during(...)}</td>
 *   </tr>
 *   <tr>
 *     <td>NFR-03</td>
 *     <td>10 API calls in 60 s steady state</td>
 *     <td>Throttle at {@code TestConfig.throttleRps()} RPS</td>
 *   </tr>
 *   <tr>
 *     <td>NFR-04</td>
 *     <td>p90 ≤ 500 ms</td>
 *     <td>Gatling {@code global().responseTime().percentile(90).lt(500)}</td>
 *   </tr>
 * </table>
 *
 * <h3>Configurable parameters (via -D Maven properties)</h3>
 * <pre>
 *   mvn gatling:test \
 *     -Dperf.vUsers=5 \
 *     -Dperf.rampUpSeconds=5 \
 *     -Dperf.steadyStateSeconds=60 \
 *     -Dperf.totalRequests=10 \
 *     -Dperf.p90ThresholdMs=500 \
 *     -Dperf.categoryIds=6327,6328,6329,6330,6331,6332,6333,6334,6335,6336
 * </pre>
 */
public class CategoriesApiSimulation extends Simulation {

    // ── Print effective config at startup ─────────────────────────────────
    static {
        System.out.printf("""
                %n╔══════════════════════════════════════════════════════════╗
                ║        TM Sandbox Categories API – Gatling Test           ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Base URL       : %-38s ║
                ║  Category IDs   : %-38s ║
                ║  VUsers         : %-38d ║
                ║  Ramp-up (s)    : %-38d ║
                ║  Steady state   : %-38d ║
                ║  Total requests : %-38d ║
                ║  Requests/User  : %-38d ║
                ║  Request pace   : %-38s ║
                ║  Target RPM     : %-38.2f ║
                ║  p90 SLA (ms)   : %-38d ║
                ║  CSV output     : %-38s ║
                ╚══════════════════════════════════════════════════════════╝%n""",
                TestConfig.BASE_URL,
                TestConfig.CATEGORY_IDS.toString(),
                TestConfig.V_USERS,
                TestConfig.RAMP_UP_SECONDS,
                TestConfig.STEADY_STATE_SECONDS,
                TestConfig.TOTAL_REQUESTS,
                TestConfig.requestsPerUser(),
                TestConfig.requestPace(),
                TestConfig.targetThroughputPerMinute(),
                TestConfig.P90_THRESHOLD_MS,
                TestConfig.CSV_OUTPUT
        );
    }

    // ── Round-robin feeder over category IDs ─────────────────────────────
    private static final AtomicInteger CATEGORY_COUNTER = new AtomicInteger(0);
    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger(0);

    private static Iterator<Map<String, Object>> categoryIdFeeder() {
        List<Integer> ids = TestConfig.CATEGORY_IDS;
        return Stream.generate(() -> {
            int idx = Math.floorMod(CATEGORY_COUNTER.getAndIncrement(), ids.size());
            return Collections.<String, Object>singletonMap("categoryId", ids.get(idx));
        }).iterator();
    }

    // ── HTTP protocol ─────────────────────────────────────────────────────
    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(TestConfig.BASE_URL)
            .acceptHeader("application/json")
            .acceptEncodingHeader("gzip, deflate")
            .userAgentHeader("Gatling-PerfTest/1.0");

    // ── Request chain ─────────────────────────────────────────────────────
    private final ChainBuilder categoryDetailsRequest = feed(categoryIdFeeder())
            .exec(
                http("GET /v1/Categories/#{categoryId}/Details.json")
                    .get("/v1/Categories/#{categoryId}/Details.json")
                    .queryParam("catalogue", "false")
                    .check(
                        // 1. HTTP 200
                        status().is(200),
                        // 2. Content-Type is JSON
                        header("Content-Type").find()
                                .transform(contentType -> contentType.contains("application/json"))
                                .is(true),
                        // 3. Capture body for scripted assertions and CSV extraction
                        bodyString().saveAs("responseBody"),
                        // 4. Assignment checks reflected in Gatling OK/KO counts
                        jsonPath("$.CategoryId").ofInt().is(session -> session.getInt("categoryId")),
                        jsonPath("$.CanRelist").ofBoolean().is(true)
                    )
            )
            // Business-rule assertions + CSV extraction run after the HTTP check
            .exec(session -> {
                if (!session.contains("responseBody")) {
                    return session;
                }

                String body       = session.getString("responseBody");
                int    categoryId = session.getInt("categoryId");

                boolean valid = ResponseValidator.validateAndExtract(categoryId, body);
                if (!valid) {
                    System.err.printf(
                            "[Simulation] Business assertion FAILED for CategoryId=%d%n",
                            categoryId);
                    // Mark the session as failed so Gatling counts the assertion error
                    return session.markAsFailed();
                }
                return session;
            });

    // ── Scenario ──────────────────────────────────────────────────────────
    private final ScenarioBuilder scenario = scenario("Categories API Load Test")
            .repeat(TestConfig.requestsPerUser()).on(
                pause(TestConfig.requestPace())
                    .doIf(session -> REQUEST_COUNTER.getAndIncrement() < TestConfig.TOTAL_REQUESTS)
                    .then(categoryDetailsRequest)
            );

    // ── Injection profile ─────────────────────────────────────────────────
    {
        setUp(
            scenario.injectOpen(
                // NFR-02: ramp at 1 VUser/second
                rampUsers(TestConfig.V_USERS)
                    .during(Duration.ofSeconds(TestConfig.RAMP_UP_SECONDS))
            )
        )
        .protocols(httpProtocol)
        // NFR-04: fail the build if p90 breaches the SLA
        .assertions(
            global().responseTime()
                    .percentile(90)
                    .lt(TestConfig.P90_THRESHOLD_MS),
            // Additional quality gates
            global().successfulRequests()
                    .percent()
                    .gte(100.0),
            global().responseTime()
                    .mean()
                    .lt(TestConfig.P90_THRESHOLD_MS)
        );
    }
}
