package model;

/**
 * PromotionRecord
 * <p>
 * Immutable value object that captures the fields written to the results CSV
 * for a single promotion line within a category response.
 *
 * <p>When a category carries no promotions a sentinel record is written with
 * {@code promotionId = ""} and {@code price = ""} so that the category still
 * appears in the output.
 */
public record PromotionRecord(
        int    categoryId,
        String name,
        String path,
        String promotionId,
        String price
) {
    /** CSV header matching the field order below. */
    public static final String CSV_HEADER =
            "CategoryID,Name,Path,PromotionID,Price";

    /**
     * Renders the record as a single CSV line.
     * Fields containing commas are enclosed in double-quotes.
     */
    public String toCsvLine() {
        return String.join(",",
                String.valueOf(categoryId),
                csvEscape(name),
                csvEscape(path),
                promotionId,
                price
        );
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
