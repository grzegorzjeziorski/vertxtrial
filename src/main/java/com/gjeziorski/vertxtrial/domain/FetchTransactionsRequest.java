package com.gjeziorski.vertxtrial.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchTransactionsRequest {

    private Long accountId;

    private Instant from;

    private Instant to;

}
