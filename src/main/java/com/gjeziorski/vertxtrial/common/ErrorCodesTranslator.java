package com.gjeziorski.vertxtrial.common;

import static com.gjeziorski.vertxtrial.common.ErrorCodes.ACCOUNT_DOESNT_EXIST;
import static com.gjeziorski.vertxtrial.common.ErrorCodes.INSUFFICIENT_FUNDS;
import static com.gjeziorski.vertxtrial.common.ErrorCodes.TECHNICAL_ERROR;

import com.google.common.collect.ImmutableMap;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;

public class ErrorCodesTranslator {

    public static final String INVALID_TRANSACTION_AMOUNT_MESSAGE = "Transaction amount should be grater than 0";
    public static final String INVALID_TRANSACTION_TYPE_MESSAGE = "Unsupported transaction type";
    public static final String NOT_NULLABLE_ACCOUNT_ID_MESSAGE = "Source account id cannot be null";
    public static final String INSUFFICIENT_FUNDS_MESSAGE = "Insufficient funds on the account to charge";
    public static final String ACCOUNT_DOESNT_EXIST_MESSAGE = "Requested account doesn't exist";
    public static final String TECHNICAL_ERROR_MESSAGE = "Technical error";

    private static final Map<Integer, Integer> ERROR_CODES_TO_HTTP_CODES = ImmutableMap.of(INSUFFICIENT_FUNDS,
        HttpResponseStatus.BAD_REQUEST.code(), ACCOUNT_DOESNT_EXIST, HttpResponseStatus.BAD_REQUEST.code(),
        TECHNICAL_ERROR, HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

    private static final Map<Integer, String> ERROR_CODES_TO_MESSAGES = ImmutableMap
        .of(INSUFFICIENT_FUNDS, INSUFFICIENT_FUNDS_MESSAGE, ACCOUNT_DOESNT_EXIST,
            ACCOUNT_DOESNT_EXIST_MESSAGE, TECHNICAL_ERROR, TECHNICAL_ERROR_MESSAGE);

    public static void translateErrorCode(final int errorCode, final RoutingContext routingContext) {
        int statusCode = ERROR_CODES_TO_HTTP_CODES
            .getOrDefault(errorCode, HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
        String message = ERROR_CODES_TO_MESSAGES.getOrDefault(errorCode, "Unknown error");
        routingContext.response().putHeader("content-type", "application/text").setStatusCode(statusCode)
            .end(message);
    }

    public static String translateErrorCode(final int errorCode) {
        return ERROR_CODES_TO_MESSAGES
            .getOrDefault(errorCode, TECHNICAL_ERROR_MESSAGE);
    }

}
