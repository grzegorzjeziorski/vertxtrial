package com.gjeziorski.vertxtrial.verticles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gjeziorski.vertxtrial.common.ObjectMapperProvider;
import com.gjeziorski.vertxtrial.domain.Account;
import com.gjeziorski.vertxtrial.domain.FetchTransactionsRequest;
import com.gjeziorski.vertxtrial.domain.Transaction;
import com.gjeziorski.vertxtrial.domain.TransactionType;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.eventbus.Message;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class RepositoryVerticleTest {

    private RepositoryVerticle repositoryVerticle;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        repositoryVerticle = new RepositoryVerticle();
        vertx.deployVerticle(repositoryVerticle, vertxTestContext.completing());
    }

    @Test
    void testShouldCreateAccount(VertxTestContext vertxTestContext) {
        Message<Object> message = mock(Message.class);
        Account account = Account.builder().name("John").surname("Doe").build();
        when(message.body()).thenReturn(JsonObject.mapFrom(account));
        repositoryVerticle.createAccount(message).subscribe(
            updateResult -> vertxTestContext.verify(
                () -> {
                    assertThat(updateResult.getKeys().size()).isEqualTo(1);
                    verify(message).reply(any());
                    vertxTestContext.completeNow();
                }));
    }

    @Test
    void testShouldFetchListOfAccounts(VertxTestContext vertxTestContext) {
        Message<Object> message = mock(Message.class);
        repositoryVerticle.listAccounts(message).subscribe(
            result -> vertxTestContext.verify(
                () -> {
                    verify(message).reply(any());
                    vertxTestContext.completeNow();
                }));
    }

    @Test
    void testShouldTransferWhenPreconditionsFulfilled(VertxTestContext vertxTestContext) throws SQLException {
        setUpDBState();

        Message<Object> transferMessage = mock(Message.class);
        Transaction transferTransaction = Transaction.builder().transactionType(TransactionType.TRANSFER)
            .amount(new BigDecimal(50)).sourceAccountId(1L).destinationAccountId(0L).build();
        when(transferMessage.body()).thenReturn(JsonObject.mapFrom(transferTransaction).toString());

        Message<Object> transactionsMessage = mock(Message.class);
        FetchTransactionsRequest fetchTransactionsRequest = FetchTransactionsRequest.builder().accountId(0L).build();
        when(transactionsMessage.body()).thenReturn(JsonObject.mapFrom(fetchTransactionsRequest).toString());

        repositoryVerticle.handleTransfer(transferMessage)
            .flatMap(result -> repositoryVerticle.listTransactions(transactionsMessage))
            .subscribe(result -> vertxTestContext.verify(() -> {
                assertThat(getTransactions(result).size()).isEqualTo(1);
                vertxTestContext.completeNow();
            }));
    }

    @Test
    void testShouldWithdrawWhenBalancePositive(VertxTestContext vertxTestContext) throws SQLException {
        setUpDBState();
        Message<Object> depositMessage = mock(Message.class);
        Message<Object> withdrawMessage = mock(Message.class);
        Message<Object> transactionsMessage = mock(Message.class);
        Transaction depositTransaction = Transaction.builder().transactionType(TransactionType.DEPOSIT)
            .amount(new BigDecimal(50)).destinationAccountId(0L).build();
        Transaction withdrawTransaction = Transaction.builder().transactionType(TransactionType.WITHDRAW)
            .amount(new BigDecimal(70)).destinationAccountId(0L).build();
        FetchTransactionsRequest fetchTransactionsRequest = FetchTransactionsRequest.builder().accountId(0L).build();
        String fetchTransactionsRequestString = JsonObject.mapFrom(fetchTransactionsRequest).toString();
        when(depositMessage.body()).thenReturn(JsonObject.mapFrom(depositTransaction));
        when(withdrawMessage.body()).thenReturn(JsonObject.mapFrom(withdrawTransaction));
        when(transactionsMessage.body()).thenReturn(fetchTransactionsRequestString);

        repositoryVerticle.handleDeposit(depositMessage)
            .flatMap(result -> repositoryVerticle.handleDeposit(depositMessage))
            .flatMap(result -> repositoryVerticle.handleWithdraw(withdrawMessage))
            .flatMap(result -> repositoryVerticle.listTransactions(transactionsMessage))
            .subscribe(result -> vertxTestContext.verify(() -> {
                assertThat(getTransactions(result).size()).isEqualTo(3);
                vertxTestContext.completeNow();
            }));
    }

    @Test
    void testShouldNotWithdrawWhenBalanceNegative(VertxTestContext vertxTestContext) throws SQLException {
        setUpDBState();
        Message<Object> depositMessage = mock(Message.class);
        Message<Object> withdrawMessage = mock(Message.class);
        Message<Object> transactionsMessage = mock(Message.class);
        Transaction depositTransaction = Transaction.builder().transactionType(TransactionType.DEPOSIT)
            .amount(new BigDecimal(50)).destinationAccountId(0L).build();
        Transaction withdrawTransaction = Transaction.builder().transactionType(TransactionType.WITHDRAW)
            .amount(new BigDecimal(70)).destinationAccountId(0L).build();
        FetchTransactionsRequest fetchTransactionsRequest = FetchTransactionsRequest.builder().accountId(0L).build();
        String fetchTransactionsRequestString = JsonObject.mapFrom(fetchTransactionsRequest).toString();
        when(depositMessage.body()).thenReturn(JsonObject.mapFrom(depositTransaction));
        when(withdrawMessage.body()).thenReturn(JsonObject.mapFrom(withdrawTransaction));
        when(transactionsMessage.body()).thenReturn(fetchTransactionsRequestString);

        repositoryVerticle.handleDeposit(depositMessage)
            .flatMap(result -> repositoryVerticle.handleWithdraw(withdrawMessage))
            .flatMap(result -> repositoryVerticle.listTransactions(transactionsMessage))
            .subscribe(result -> vertxTestContext.verify(() -> {
                assertThat(getTransactions(result).size()).isEqualTo(1);
                vertxTestContext.completeNow();
            }));
    }

    void setUpDBState() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:test?shutdown=true")) {
            connection.createStatement()
                .executeUpdate("INSERT INTO ACCOUNT(ID, BALANCE, NAME, SURNAME) VALUES (0, 0, 'John', 'Doe')");
            connection.createStatement()
                .executeUpdate("INSERT INTO ACCOUNT(ID, BALANCE, NAME, SURNAME) VALUES (1, 100, 'Jane', 'Doe')");
        }
    }

    private static List<Transaction> getTransactions(final String result) throws IOException {
        final List<Transaction> transactions = ObjectMapperProvider.getObjectMapper().readValue(result, List.class);
        return transactions;
    }


}
