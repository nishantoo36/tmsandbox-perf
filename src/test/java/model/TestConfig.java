package model;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class TestConfig {

    public static final String BASE_URL =
            System.getProperty("perf.baseUrl", "https://api.tmsandbox.co.nz");

    public static final List<Integer> CATEGORY_IDS =
            Arrays.stream(System.getProperty(
                            "perf.categoryIds",
                            "6327,6328,6329,6330,6331,6332,6333,6334,6335,6336"
                    ).split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Integer::parseInt)
                    .toList();

    public static final int V_USERS =
            positiveIntProp("perf.vUsers", Math.max(1, CATEGORY_IDS.size() / 2));

    public static final int RAMP_UP_SECONDS =
            positiveIntProp("perf.rampUpSeconds", V_USERS);

    public static final int STEADY_STATE_SECONDS =
            positiveIntProp("perf.steadyStateSeconds", 60);

    public static final int TOTAL_REQUESTS =
            positiveIntProp("perf.totalRequests", 10);

    public static final int P90_THRESHOLD_MS =
            positiveIntProp("perf.p90ThresholdMs", 500);

    public static final String CSV_OUTPUT =
            System.getProperty("perf.csvOutput", "results/category_results.csv");

    public static int requestsPerUser() {
        return (int) Math.ceil((double) TOTAL_REQUESTS / V_USERS);
    }

    public static Duration requestPace() {
        long seconds = Math.max(1L, Math.round((double) STEADY_STATE_SECONDS / requestsPerUser()));
        return Duration.ofSeconds(seconds);
    }

    public static double targetThroughputPerMinute() {
        return 60.0 * TOTAL_REQUESTS / STEADY_STATE_SECONDS;
    }

    private static int positiveIntProp(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Use the common warning below.
        }

        System.err.printf("[TestConfig] Invalid value '%s' for '%s'; using default %d%n",
                value, key, defaultValue);
        return defaultValue;
    }

    private TestConfig() {
    }
}
