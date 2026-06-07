package assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.CsvWriter;
import model.PromotionRecord;
import model.TestConfig;

import java.util.ArrayList;
import java.util.List;

public final class ResponseValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static boolean validateAndExtract(int expectedCategoryId, String responseBody) {
        JsonNode root = parseJson(expectedCategoryId, responseBody);
        if (root == null) {
            return false;
        }

        boolean categoryValid = validateCategoryId(expectedCategoryId, root);
        if (!categoryValid) {
            return false;
        }

        boolean canRelistValid = validateCanRelist(expectedCategoryId, root);
        CsvWriter.getInstance(TestConfig.CSV_OUTPUT).append(extractPromotions(expectedCategoryId, root));
        return canRelistValid;
    }

    private static JsonNode parseJson(int categoryId, String responseBody) {
        try {
            return MAPPER.readTree(responseBody);
        } catch (Exception e) {
            System.err.printf("[Validator] [%d] JSON parse error: %s%n", categoryId, e.getMessage());
            return null;
        }
    }

    private static boolean validateCategoryId(int expectedCategoryId, JsonNode root) {
        JsonNode categoryIdNode = root.path("CategoryId");
        if (categoryIdNode.isMissingNode()) {
            System.err.printf("[Validator] [%d] CategoryId field is missing%n", expectedCategoryId);
            return false;
        }

        int actualCategoryId = categoryIdNode.asInt();
        if (actualCategoryId != expectedCategoryId) {
            System.err.printf("[Validator] [%d] CategoryId mismatch; got %d%n",
                    expectedCategoryId, actualCategoryId);
            return false;
        }
        return true;
    }

    private static boolean validateCanRelist(int categoryId, JsonNode root) {
        JsonNode canRelistNode = root.path("CanRelist");
        if (canRelistNode.isMissingNode()) {
            System.err.printf("[Validator] [%d] CanRelist field is missing%n", categoryId);
            return false;
        }

        if (!canRelistNode.asBoolean()) {
            System.err.printf("[Validator] [%d] CanRelist expected true, got %s%n",
                    categoryId, canRelistNode.asText());
            return false;
        }
        return true;
    }

    private static List<PromotionRecord> extractPromotions(int categoryId, JsonNode root) {
        String name = root.path("Name").asText("");
        String path = root.path("Path").asText("");
        JsonNode promotions = root.path("Promotions");

        List<PromotionRecord> records = new ArrayList<>();
        if (promotions.isArray() && !promotions.isEmpty()) {
            for (JsonNode promotion : promotions) {
                records.add(new PromotionRecord(
                        categoryId,
                        name,
                        path,
                        promotion.path("Id").asText(""),
                        promotion.path("Price").asText("")
                ));
            }
        } else {
            records.add(new PromotionRecord(categoryId, name, path, "", ""));
        }
        return records;
    }

    private ResponseValidator() {
    }
}
