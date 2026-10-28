package com.finemp.finance.bank;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankAccountResponse(UUID id,UUID companyId,String name,BankAccountType type,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openingBalance,LocalDate openingDate,boolean active,long version){
    static BankAccountResponse from(BankAccount bank){return new BankAccountResponse(bank.getId(),bank.getCompany().getId(),bank.getName(),bank.getType(),bank.getOpeningBalance(),bank.getOpeningDate(),bank.isActive(),bank.getVersion());}
}
