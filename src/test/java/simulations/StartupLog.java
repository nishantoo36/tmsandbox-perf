package simulations;

import model.TestConfig;

final class StartupLog {

    static void print() {
        System.out.printf("""
                
                TM Sandbox Categories API performance test
                Base URL       : %s
                Category IDs   : %s
                VUsers         : %d
                Ramp-up        : %d seconds
                Steady state   : %d seconds
                Total requests : %d
                Requests/user  : %d
                Request pace   : %s
                Target rate    : %.2f requests/minute
                p90 SLA        : %d ms
                CSV output     : %s
                
                """,
                TestConfig.BASE_URL,
                TestConfig.CATEGORY_IDS,
                TestConfig.V_USERS,
                TestConfig.RAMP_UP_SECONDS,
                TestConfig.STEADY_STATE_SECONDS,
                TestConfig.TOTAL_REQUESTS,
                TestConfig.requestsPerUser(),
                TestConfig.requestPace(),
                TestConfig.targetThroughputPerMinute(),
                TestConfig.P90_THRESHOLD_MS,
                TestConfig.CSV_OUTPUT
        );
    }

    private StartupLog() {
    }
}
