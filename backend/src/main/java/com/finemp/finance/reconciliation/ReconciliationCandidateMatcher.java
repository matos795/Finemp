package com.finemp.finance.reconciliation;

import com.finemp.finance.category.FinancialDirection;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure selection of candidates; callers must supply an authorized, Company-scoped snapshot. */
public class ReconciliationCandidateMatcher {
    private static final BigDecimal TOLERANCE=new BigDecimal("0.011");
    public List<Match> find(Statement statement,List<Entry> entries,Set<UUID> alreadyLinked) {
        if(statement==null || entries==null || alreadyLinked==null || alreadyLinked.stream().anyMatch(Objects::isNull))throw invalid();
        var expected=statement.signedAmount().signum()>0?FinancialDirection.INCOME:FinancialDirection.EXPENSE;
        var ids=new HashSet<UUID>();var matches=new ArrayList<Match>();
        for(var entry:entries) {
            if(entry==null || !ids.add(entry.id()))throw invalid();
            if(alreadyLinked.contains(entry.id()) || entry.direction()!=expected)continue;
            boolean paid=entry.paymentDate()!=null;
            if(paid && !entry.bankAccountId().equals(statement.bankAccountId()))continue;
            var value=paid && entry.actualPaidAmount()!=null?entry.actualPaidAmount():entry.nominalAmount();
            if(value.subtract(statement.signedAmount().abs()).abs().compareTo(TOLERANCE)>=0)continue;
            long distance=Math.abs(ChronoUnit.DAYS.between(statement.date(),paid?entry.paymentDate():entry.dueDate()));
            if(distance<=(paid?3:15))matches.add(new Match(entry.id(),paid,distance));
        }
        matches.sort(Comparator.comparing(Match::paid).reversed().thenComparingLong(Match::distanceDays));
        return List.copyOf(matches);
    }
    public record Statement(UUID bankAccountId,LocalDate date,BigDecimal signedAmount) {
        public Statement {if(bankAccountId==null)throw invalid();date=validDate(date);signedAmount=money(signedAmount,true);if(signedAmount.signum()==0)throw invalid();}
    }
    public record Entry(UUID id,FinancialDirection direction,UUID bankAccountId,LocalDate dueDate,LocalDate paymentDate,BigDecimal nominalAmount,BigDecimal actualPaidAmount) {
        public Entry {
            if(id==null || direction==null || bankAccountId==null || paymentDate==null && actualPaidAmount!=null)throw invalid();
            dueDate=validDate(dueDate);if(paymentDate!=null)paymentDate=validDate(paymentDate);nominalAmount=money(nominalAmount,false);if(actualPaidAmount!=null)actualPaidAmount=money(actualPaidAmount,false);
        }
    }
    public record Match(UUID entryId,boolean paid,long distanceDays) { }
    private static LocalDate validDate(LocalDate value) {if(value==null || value.getYear()<1 || value.getYear()>9999)throw invalid();return value;}
    private static BigDecimal money(BigDecimal value,boolean signed) {
        if(value==null || !signed && value.signum()<0)throw invalid();
        try {var compact=value.signum()==0?BigDecimal.ZERO:value.stripTrailingZeros();if(compact.scale()>2 || (long)compact.precision()-compact.scale()>17)throw invalid();return compact.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException invalid) {throw invalid();}
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Dados de candidato de conciliação inválidos.");}
}
