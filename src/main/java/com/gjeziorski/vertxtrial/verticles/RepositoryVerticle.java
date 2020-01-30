package com.gjeziorski.vertxtrial.verticles;

import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_ACCOUNT_CREATE;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_ACCOUNT_LIST;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_DEPOSIT;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_LIST;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_TRANSFER;
import static com.gjeziorski.vertxtrial.common.EventBusAddresses.DATABASE_TRANSACTION_WITHDRAW;

import com.gjeziorski.vertxtrial.repository.AccountsRepository;
import com.gjeziorski.vertxtrial.repository.TransactionsRepository;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepositoryVerticle extends AbstractVerticle {

    private static final String DROP_TRANSACTION_TABLE_SQL = "DROP TABLE TRANSACTION IF EXISTS";
    private static final String DROP_ACCOUNT_TABLE_SQL = "DROP TABLE ACCOUNT IF EXISTS";
    private static final String CREATE_ACCOUNT_TABLE_SQL = "CREATE TABLE IF NOT EXISTS ACCOUNT(ID INT IDENTITY PRIMARY KEY NOT NULL, NAME VARCHAR(20) NOT NULL, SURNAME VARCHAR(20) NOT NULL, BALANCE DECIMAL(20,2) DEFAULT 0 NOT NULL)";
    private static final String CREATE_TRANSACTION_TABLE_SQL = "CREATE TABLE IF NOT EXISTS TRANSACTION(ID INT IDENTITY PRIMARY KEY NOT NULL, SOURCE_ACCOUNT_ID INT, DESTINATION_ACCOUNT_ID INT NOT NULL, TRANSACTION_TYPE VARCHAR(20) NOT NULL, AMOUNT DECIMAL(20,2) NOT NULL, EXECUTION_TIME TIMESTAMP DEFAULT NOW() NOT NULL, FOREIGN KEY (SOURCE_ACCOUNT_ID) REFERENCES ACCOUNT(ID), FOREIGN KEY (DESTINATION_ACCOUNT_ID) REFERENCES ACCOUNT(ID))";
    private static final String CREATE_SOURCE_ACCOUNT_ID_INDEX_SQL = "CREATE INDEX IF NOT EXISTS SOURCE_ACCOUNT_ID_INDEX ON TRANSACTION(SOURCE_ACCOUNT_ID)";
    private static final String CREATE_DESTINATION_ACCOUNT_ID_INDEX_SQL = "CREATE INDEX IF NOT EXISTS DESTINATION_ACCOUNT_ID_INDEX ON TRANSACTION(DESTINATION_ACCOUNT_ID)";

    private JDBCClient jdbcClient;
    private AccountsRepository accountsRepository;
    private TransactionsRepository transactionsRepository;

    @Override
    public void start(final Promise<Void> startPromise) {
        JsonObject config = new JsonObject()
            .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
            .put("driver_class", "org.hsqldb.jdbcDriver")
            .put("max_pool_size", 30);

        jdbcClient = JDBCClient.createShared(vertx, config);
        accountsRepository = new AccountsRepository(jdbcClient);
        transactionsRepository = new TransactionsRepository(jdbcClient);
        initDatabase(startPromise);

        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(DATABASE_ACCOUNT_CREATE).toFlowable()
            .subscribe(message -> createAccount(message).subscribe());
        eventBus.consumer(DATABASE_ACCOUNT_LIST).toFlowable()
            .subscribe(message -> listAccounts(message).subscribe());
        eventBus.consumer(DATABASE_TRANSACTION_WITHDRAW).toFlowable()
            .subscribe(message -> handleWithdraw(message).subscribe());
        eventBus.consumer(DATABASE_TRANSACTION_DEPOSIT).toFlowable()
            .subscribe(message -> handleDeposit(message).subscribe());
        eventBus.consumer(DATABASE_TRANSACTION_TRANSFER).toFlowable()
            .subscribe(message -> handleTransfer(message).subscribe());
        eventBus.consumer(DATABASE_TRANSACTION_LIST).toFlowable()
            .subscribe(message -> listTransactions(message).subscribe());
    }

    private void initDatabase(final Promise<Void> startPromise) {
        jdbcClient.rxGetConnection().flatMap(connection -> {
            final Completable completable = connection
                .rxExecute(DROP_TRANSACTION_TABLE_SQL)
                .andThen(connection.rxExecute(DROP_ACCOUNT_TABLE_SQL))
                .andThen(connection.rxExecute(CREATE_ACCOUNT_TABLE_SQL))
                .andThen(connection.rxExecute(CREATE_TRANSACTION_TABLE_SQL))
                .andThen(connection.rxExecute(CREATE_SOURCE_ACCOUNT_ID_INDEX_SQL))
                .andThen(connection.rxExecute(CREATE_DESTINATION_ACCOUNT_ID_INDEX_SQL));
            return completable.doFinally(connection::close).toSingleDefault(1);
        }).subscribe(result -> {
            log.info("Database init succeeded");
            startPromise.complete();
        }, throwable -> {
            log.error("Database init failed", throwable);
            startPromise.fail(throwable);
        });
    }

    Single<UpdateResult> createAccount(final Message<Object> message) {
        return accountsRepository.createAccount(message);
    }

    Single<String> listAccounts(final Message<Object> message) {
        return accountsRepository.listAccounts(message);
    }

    Single<Integer> handleWithdraw(final Message<Object> message) {
        return transactionsRepository.handleWithdraw(message);
    }

    Single<Integer> handleDeposit(final Message<Object> message) {
        return transactionsRepository.handleDeposit(message);
    }

    Single<Integer> handleTransfer(final Message<Object> message) {
        return transactionsRepository.handleTransfer(message);
    }

    Single<String> listTransactions(final Message<Object> message) {
        return transactionsRepository.listTransactions(message);
    }

}
