package feeders;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class CategoryIdFeeder {

    private final List<Integer> categoryIds;
    private final AtomicInteger index = new AtomicInteger();

    public CategoryIdFeeder(List<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("At least one category ID is required");
        }
        this.categoryIds = List.copyOf(categoryIds);
    }

    public Iterator<Map<String, Object>> circular() {
        return Stream.generate(() -> {
            int current = Math.floorMod(index.getAndIncrement(), categoryIds.size());
            return Collections.<String, Object>singletonMap("categoryId", categoryIds.get(current));
        }).iterator();
    }
}
