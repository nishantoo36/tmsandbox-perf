package model;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TestConfig
 * <p>
 * Single source of truth for every runtime parameter.  Values are read from
 * Java system properties so they can be overridden on the Maven CLI:
 *
 * <pre>
 *   mvn gatling:test \
 *     -Dperf.vUsers=10 \
 *     -Dperf.rampUpSeconds=10 \
 *     -Dperf.steadyStateSeconds=120 \
 *     -Dperf.totalRequests=20 \
 *     -Dperf.p90ThresholdMs=500
 * </pre>
 *
 * <p>Defaults satisfy all NFRs with the supplied test data (10 category IDs):
 * <ul>
 *   <li>NFR-01  – vUsers = 5 (half of 10)</li>
 *   <li>NFR-02  – rampUpSeconds = 5 (1 VUser / second)</li>
 *   <li>NFR-03  – totalRequests = 10 in steadyStateSeconds = 60</li>
 *   <li>NFR-04  – p90ThresholdMs = 500</li>
 * </ul>
 */
public final class TestConfig {

    // ── API ────────────────────────────────────────────────────────────────
    public static final String BASE_URL =
            System.getProperty("perf.baseUrl", "https://api.tmsandbox.co.nz");

    /** Category IDs under test (comma-separated, e.g. "6327,6328,..."). */
    public static final List<Integer> CATEGORY_IDS =
            Arrays.stream(
                    System.getProperty(
                            "perf.categoryIds",
                            "6327,6328,6329,6330,6331,6332,6333,6334,6335,6336"
                    ).split(","))
                  .map(String::trim)
                  .map(Integer::parseInt)
                  .collect(Collectors.toUnmodifiableList());

    // ── Load model ─────────────────────────────────────────────────────────

    /** NFR-01: concurrent virtual users. Default = half of category IDs. */
    public static final int V_USERS =
            intProp("perf.vUsers", CATEGORY_IDS.size() / 2);

    /** NFR-02: seconds taken to reach full VUser count (1 VUser / second). */
    public static final int RAMP_UP_SECONDS =
            intProp("perf.rampUpSeconds", V_USERS); // 1 user/s → ramp = vUsers seconds

    /** NFR-03: steady-state window duration in seconds. */
    public static final int STEADY_STATE_SECONDS =
            intProp("perf.steadyStateSeconds", 60);

    /**
     * NFR-03: target total API calls across the steady-state window.
     */
    public static final int TOTAL_REQUESTS =
            intProp("perf.totalRequests", 10);

    /** NFR-04: p90 response-time SLA in milliseconds. */
    public static final int P90_THRESHOLD_MS =
            intProp("perf.p90ThresholdMs", 500);

    // ── Output ─────────────────────────────────────────────────────────────

    /** Path (relative to project root) where category results are written. */
    public static final String CSV_OUTPUT =
            System.getProperty("perf.csvOutput", "results/category_results.csv");

    // ── Derived ────────────────────────────────────────────────────────────

    /** Number of request attempts each VUser schedules. */
    public static int requestsPerUser() {
        return (int) Math.ceil((double) TOTAL_REQUESTS / V_USERS);
    }

    /** Delay between each VUser's request attempts. */
    public static Duration requestPace() {
        long seconds = Math.max(1L, Math.round((double) STEADY_STATE_SECONDS / requestsPerUser()));
        return Duration.ofSeconds(seconds);
    }

    /** Informational target throughput for reports and startup logging. */
    public static double targetThroughputPerMinute() {
        return 60.0 * TOTAL_REQUESTS / STEADY_STATE_SECONDS;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static int intProp(String key, int defaultValue) {
        String val = System.getProperty(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            System.err.printf("[TestConfig] Invalid value '%s' for '%s'; using default %d%n",
                    val, key, defaultValue);
            return defaultValue;
        }
    }

    private TestConfig() { /* utility class */ }
}
