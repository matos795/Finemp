package com.finemp.finance.reconciliation;

import com.finemp.finance.category.FinancialDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReconciliationCandidateMatcherTest {
    final ReconciliationCandidateMatcher matcher=new ReconciliationCandidateMatcher();final UUID bank=UUID.randomUUID(),other=UUID.randomUUID();final LocalDate date=LocalDate.of(2026,7,15);
    @Test void paidUsesEffectiveValueSameBankAndThreeDayWindowWhileOpenUsesNominalAndFifteenDays() {
        var line=line("100.00");var paid=entry(FinancialDirection.INCOME,bank,date.plusDays(3),date.plusDays(3),"200.00","100.01");
        var outsidePaid=entry(FinancialDirection.INCOME,bank,date,date.plusDays(4),"100.00",null);var wrongBank=entry(FinancialDirection.INCOME,other,date,date,"100.00",null);
        var open=entry(FinancialDirection.INCOME,other,date.minusDays(15),null,"100.00",null);var outsideOpen=entry(FinancialDirection.INCOME,bank,date.plusDays(16),null,"100.00",null);
        assertThat(matcher.find(line,List.of(outsideOpen,open,wrongBank,outsidePaid,paid),Set.of())).extracting(ReconciliationCandidateMatcher.Match::entryId).containsExactly(paid.id(),open.id());
    }
    @Test void signValueToleranceAndAlreadyLinkedItemsMustBeRespected() {
        var valid=entry(FinancialDirection.EXPENSE,bank,date,null,"5.01",null);var tooDifferent=entry(FinancialDirection.EXPENSE,bank,date,null,"5.02",null);var otherSign=entry(FinancialDirection.INCOME,bank,date,null,"5.00",null);var used=entry(FinancialDirection.EXPENSE,bank,date,null,"5.00",null);
        assertThat(matcher.find(line("-5.00"),List.of(used,otherSign,tooDifferent,valid),Set.of(used.id()))).extracting(ReconciliationCandidateMatcher.Match::entryId).containsExactly(valid.id());
    }
    @Test void paidAlwaysPrecedesOpenThenDistanceAndStableInputOrderResolveTies() {
        var openNear=entry(FinancialDirection.INCOME,bank,date,null,"10",null);var paidFar=entry(FinancialDirection.INCOME,bank,date,date.plusDays(3),"10",null);var paidA=entry(FinancialDirection.INCOME,bank,date,date.minusDays(1),"10",null);var paidB=entry(FinancialDirection.INCOME,bank,date,date.plusDays(1),"10",null);
        assertThat(matcher.find(line("10"),List.of(openNear,paidB,paidFar,paidA),Set.of())).extracting(ReconciliationCandidateMatcher.Match::entryId).containsExactly(paidB.id(),paidA.id(),paidFar.id(),openNear.id());
    }
    @Test void invalidInputsMustNeverBeRoundedOrProduceAmbiguousDuplicateCandidates() {
        assertThatThrownBy(()->line("0")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->line("1.005")).isInstanceOf(IllegalArgumentException.class);
        var candidate=entry(FinancialDirection.INCOME,bank,date,null,"1",null);assertThatThrownBy(()->matcher.find(line("1"),List.of(candidate,candidate),Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->entry(FinancialDirection.INCOME,bank,date,null,"1","1")).isInstanceOf(IllegalArgumentException.class);
    }
    ReconciliationCandidateMatcher.Statement line(String amount) {return new ReconciliationCandidateMatcher.Statement(bank,date,new BigDecimal(amount));}
    ReconciliationCandidateMatcher.Entry entry(FinancialDirection direction,UUID account,LocalDate due,LocalDate payment,String nominal,String actual) {return new ReconciliationCandidateMatcher.Entry(UUID.randomUUID(),direction,account,due,payment,new BigDecimal(nominal),actual==null?null:new BigDecimal(actual));}
}
