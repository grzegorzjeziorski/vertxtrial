package com.gjeziorski.vertxtrial.repository;

import static com.gjeziorski.vertxtrial.common.ErrorCodes.TECHNICAL_ERROR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjeziorski.vertxtrial.common.ObjectMapperProvider;
import com.gjeziorski.vertxtrial.domain.Account;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountsRepository {

    private static final String INSERT_NEW_ACCOUNT_SQL = "INSERT INTO ACCOUNT(BALANCE, NAME, SURNAME) VALUES (0, ?, ?)";

    private JDBCClient jdbcClient;
    private ObjectMapper objectMapper;

    public AccountsRepository(final JDBCClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = ObjectMapperProvider.getObjectMapper();
    }

    public Single<UpdateResult> createAccount(Message<Object> message) {
        log.info("inside create account");
        final Account account = deserializeAccount(message);
        return jdbcClient.rxGetConnection().flatMap(connection -> {
            final Single<UpdateResult> updateResult = connection.rxUpdateWithParams(INSERT_NEW_ACCOUNT_SQL,
                new JsonArray().add(account.getName()).add(account.getSurname()));
            return updateResult.doAfterTerminate(connection::close);
        }).doOnSuccess(updateResult -> {
            log.info("Account created: " + updateResult.getKeys().toString());
            message.reply(updateResult.getKeys().getLong(0));
        }).doOnError(throwable -> {
            log.error("Failed to create account", throwable);
            message.fail(TECHNICAL_ERROR, "Failed to create account");
        });
    }

    // Serving JSON directly from database feels wrong
    // Optimally I would put transformation / serialization logic in service layer
    // I decided to put it here because serialization is required in order to use eventBus
    public Single<String> listAccounts(Message<Object> message) {
        return jdbcClient.rxGetConnection().flatMap(connection -> {
            final Single<String> result = connection.rxQuery("SELECT * FROM Account").map(this::mapAccounts)
                .map(accounts -> objectMapper.writeValueAsString(
                    accounts));
            return result.doAfterTerminate(connection::close);
        }).doOnSuccess(result -> {
            log.info("Fetched list of accounts from db");
            message.reply(result);
        }).doOnError(throwable -> {
            log.error("Failed to fetch accounts", throwable);
            message.fail(TECHNICAL_ERROR, "Failed to fetch accounts");
        });
    }

    private List<Account> mapAccounts(final ResultSet resultSet) throws IOException {
        List<Account> result = new ArrayList<>();
        for (JsonObject jsonObject : resultSet.getRows()) {
            result.add(objectMapper.readValue(jsonObject.toString(), Account.class));
        }
        return result;
    }

    private Account deserializeAccount(Message<Object> message) {
        JsonObject jsonObject = new JsonObject(message.body().toString());
        return jsonObject.mapTo(Account.class);
    }

}
