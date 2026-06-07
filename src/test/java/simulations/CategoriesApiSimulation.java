package simulations;

import config.TestConfig;
import io.gatling.javaapi.core.Simulation;
import scenarios.CategoryDetailsScenario;
import support.StartupLog;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;

public class CategoriesApiSimulation extends Simulation {

    private final CategoryDetailsScenario categories = new CategoryDetailsScenario();

    {
        StartupLog.print();

        setUp(
                categories.build().injectOpen(
                        rampUsers(TestConfig.V_USERS)
                                .during(Duration.ofSeconds(TestConfig.RAMP_UP_SECONDS))
                )
        )
                .protocols(categories.httpProtocol())
                .assertions(
                        global().responseTime().percentile(90).lt(TestConfig.P90_THRESHOLD_MS),
                        global().successfulRequests().percent().gte(100.0),
                        global().responseTime().mean().lt(TestConfig.P90_THRESHOLD_MS)
                );
    }
}
