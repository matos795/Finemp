package com.finemp.finance.withholding;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.math.*;

@Embeddable
public record WithholdingValues(
        @Column(name="tax_code",nullable=false,length=40) String taxCode,
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) WithholdingTreatment treatment,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @Column(nullable=false,precision=19,scale=2) BigDecimal amount,
        @Column(name="document_reference",nullable=false,length=255) String documentReference) {
    public WithholdingValues {
        taxCode=text(taxCode,40);if(taxCode.isBlank() || treatment==null || amount==null || amount.signum()<0)throw invalid();
        documentReference=text(documentReference,255);
        try {
            var compact=amount.signum()==0?BigDecimal.ZERO:amount.stripTrailingZeros();
            if(compact.scale()>2 || (long)compact.precision()-compact.scale()>17)throw invalid();
            amount=compact.setScale(2,RoundingMode.UNNECESSARY);
        }catch(ArithmeticException invalid) {throw invalid();}
    }
    private static String text(String value,int max) {
        if(value==null)return "";if(value.length()>max)throw invalid();
        for(int i=0;i<value.length();i++) {char c=value.charAt(i);if(c==0 || Character.isLowSurrogate(c))throw invalid();if(Character.isHighSurrogate(c) && (++i>=value.length() || !Character.isLowSurrogate(value.charAt(i))))throw invalid();}
        return value.strip();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Retenção financeira inválida.");}
    @Override public String toString() {return "WithholdingValues[redacted]";}
}
