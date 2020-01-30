package com.gjeziorski.vertxtrial.service;

import static com.gjeziorski.vertxtrial.common.ErrorCodesTranslator.INVALID_TRANSACTION_AMOUNT_MESSAGE;
import static com.gjeziorski.vertxtrial.common.ErrorCodesTranslator.INVALID_TRANSACTION_TYPE_MESSAGE;
import static com.gjeziorski.vertxtrial.common.ErrorCodesTranslator.NOT_NULLABLE_ACCOUNT_ID_MESSAGE;

import com.gjeziorski.vertxtrial.domain.Transaction;
import com.gjeziorski.vertxtrial.domain.TransactionType;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.CustomValidator;
import io.vertx.ext.web.api.validation.ValidationException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

public class CreateTransactionValidator implements CustomValidator {

    @Override
    public void validate(final RoutingContext routingContext) throws ValidationException {
        JsonObject jsonObject = new JsonObject(routingContext.getBodyAsString());

        final String transactionType = jsonObject.getString("transaction_type");

        if (transactionType == null || !isTransactionTypeValid(transactionType)) {
            throw new ValidationException(INVALID_TRANSACTION_TYPE_MESSAGE);
        }

        Transaction transaction = jsonObject.mapTo(Transaction.class);
        if (transaction.getAmount().compareTo(new BigDecimal(0)) <= 0) {
            throw new ValidationException(INVALID_TRANSACTION_AMOUNT_MESSAGE);
        }

        if (TransactionType.TRANSFER.equals(transaction.getTransactionType())
            && transaction.getSourceAccountId() == null) {
            throw new ValidationException(NOT_NULLABLE_ACCOUNT_ID_MESSAGE);
        }
    }

    private boolean isTransactionTypeValid(String transactionType) {
        return Arrays.stream(TransactionType.values()).map(TransactionType::toString)
            .collect(Collectors.toSet()).contains(transactionType);
    }

}
