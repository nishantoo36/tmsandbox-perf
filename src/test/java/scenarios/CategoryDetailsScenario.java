package scenarios;

import assertions.ResponseValidator;
import config.TestConfig;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import support.RequestBudget;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class CategoryDetailsScenario {

    private final RequestBudget requestBudget = new RequestBudget(TestConfig.TOTAL_REQUESTS);
    private final AtomicInteger categoryIndex = new AtomicInteger();

    public HttpProtocolBuilder httpProtocol() {
        return http
                .baseUrl(TestConfig.BASE_URL)
                .acceptHeader("application/json")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader("Gatling-PerfTest/1.0");
    }

    public ScenarioBuilder build() {
        return scenario("Categories API Load Test")
                .repeat(TestConfig.requestsPerUser()).on(
                        pause(TestConfig.requestPace())
                                .doIf(session -> requestBudget.tryUse())
                                .then(categoryDetailsRequest())
                );
    }

    private ChainBuilder categoryDetailsRequest() {
        return feed(this::categoryIdFeeder)
                .exec(
                        http("GET /v1/Categories/#{categoryId}/Details.json")
                                .get("/v1/Categories/#{categoryId}/Details.json")
                                .queryParam("catalogue", "false")
                                .check(
                                        status().is(200),
                                        header("Content-Type").find()
                                                .transform(contentType -> contentType.contains("application/json"))
                                                .is(true),
                                        bodyString().saveAs("responseBody"),
                                        jsonPath("$.CategoryId").ofInt().is(session -> session.getInt("categoryId")),
                                        jsonPath("$.CanRelist").ofBoolean().is(true)
                                )
                )
                .exec(session -> {
                    if (!session.contains("responseBody")) {
                        return session;
                    }

                    int categoryId = session.getInt("categoryId");
                    boolean valid = ResponseValidator.validateAndExtract(
                            categoryId,
                            session.getString("responseBody")
                    );

                    if (!valid) {
                        System.err.printf("[Simulation] Business assertion failed for CategoryId=%d%n", categoryId);
                        return session.markAsFailed();
                    }
                    return session;
                });
    }

    private Iterator<Map<String, Object>> categoryIdFeeder() {
        return Stream.generate(() -> {
            int current = Math.floorMod(categoryIndex.getAndIncrement(), TestConfig.CATEGORY_IDS.size());
            return Collections.<String, Object>singletonMap("categoryId", TestConfig.CATEGORY_IDS.get(current));
        }).iterator();
    }
}
