package com.gjeziorski.vertxtrial;


import static com.gjeziorski.vertxtrial.common.ErrorCodesTranslator.ACCOUNT_DOESNT_EXIST_MESSAGE;
import static com.gjeziorski.vertxtrial.common.ErrorCodesTranslator.INVALID_TRANSACTION_AMOUNT_MESSAGE;
import static com.gjeziorski.vertxtrial.common.ErrorCodesTranslator.INVALID_TRANSACTION_TYPE_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.gjeziorski.vertxtrial.verticles.HttpVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
public class TransactionIntegrationTest {

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.deployVerticle(new HttpVerticle(), vertxTestContext.completing());
    }

    @Test
    void testShouldReturnTransactionListAfterValidDeposit(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);

        JsonObject account = new JsonObject().put("name", "John").put("surname", "Doe");
        JsonObject transaction = new JsonObject().put("amount", 100).put("destination_account_id", 0)
            .put("transaction_type", "DEPOSIT");

        client.post(8080, "localhost", "/api/accounts")
            .rxSendJson(account)
            .flatMap(result -> client.post(8080, "localhost", "/api/transactions").rxSendJson(transaction))
            .flatMap(result -> client.get(8080, "localhost", "/api/transactions?account-id=0").rxSend())
            .subscribe(result -> vertxTestContext.verify(
                () -> {
                    assertThat(result.statusCode()).isEqualTo(200);
                    vertxTestContext.completeNow();
                }
            ));
    }

    @Test
    void testShouldReturn201OnValidTransfer(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);

        JsonObject firstAccount = new JsonObject().put("name", "John").put("surname", "Doe");
        JsonObject secondAccount = new JsonObject().put("name", "Jane").put("surname", "Doe");
        JsonObject deposit = new JsonObject().put("amount", 100).put("destination_account_id", 0)
            .put("transaction_type", "DEPOSIT");
        JsonObject transfer = new JsonObject().put("amount", 100).put("source_account_id", 0)
            .put("destination_account_id", 1)
            .put("transaction_type", "TRANSFER");

        client.post(8080, "localhost", "/api/accounts")
            .rxSendJson(firstAccount)
            .flatMap(firstAccountId -> client.post(8080, "localhost", "/api/accounts").rxSendJson(secondAccount))
            .flatMap(secondAccountId -> client.post(8080, "localhost", "/api/transactions").rxSendJson(deposit))
            .flatMap(depositResponse -> client.post(8080, "localhost", "/api/transactions").rxSendJson(transfer))
            .subscribe(result -> vertxTestContext.verify(
                () -> {
                    assertThat(result.statusCode()).isEqualTo(201);
                    vertxTestContext.completeNow();
                }
            ));
    }


    @Test
    void testShouldReturn400OnInsufficientBalance(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);

        JsonObject account = new JsonObject().put("name", "John").put("surname", "Doe");
        JsonObject transaction = new JsonObject().put("amount", 100).put("destination_account_id", 0)
            .put("transaction_type", "WITHDRAW");

        client.post(8080, "localhost", "/api/accounts")
            .rxSendJson(account)
            .flatMap(result -> client.post(8080, "localhost", "/api/transactions").rxSendJson(transaction))
            .subscribe(result -> vertxTestContext.verify(
                () -> {
                    assertThat(result.statusCode()).isEqualTo(400);
                    vertxTestContext.completeNow();
                }
            ));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void testBadRequestOnInvalidPostRequest(JsonObject request, String errorMessage, Vertx vertx,
        VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);

        client.post(8080, "localhost", "/api/transactions")
            .rxSendJson(request)
            .subscribe(result -> vertxTestContext.verify(
                () -> {
                    assertThat(result.statusCode()).isEqualTo(400);
                    assertThat(result.body().toString()).isEqualTo(errorMessage);
                    vertxTestContext.completeNow();
                }
            ));
    }

    @ParameterizedTest
    @MethodSource("getUrls")
    void testRequestCodesOnGetRequest(String url, int httpCode, Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient client = WebClient.create(vertx);

        client.get(8080, "localhost", url)
            .rxSend()
            .subscribe(result -> vertxTestContext.verify(
                () -> {
                    assertThat(result.statusCode()).isEqualTo(httpCode);
                    vertxTestContext.completeNow();
                }
            ));
    }

    private static Object[] getUrls() {
        return new Object[]{
            new Object[]{"/api/transactions", 400},
            new Object[]{"/api/transactions?account-id=5", 200},
            new Object[]{"/api/transactions?account-id=5&from=2020-01-29T17:29:50Z", 200},
            new Object[]{"/api/transactions?account-id=5&from=piatek", 400}
        };
    }

    private static Object[] invalidRequests() {
        final JsonObject nonExistentSourceAccountIdRequest = new JsonObject().put("amount", 100)
            .put("source_account_id", 100)
            .put("destination_account_id", 0)
            .put("transaction_type", "TRANSFER");

        final JsonObject nonExistentDestinationAccountIdRequest = new JsonObject().put("amount", 100)
            .put("destination_account_id", 100)
            .put("transaction_type", "DEPOSIT");

        final JsonObject negativeAmountRequest = new JsonObject().put("amount", -100)
            .put("destination_account_id", 0)
            .put("transaction_type", "DEPOSIT");

        final JsonObject nonExistentTransactionTypeRequest = new JsonObject().put("amount", 100)
            .put("destination_account_id", 0)
            .put("transaction_type", "TOPUP");

        return new Object[]{
            new Object[]{nonExistentSourceAccountIdRequest, ACCOUNT_DOESNT_EXIST_MESSAGE},
            new Object[]{nonExistentDestinationAccountIdRequest, ACCOUNT_DOESNT_EXIST_MESSAGE},
            new Object[]{negativeAmountRequest, INVALID_TRANSACTION_AMOUNT_MESSAGE},
            new Object[]{nonExistentTransactionTypeRequest, INVALID_TRANSACTION_TYPE_MESSAGE}
        };
    }

}
