package assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.CsvWriter;
import model.PromotionRecord;
import model.TestConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * ResponseValidator
 * <p>
 * Stateless utility that validates a raw JSON response body against the
 * business rules defined in the assignment and extracts structured data
 * for CSV output.
 *
 * <p>Validation rules:
 * <ol>
 *   <li>The JSON {@code CategoryId} field must match the requested
 *       {@code categoryId}.</li>
 *   <li>The JSON {@code CanRelist} field must be present and equal
 *       {@code true}.</li>
 * </ol>
 *
 * <p>On success, one {@link PromotionRecord} is written to the CSV per
 * promotion entry.  If the category has no promotions a sentinel record is
 * written so the category still appears in the output.
 */
public final class ResponseValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Validate {@code responseBody} for the given {@code categoryId} and
     * write extracted data to the shared {@link CsvWriter}.
     *
     * @param categoryId   the category ID that was requested
     * @param responseBody raw JSON string from the API
     * @return {@code true} if all assertions pass; {@code false} otherwise
     */
    public static boolean validateAndExtract(int categoryId, String responseBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            System.err.printf("[Validator] [%d] JSON parse error: %s%n",
                    categoryId, e.getMessage());
            return false;
        }

        // ── Assertion 1: CategoryId must match ───────────────────────────
        JsonNode categoryIdNode = root.path("CategoryId");
        if (categoryIdNode.isMissingNode()) {
            System.err.printf("[Validator] [%d] 'CategoryId' field missing in response.%n",
                    categoryId);
            return false;
        }
        int actualId = categoryIdNode.asInt();
        if (actualId != categoryId) {
            System.err.printf("[Validator] [%d] CategoryId mismatch – expected %d, got %d.%n",
                    categoryId, categoryId, actualId);
            return false;
        }

        // ── Assertion 2: CanRelist must be true ──────────────────────────
        boolean valid = true;
        JsonNode canRelistNode = root.path("CanRelist");
        if (canRelistNode.isMissingNode()) {
            System.err.printf("[Validator] [%d] 'CanRelist' field missing in response.%n",
                    categoryId);
            valid = false;
        } else if (!canRelistNode.asBoolean()) {
            System.err.printf("[Validator] [%d] CanRelist assertion failed – expected true, got %s.%n",
                    categoryId, canRelistNode.asText());
            valid = false;
        }

        // ── Data extraction ──────────────────────────────────────────────
        String name       = root.path("Name").asText("");
        String path       = root.path("Path").asText("");
        JsonNode promos   = root.path("Promotions");

        List<PromotionRecord> records = new ArrayList<>();

        if (promos.isArray() && !promos.isEmpty()) {
            for (JsonNode promo : promos) {
                records.add(new PromotionRecord(
                        categoryId,
                        name,
                        path,
                        promo.path("Id").asText(""),
                        promo.path("Price").asText("")
                ));
            }
        } else {
            // No promotions – write a sentinel row so the category is represented
            records.add(new PromotionRecord(categoryId, name, path, "", ""));
        }

        CsvWriter.getInstance(TestConfig.CSV_OUTPUT).append(records);
        return valid;
    }

    private ResponseValidator() { /* utility class */ }
}
