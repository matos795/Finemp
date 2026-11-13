package com.finemp.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractExceptionHandlerTest {

    private final ContractExceptionHandler handler = new ContractExceptionHandler();

    @Test
    void referencedPartnerChangeHasStableProblemCode() {
        var problem = handler.conflict(new ContractConflictException(
                ContractConflictException.Reason.REFERENCED_PARTNER_CHANGE));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties())
                .containsEntry("code", "CONTRACT_REFERENCED_PARTNER_CHANGE");
    }

    @Test
    void staleVersionKeepsDistinctProblemCode() {
        var problem = handler.conflict(new ContractConflictException());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties())
                .containsEntry("code", "CONTRACT_STALE_VERSION");
    }
}
