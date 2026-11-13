package com.finemp.sales;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpportunityTest {

    @Test
    void validatesProbabilityAndExactMonthlyAmount() {
        assertThatThrownBy(() -> new OpportunityValues(
                "Renovação comercial", "Proposta", new BigDecimal("1000.00"),
                101, "Alex", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new OpportunityValues(
                "Renovação comercial", "Proposta", new BigDecimal("-0.01"),
                50, "Alex", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movingStageIsAppendOnlyAndOutcomesDoNotEraseStageHistory() {
        var opportunity = Opportunity.forTest(new OpportunityValues(
                "Projeto ERP", "Diagnóstico", new BigDecimal("1500.00"),
                40, "Alex", null, null));

        opportunity.moveStage("Proposta", Instant.parse("2026-11-10T12:00:00Z"));
        opportunity.win(Instant.parse("2026-11-11T12:00:00Z"));
        opportunity.reopen(Instant.parse("2026-11-12T12:00:00Z"));

        assertThat(opportunity.getStatus()).isEqualTo(OpportunityStatus.OPEN);
        assertThat(opportunity.getStageHistory())
                .extracting(OpportunityStageHistory::getStage)
                .containsExactly("Diagnóstico", "Proposta");
        assertThat(opportunity.getWonAt()).isNull();
        assertThat(opportunity.getLostAt()).isNull();
    }
}
