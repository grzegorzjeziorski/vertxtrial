package com.gjeziorski.vertxtrial.service;

import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_DEPOSIT;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_LIST;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_TRANSFER;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_WITHDRAW;

import com.gjeziorski.vertxtrial.common.ErrorCodesTranslator;
import com.gjeziorski.vertxtrial.domain.FetchTransactionsRequest;
import com.gjeziorski.vertxtrial.domain.Transaction;
import com.gjeziorski.vertxtrial.domain.TransactionType;
import com.google.common.collect.ImmutableMap;
import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterType;
import java.time.Instant;
import java.util.Map;

public class TransactionsService {

    private static final String TRANSACTION_JSON_SCHEMA = "{\"type\": \"object\", \"properties\": {\"source_account_id\": {\"type\": \"number\"}, \"destination_account_id\": {\"type\": \"number\"}, \"amount\": {\"type\": \"number\"}, \"transaction_type\": {\"type\": \"string\"}}, \"required\": [\"destination_account_id\", \"amount\", \"transaction_type\"]}";
    private static final Map<TransactionType, String> TRANSACTION_TYPE_TO_ADDRESS_MAP = ImmutableMap
        .of(TransactionType.DEPOSIT, DATABASE_TRANSACTION_DEPOSIT, TransactionType.WITHDRAW,
            DATABASE_TRANSACTION_WITHDRAW, TransactionType.TRANSFER, DATABASE_TRANSACTION_TRANSFER);

    private Vertx vertx;
    private HTTPRequestValidationHandler createTransactionRequestValidationHandler;
    private HTTPRequestValidationHandler listTransactionsRequestValidationHandler;

    public TransactionsService(final Vertx vertx) {
        this.vertx = vertx;
        this.createTransactionRequestValidationHandler = prepareCreateTransactionRequestValidationHandler();
        this.listTransactionsRequestValidationHandler = prepareListTransactionsRequestValidationHandler();
    }

    public void handleGetTransactionsList(RoutingContext routingContext) {
        vertx.eventBus()
            .request(DATABASE_TRANSACTION_LIST,
                serializeFetchTransactionsRequest(getTransactionRequestFromRoutingContext(routingContext)),
                reply -> handleGetListResponseMessage(reply, routingContext));
    }

    public void handleCreateTransaction(RoutingContext routingContext) {
        JsonObject jsonObject = new JsonObject(routingContext.getBodyAsString());
        Transaction transaction = jsonObject.mapTo(Transaction.class);
        handleTransaction(routingContext, TRANSACTION_TYPE_TO_ADDRESS_MAP.get(transaction.getTransactionType()));
    }

    public HTTPRequestValidationHandler getCreateTransactionRequestValidationHandler() {
        return createTransactionRequestValidationHandler;
    }

    public HTTPRequestValidationHandler getListTransactionsRequestValidationHandler() {
        return listTransactionsRequestValidationHandler;
    }

    private void handleTransaction(RoutingContext routingContext, String address) {
        vertx.eventBus()
            .request(address, routingContext.getBodyAsString(),
                reply -> handleCreateTransactionResponseMessage(reply, routingContext));
    }

    private FetchTransactionsRequest getTransactionRequestFromRoutingContext(RoutingContext routingContext) {
        FetchTransactionsRequest.FetchTransactionsRequestBuilder builder = FetchTransactionsRequest.builder();
        MultiMap params = routingContext.request().params();
        if (routingContext.request().params().contains("account-id")) {
            builder.accountId(Long.parseLong(params.get("account-id")));
        }
        if (routingContext.request().params().contains("from")) {
            builder.from(Instant.parse(params.get("from")));
        }
        if (routingContext.request().params().contains("to")) {
            builder.from(Instant.parse(params.get("to")));
        }
        return builder.build();
    }

    private String serializeFetchTransactionsRequest(FetchTransactionsRequest fetchTransactionsRequest) {
        return JsonObject.mapFrom(fetchTransactionsRequest).toString();
    }

    private HTTPRequestValidationHandler prepareCreateTransactionRequestValidationHandler() {
        return HTTPRequestValidationHandler.create().addJsonBodySchema(TRANSACTION_JSON_SCHEMA)
            .addCustomValidatorFunction(new CreateTransactionValidator());
    }

    private HTTPRequestValidationHandler prepareListTransactionsRequestValidationHandler() {
        return HTTPRequestValidationHandler.create().addQueryParam("account-id", ParameterType.INT, true)
            .addQueryParam("from", ParameterType.DATETIME, false).addQueryParam("to", ParameterType.DATETIME, false);
    }

    private void handleCreateTransactionResponseMessage(AsyncResult<Message<Object>> reply, RoutingContext routingContext) {
        if (reply.failed()) {
            ReplyException cause = (ReplyException) reply.cause();
            ErrorCodesTranslator.translateErrorCode(cause.failureCode(), routingContext);
        } else {
            routingContext.response().putHeader("content-type", "application/json").setStatusCode(201)
                .end();
        }
    }

    private void handleGetListResponseMessage(AsyncResult<Message<Object>> reply, RoutingContext routingContext) {
        if (reply.failed()) {
            ReplyException cause = (ReplyException) reply.cause();
            ErrorCodesTranslator.translateErrorCode(cause.failureCode(), routingContext);
        } else {
            routingContext.response().putHeader("content-type", "application/json").setStatusCode(200)
                .end(reply.result().body().toString());
        }
    }

}
