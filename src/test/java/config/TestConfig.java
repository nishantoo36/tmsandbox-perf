package config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class TestConfig {

    private static final String CONFIG_FILE = "performance.properties";
    private static final Properties FILE_PROPS = loadProperties();

    public static final String BASE_URL = stringProp("perf.baseUrl");
    public static final List<Integer> CATEGORY_IDS = categoryIds();
    public static final int V_USERS = positiveIntProp("perf.vUsers", Math.max(1, CATEGORY_IDS.size() / 2));
    public static final int RAMP_UP_SECONDS = positiveIntProp("perf.rampUpSeconds", V_USERS);
    public static final int STEADY_STATE_SECONDS = positiveIntProp("perf.steadyStateSeconds", 60);
    public static final int TOTAL_REQUESTS = positiveIntProp("perf.totalRequests", 10);
    public static final int P90_THRESHOLD_MS = positiveIntProp("perf.p90ThresholdMs", 500);
    public static final String CSV_OUTPUT = stringProp("perf.csvOutput");

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

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = TestConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IllegalStateException(CONFIG_FILE + " not found on the test classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + CONFIG_FILE, e);
        }
        return properties;
    }

    private static String stringProp(String key) {
        String value = System.getProperty(key, FILE_PROPS.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static List<Integer> categoryIds() {
        return Arrays.stream(stringProp("perf.categoryIds").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    private static int positiveIntProp(String key, int defaultValue) {
        String value = System.getProperty(key, FILE_PROPS.getProperty(key));
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Use the warning below.
        }

        System.err.printf("[TestConfig] Invalid value '%s' for '%s'; using default %d%n",
                value, key, defaultValue);
        return defaultValue;
    }

    private TestConfig() {
    }
}
