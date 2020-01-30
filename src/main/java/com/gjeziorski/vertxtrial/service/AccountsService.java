package com.gjeziorski.vertxtrial.service;

import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_ACCOUNT_CREATE;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_ACCOUNT_LIST;

import com.gjeziorski.vertxtrial.common.ErrorCodesTranslator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;

public class AccountsService {

    private static final String ACCOUNT_JSON_SCHEMA = "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}, \"surname\": {\"type\": \"string\"}}, \"required\": [\"name\", \"surname\"]}";

    private Vertx vertx;
    private HTTPRequestValidationHandler accountCreationRequestValidationHandler;

    public AccountsService(final Vertx vertx) {
        this.vertx = vertx;
        accountCreationRequestValidationHandler = HTTPRequestValidationHandler.create()
            .addJsonBodySchema(ACCOUNT_JSON_SCHEMA);
    }

    public void handleNewAccount(RoutingContext routingContext) {
        vertx.eventBus()
            .request(DATABASE_ACCOUNT_CREATE, routingContext.getBodyAsString(),
                reply -> handleResponseMessage(reply, routingContext, 201));
    }

    public void handleGetAccounts(RoutingContext routingContext) {
        vertx.eventBus().request(DATABASE_ACCOUNT_LIST, "", reply -> handleResponseMessage(reply, routingContext, 200));
    }

    public HTTPRequestValidationHandler getAccountCreationRequestValidationHandler() {
        return accountCreationRequestValidationHandler;
    }

    private void handleResponseMessage(AsyncResult<Message<Object>> reply, RoutingContext routingContext,
        int statusCode) {
        if (reply.failed()) {
            ReplyException cause = (ReplyException) reply.cause();
            ErrorCodesTranslator.translateErrorCode(cause.failureCode(), routingContext);
        } else {
            routingContext.response().putHeader("content-type", "application/json").setStatusCode(statusCode)
                .end(reply.result().body().toString());
        }
    }

}
