package model;

public record PromotionRecord(
        int categoryId,
        String name,
        String path,
        String promotionId,
        String price
) {
    public static final String CSV_HEADER = "CategoryID,Name,Path,PromotionID,Price";

    public String toCsvLine() {
        return String.join(",",
                csvEscape(String.valueOf(categoryId)),
                csvEscape(name),
                csvEscape(path),
                csvEscape(promotionId),
                csvEscape(price)
        );
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
