package com.gjeziorski.vertxtrial.verticles;

import com.gjeziorski.vertxtrial.service.AccountsService;
import com.gjeziorski.vertxtrial.service.TransactionsService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpVerticle extends AbstractVerticle {

    private AccountsService accountsService;
    private TransactionsService transactionsService;

    @Override
    public void start(final Promise<Void> startPromise) {
        accountsService = new AccountsService(vertx);
        transactionsService = new TransactionsService(vertx);

        vertx.deployVerticle(
            new RepositoryVerticle(), event -> {
                createRouter(startPromise);
            });
    }

    private void createRouter(final Promise<Void> startPromise) {
        final Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.route("/api/accounts").method(HttpMethod.POST).handler(accountsService.getAccountCreationRequestValidationHandler())
            .handler(accountsService::handleNewAccount).failureHandler(this::handleValidationFailure);
        router.route("/api/accounts").method(HttpMethod.GET).handler(accountsService::handleGetAccounts);

        router.route("/api/transactions").method(HttpMethod.GET)
            .handler(transactionsService.getListTransactionsRequestValidationHandler())
            .handler(transactionsService::handleGetTransactionsList);
        router.route("/api/transactions").method(HttpMethod.POST)
            .handler(transactionsService.getCreateTransactionRequestValidationHandler())
            .handler(transactionsService::handleCreateTransaction)
            .failureHandler(this::handleValidationFailure);

        vertx.createHttpServer().requestHandler(router).listen(8080, result -> {
            if (result.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(result.cause());
            }
        });
    }

    private void handleValidationFailure(RoutingContext routingContext) {
        Throwable failure = routingContext.failure();
        if (failure instanceof ValidationException) {
            routingContext.response().setStatusCode(400).end(failure.getMessage());
        }
    }

}
