package com.finemp.finance.entry;

import com.finemp.finance.category.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Canonical persisted state; capture/rounding and authorization belong to command services. */
@Embeddable
public record FinancialEntryValues(
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) FinancialDirection direction,
        @Column(nullable=false,length=255) String description,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @Column(name="nominal_amount",nullable=false,precision=19,scale=2) BigDecimal nominalAmount,
        @Column(name="document_date",nullable=false) LocalDate documentDate,
        @Column(name="entry_date",nullable=false) LocalDate entryDate,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @Convert(converter=EntryCompetenceConverter.class) @Column(nullable=false,length=7) YearMonth competence,
        @Column(name="due_date",nullable=false) LocalDate dueDate,
        @Column(name="payment_date") LocalDate paymentDate,
        @JsonFormat(shape=JsonFormat.Shape.STRING) @Column(name="actual_paid_amount",precision=19,scale=2) BigDecimal actualPaidAmount,
        @Column(name="bank_account_id",nullable=false) UUID bankAccountId,
        @Column(name="category_id",nullable=false) UUID categoryId,
        @Column(name="partner_id") UUID partnerId,
        @Column(name="result_center_id") UUID resultCenterId,
        @Enumerated(EnumType.STRING) @Column(name="cost_behavior_override",length=20) CostBehavior costBehaviorOverride,
        @Column(name="payment_method",nullable=false,length=80) String paymentMethod,
        @Column(name="document_number",nullable=false,length=255) String documentNumber,
        @Column(nullable=false,length=4000) String notes) {
    public FinancialEntryValues {
        if(direction==null || bankAccountId==null || categoryId==null)throw invalid();
        description=text(description,255);if(description.length()<2)throw invalid();
        date(documentDate);date(entryDate);date(dueDate);
        if(competence==null)throw invalid();year(competence.getYear());
        nominalAmount=money(nominalAmount);
        if(paymentDate!=null)date(paymentDate);
        if(actualPaidAmount!=null) {
            if(paymentDate==null)throw invalid();
            actualPaidAmount=money(actualPaidAmount);
        }
        paymentMethod=text(paymentMethod,80);documentNumber=text(documentNumber,255);notes=text(notes,4000);
    }
    public BigDecimal effectiveAmount() {return paymentDate!=null && actualPaidAmount!=null?actualPaidAmount:nominalAmount;}
    private static BigDecimal money(BigDecimal value) {
        if(value==null || value.signum()<0)throw invalid();
        try {
            var compact=value.signum()==0?BigDecimal.ZERO:value.stripTrailingZeros();
            if(compact.scale()>2 || (long)compact.precision()-compact.scale()>17)throw invalid();
            return compact.setScale(2,RoundingMode.UNNECESSARY);
        }catch(ArithmeticException invalid) {throw invalid();}
    }
    private static String text(String value,int limit) {if(value==null)return "";if(value.length()>limit)throw invalid();return value.trim();}
    private static void date(LocalDate value) {if(value==null)throw invalid();year(value.getYear());}
    private static void year(int value) {if(value<1 || value>9999)throw invalid();}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Estado do lançamento financeiro inválido.");}
    @Override public String toString() {return "FinancialEntryValues[redacted]";}
}
