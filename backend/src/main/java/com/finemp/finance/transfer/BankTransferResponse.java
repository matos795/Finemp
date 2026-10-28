package com.finemp.finance.transfer;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Current scalar projection; no actor identity or implicit balance calculation. */
public record BankTransferResponse(UUID id,UUID companyId,UUID originBankAccountId,String originBankAccountName,
        UUID destinationBankAccountId,String destinationBankAccountName,LocalDate transferDate,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount,String description,long version) {
    @Override public String toString() {return "BankTransferResponse[id="+id+"]";}
}
