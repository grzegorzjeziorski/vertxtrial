package com.gjeziorski.vertxtrial.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("source_account_id")
    private Long sourceAccountId;

    @JsonProperty("destination_account_id")
    private Long destinationAccountId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("transaction_type")
    private TransactionType transactionType;

    @JsonProperty("execution_time")
    private Instant executionTime;

}