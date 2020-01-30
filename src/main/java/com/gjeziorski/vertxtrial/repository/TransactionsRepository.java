package com.gjeziorski.vertxtrial.repository;

import static com.gjeziorski.vertxtrial.common.ErrorCodes.ACCOUNT_DOESNT_EXIST;
import static com.gjeziorski.vertxtrial.common.ErrorCodes.INSUFFICIENT_FUNDS;
import static com.gjeziorski.vertxtrial.common.ErrorCodes.OK;
import static com.gjeziorski.vertxtrial.common.ErrorCodes.TECHNICAL_ERROR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjeziorski.vertxtrial.common.ErrorCodesTranslator;
import com.gjeziorski.vertxtrial.common.ObjectMapperProvider;
import com.gjeziorski.vertxtrial.domain.FetchTransactionsRequest;
import com.gjeziorski.vertxtrial.domain.Transaction;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLClientHelper;
import io.vertx.reactivex.ext.sql.SQLConnection;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionsRepository {

    private static final String FETCH_TRANSACTIONS_BY_SOURCE_ACCOUNT_ID_SQL = "SELECT * FROM TRANSACTION WHERE SOURCE_ACCOUNT_ID = ?";
    private static final String FETCH_TRANSACTIONS_BY_DESTINATION_ACCOUNT_ID_SQL = "SELECT * FROM TRANSACTION WHERE DESTINATION_ACCOUNT_ID = ?";

    private static final String LOCK_ACCOUNT_ID_SQL = "SELECT ID, CAST(BALANCE AS VARCHAR(20)) AS BALANCE FROM ACCOUNT WHERE ID = ? FOR UPDATE";

    private static final String INCREASE_ACCOUNT_BALANCE_SQL = "UPDATE ACCOUNT SET BALANCE = BALANCE + ? WHERE ID = ?";
    private static final String DECREASE_ACCOUNT_BALANCE_SQL = "UPDATE ACCOUNT SET BALANCE = BALANCE - ? WHERE ID = ?";

    private static final String INSERT_TRANSACTION_SQL = "INSERT INTO TRANSACTION(DESTINATION_ACCOUNT_ID, TRANSACTION_TYPE, AMOUNT) VALUES (?, ?, ?)";
    private static final String INSERT_TRANSFER_TRANSACTION_SQL = "INSERT INTO TRANSACTION(SOURCE_ACCOUNT_ID, DESTINATION_ACCOUNT_ID, TRANSACTION_TYPE, AMOUNT) VALUES (?, ?, ?, ?)";

    private JDBCClient jdbcClient;
    private ObjectMapper objectMapper;

    public TransactionsRepository(final JDBCClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = ObjectMapperProvider.getObjectMapper();
    }

    public Single<Integer> handleDeposit(Message<Object> message) {
        return handleTransactionResult(executeDeposit(message), message);
    }

    public Single<Integer> handleWithdraw(Message<Object> message) {
        return handleTransactionResult(executeWithdraw(message), message);
    }

    public Single<Integer> handleTransfer(Message<Object> message) {
        return handleTransactionResult(executeTransfer(message), message);
    }

    // Time period condition should be pushed to the database and supported with index.
    // Since JDBCClient doesn't support condition builders I did in application level.
    // I considered querydsl library for this.
    public Single<String> listTransactions(Message<Object> message) {
        return jdbcClient.rxGetConnection().flatMap(connection -> {
            final FetchTransactionsRequest fetchTransactionsRequest = deserializeFetchTransactionsRequest(message);
            final JsonArray parameters = new JsonArray().add(fetchTransactionsRequest.getAccountId());
            final Single<ResultSet> singleSourceAccountTransactions = connection
                .rxQueryWithParams(FETCH_TRANSACTIONS_BY_SOURCE_ACCOUNT_ID_SQL, parameters);
            final Single<ResultSet> singleDestinationAccountTransactions =
                connection.rxQueryWithParams(FETCH_TRANSACTIONS_BY_DESTINATION_ACCOUNT_ID_SQL, parameters);
            return singleSourceAccountTransactions
                .zipWith(singleDestinationAccountTransactions,
                    (outgoingTransactionsRs, incomingTransactionsRs) -> getTransactions(fetchTransactionsRequest,
                        outgoingTransactionsRs, incomingTransactionsRs))
                .map(transactions -> objectMapper.writeValueAsString(transactions))
                .compose(SQLClientHelper.txSingleTransformer(connection)).doAfterTerminate(connection::close);
        }).doOnSuccess(result -> {
            log.info("Fetched transactions from db");
            message.reply(result);
        }).doOnError(throwable -> {
            log.error("Failed to fetch transactions", throwable);
            message.fail(TECHNICAL_ERROR, "Failed to fetch transactions");
        });
    }

    private List<Transaction> getTransactions(final FetchTransactionsRequest fetchTransactionsRequest,
        final ResultSet outgoingTransactionsRs, final ResultSet incomingTransactionsRs) throws IOException {
        List<Transaction> transactions = mapTransactions(outgoingTransactionsRs);
        List<Transaction> incomingTransactions = mapTransactions(incomingTransactionsRs);
        transactions.addAll(incomingTransactions);

        return transactions.stream().filter(transaction -> isTransactionInRange(fetchTransactionsRequest, transaction))
            .sorted(
                Comparator.comparing(Transaction::getExecutionTime)).collect(Collectors.toList());
    }

    private boolean isTransactionInRange(final FetchTransactionsRequest fetchTransactionsRequest,
        final Transaction transaction) {
        if (fetchTransactionsRequest.getFrom() != null && fetchTransactionsRequest.getFrom()
            .isAfter(transaction.getExecutionTime())) {
            return false;
        }

        if (fetchTransactionsRequest.getTo() != null && fetchTransactionsRequest.getTo()
            .isBefore(transaction.getExecutionTime())) {
            return false;
        }

        return true;
    }

    private List<Transaction> mapTransactions(final ResultSet resultSet) throws IOException {
        List<Transaction> result = new ArrayList<>();
        for (JsonObject jsonObject : resultSet.getRows()) {
            result.add(objectMapper.readValue(jsonObject.toString(), Transaction.class));
        }
        return result;
    }

    private Single<Integer> executeTransfer(Message<Object> message) {
        return jdbcClient.rxGetConnection().flatMap(connection -> {
            final Transaction transaction = deserializeTransaction(message);
            long firstAccountIdToLock = Math
                .min(transaction.getSourceAccountId(), transaction.getDestinationAccountId());
            long secondAccountIdToLock = Math
                .max(transaction.getSourceAccountId(), transaction.getDestinationAccountId());
            return connection
                .rxQueryWithParams(LOCK_ACCOUNT_ID_SQL, new JsonArray().add(firstAccountIdToLock))
                .flatMap(firstAccountRs -> {
                    if (!accountExists(firstAccountRs)) {
                        return Single.just(ACCOUNT_DOESNT_EXIST);
                    } else {
                        return connection
                            .rxQueryWithParams(LOCK_ACCOUNT_ID_SQL, new JsonArray().add(secondAccountIdToLock))
                            .flatMap(secondAccountRs -> {
                                if (!accountExists(secondAccountRs)) {
                                    return Single.just(ACCOUNT_DOESNT_EXIST);
                                } else if (!sufficientFunds(firstAccountRs, secondAccountRs, transaction)) {
                                    return Single.just(INSUFFICIENT_FUNDS);
                                } else {
                                    return updateBalances(connection, transaction);
                                }
                            });
                    }
                })
                .compose(SQLClientHelper.txSingleTransformer(connection))
                .doFinally(connection::close);
        });
    }

    private Single<Integer> executeWithdraw(Message<Object> message) {
        return jdbcClient.rxGetConnection().flatMap(connection -> {
            final Transaction transaction = deserializeTransaction(message);
            return connection
                .rxQueryWithParams(LOCK_ACCOUNT_ID_SQL, new JsonArray().add(transaction.getDestinationAccountId()))
                .flatMap(accountRs -> {
                    if (!accountExists(accountRs)) {
                        return Single.just(ACCOUNT_DOESNT_EXIST);
                    } else if (!sufficientFunds(accountRs, transaction)) {
                        return Single.just(INSUFFICIENT_FUNDS);
                    } else {
                        return connection
                            .rxUpdateWithParams(DECREASE_ACCOUNT_BALANCE_SQL,
                                new JsonArray().add(transaction.getAmount().toString())
                                    .add(transaction.getDestinationAccountId()))
                            .flatMap(result -> connection.rxUpdateWithParams(INSERT_TRANSACTION_SQL,
                                new JsonArray().add(transaction.getDestinationAccountId())
                                    .add(transaction.getTransactionType())
                                    .add(transaction.getAmount().toString())))
                            .flatMap(result -> Single.just(OK));
                    }
                })
                .compose(SQLClientHelper.txSingleTransformer(connection))
                .doFinally(connection::close);
        });
    }

    private boolean accountExists(ResultSet accountRs) {
        return accountRs.getRows().size() == 1;
    }

    private boolean sufficientFunds(ResultSet accountRs, Transaction transaction) {
        JsonObject jsonObject = accountRs.getRows().get(0);
        BigDecimal balance = new BigDecimal(jsonObject.getString("BALANCE"));
        return balance.compareTo(transaction.getAmount()) >= 0;
    }

    private boolean sufficientFunds(ResultSet firstAccountRs, ResultSet secondAccountRs, Transaction transaction) {
        ResultSet sourceAccountRs = getResultSetByAccountId(firstAccountRs, secondAccountRs,
            transaction.getSourceAccountId());

        return sufficientFunds(sourceAccountRs, transaction);
    }

    private ResultSet getResultSetByAccountId(ResultSet firstAccountRs, ResultSet secondAccountRs, long accountId) {
        if (accountId == firstAccountRs.getRows().get(0).getLong("ID")) {
            return firstAccountRs;
        }
        return secondAccountRs;
    }

    private Single<Integer> updateBalances(SQLConnection connection, Transaction transaction) {
        return connection
            .rxUpdateWithParams(DECREASE_ACCOUNT_BALANCE_SQL, new JsonArray().add(transaction.getAmount().toString())
                .add(transaction.getSourceAccountId()))
            .flatMap(decreaseResult -> connection.rxUpdateWithParams(INCREASE_ACCOUNT_BALANCE_SQL,
                new JsonArray().add(transaction.getAmount().toString()).add(transaction.getDestinationAccountId())))
            .flatMap(increaseResult -> connection.rxUpdateWithParams(INSERT_TRANSFER_TRANSACTION_SQL,
                new JsonArray().add(transaction.getSourceAccountId())
                    .add(transaction.getDestinationAccountId())
                    .add(transaction.getTransactionType())
                    .add(transaction.getAmount().toString())))
            .flatMap(insertResult -> Single.just(OK));
    }

    private Single<Integer> executeDeposit(Message<Object> message) {
        final Transaction transaction = deserializeTransaction(message);
        return jdbcClient.rxGetConnection().flatMap(connection ->
            connection
                .rxQueryWithParams(LOCK_ACCOUNT_ID_SQL, new JsonArray().add(transaction.getDestinationAccountId()))
                .flatMap(accountRs -> {
                    if (!accountExists(accountRs)) {
                        return Single.just(ACCOUNT_DOESNT_EXIST);
                    } else {
                        return connection
                            .rxUpdateWithParams(INCREASE_ACCOUNT_BALANCE_SQL,
                                new JsonArray().add(transaction.getAmount().toString())
                                    .add(transaction.getDestinationAccountId()))
                            .flatMap(result -> connection.rxUpdateWithParams(INSERT_TRANSACTION_SQL,
                                new JsonArray().add(transaction.getDestinationAccountId())
                                    .add(transaction.getTransactionType())
                                    .add(transaction.getAmount().toString())))
                            .flatMap(result -> Single.just(OK));
                    }
                })
                .compose(SQLClientHelper.txSingleTransformer(connection))
                .doFinally(connection::close));
    }


    private Single<Integer> handleTransactionResult(Single<Integer> input, Message<Object> message) {
        return input
            .doOnSuccess(errorCode -> {
                if (errorCode == 0) {
                    message.reply("");
                } else {
                    message.fail(errorCode, ErrorCodesTranslator.translateErrorCode(errorCode));
                }
            })
            .doOnError(throwable -> {
                log.error("Technical error", throwable);
                message.fail(TECHNICAL_ERROR, "Technical error");
            });
    }

    private Transaction deserializeTransaction(Message<Object> message) {
        JsonObject jsonObject = new JsonObject(message.body().toString());
        return jsonObject.mapTo(Transaction.class);
    }

    private FetchTransactionsRequest deserializeFetchTransactionsRequest(Message<Object> message) {
        JsonObject jsonObject = new JsonObject(message.body().toString());
        return jsonObject.mapTo(FetchTransactionsRequest.class);
    }

}
