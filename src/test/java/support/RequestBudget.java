package support;

import java.util.concurrent.atomic.AtomicInteger;

public final class RequestBudget {

    private final int maxRequests;
    private final AtomicInteger usedRequests = new AtomicInteger();

    public RequestBudget(int maxRequests) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("Request budget must be positive");
        }
        this.maxRequests = maxRequests;
    }

    public boolean tryUse() {
        return usedRequests.getAndIncrement() < maxRequests;
    }
}
