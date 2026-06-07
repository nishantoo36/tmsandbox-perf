package simulations;

import java.util.concurrent.atomic.AtomicInteger;

final class RequestBudget {

    private final int maxRequests;
    private final AtomicInteger usedRequests = new AtomicInteger();

    RequestBudget(int maxRequests) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("Request budget must be positive");
        }
        this.maxRequests = maxRequests;
    }

    boolean tryUse() {
        return usedRequests.getAndIncrement() < maxRequests;
    }
}
