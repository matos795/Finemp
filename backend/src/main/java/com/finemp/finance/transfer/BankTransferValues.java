package com.finemp.finance.transfer;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.math.*;
import java.time.LocalDate;
import java.util.UUID;

/** Canonical persisted state, distinct from the positive operational capture contract. */
@Embeddable
public record BankTransferValues(
        @Column(name="origin_bank_account_id",nullable=false,updatable=false) UUID originBankAccountId,
        @Column(name="destination_bank_account_id",nullable=false,updatable=false) UUID destinationBankAccountId,
        @Column(name="transfer_date",nullable=false,updatable=false) LocalDate transferDate,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @Column(nullable=false,precision=19,scale=2,updatable=false) BigDecimal amount,
        @Column(nullable=false,length=255,updatable=false) String description) {
    public BankTransferValues {
        if(originBankAccountId==null || destinationBankAccountId==null || originBankAccountId.equals(destinationBankAccountId)
                || transferDate==null || transferDate.getYear()<1 || transferDate.getYear()>9999 || amount==null || amount.signum()<0)throw invalid();
        try {
            var compact=amount.signum()==0?BigDecimal.ZERO:amount.stripTrailingZeros();
            if(compact.scale()>2 || (long)compact.precision()-compact.scale()>17)throw invalid();
            amount=compact.setScale(2,RoundingMode.UNNECESSARY);
        }catch(ArithmeticException invalid) {throw invalid();}
        if(description==null)description="";if(description.length()>255)throw invalid();
        for(int index=0;index<description.length();index++) {
            char current=description.charAt(index);if(current==0 || Character.isLowSurrogate(current))throw invalid();
            if(Character.isHighSurrogate(current) && (++index>=description.length() || !Character.isLowSurrogate(description.charAt(index))))throw invalid();
        }
        description=description.trim();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Estado canônico de transferência inválido.");}
    @Override public String toString() {return "BankTransferValues[redacted]";}
}
