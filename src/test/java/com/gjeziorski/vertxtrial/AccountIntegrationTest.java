package com.gjeziorski.vertxtrial;

import static org.assertj.core.api.Assertions.assertThat;

import com.gjeziorski.vertxtrial.verticles.HttpVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class AccountIntegrationTest {

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.deployVerticle(new HttpVerticle(), vertxTestContext.completing());
    }

    @Test
    void testShouldReturn404OnNotSupportedUrl(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        client.get(8080, "localhost", "/").as(BodyCodec.string())
            .send(vertxTestContext.succeeding(response -> vertxTestContext.verify(() -> {
                assertThat(response.statusCode()).isEqualTo(404);
                vertxTestContext.completeNow();
            })));
    }

    @Test
    void testShouldReturn201OnProperInput(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        JsonObject account = new JsonObject().put("name", "John").put("surname", "Doe");
        client.post(8080, "localhost", "/api/accounts")
            .sendJson(account, vertxTestContext.succeeding(response -> vertxTestContext.verify(() -> {
                assertThat(response.statusCode()).isEqualTo(201);
                vertxTestContext.completeNow();
            })));
    }

    @Test
    void testShouldReturn400OnMissingInputField(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);
        JsonObject account = new JsonObject().put("name", "John");
        client.post(8080, "localhost", "/api/accounts")
            .sendJson(account, vertxTestContext.succeeding(response -> vertxTestContext.verify(() -> {
                assertThat(response.statusCode()).isEqualTo(400);
                vertxTestContext.completeNow();
            })));
    }

}
