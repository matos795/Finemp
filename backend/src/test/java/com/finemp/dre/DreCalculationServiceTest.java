package com.finemp.dre;

import com.finemp.accounting.movement.AccountingMovementSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DreCalculationServiceTest {

    @Mock
    private DreMovementRepository dreMovementRepository;

    private DreCalculationService service;

    @BeforeEach
    void setUp() {
        service = new DreCalculationService(
                dreMovementRepository,
                new DreMovementValueCalculator());
    }

    @Test
    void shouldAggregateMovementsWithSameDreClassification() {

        UUID companyId = UUID.randomUUID();

        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);

        DreMovementRow firstMovement = new DreMovementRow(
                UUID.randomUUID(),
                "3.1.01.100",
                AccountingMovementSide.CREDIT,
                new BigDecimal("100.00"),
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        DreMovementRow secondMovement = new DreMovementRow(
                UUID.randomUUID(),
                "3.1.01.200",
                AccountingMovementSide.CREDIT,
                new BigDecimal("250.00"),
                "EBITDA",
                "RECEITA BRUTA",
                "SERVIÇOS");

        when(dreMovementRepository.findAllByCompanyAndPeriod(
                companyId,
                startDate,
                endDate))
                .thenReturn(List.of(
                        firstMovement,
                        secondMovement));

        DreCalculationResult result = service.calculate(
                companyId,
                startDate,
                endDate);

        assertThat(result.lines())
                .containsExactly(
                        new DreLine(
                                "EBITDA",
                                "RECEITA BRUTA",
                                "SERVIÇOS",
                                new BigDecimal("350.00")));

        assertThat(result.unclassifiedAccountCount())
                .isZero();
    }

    @Test
    void shouldRejectInvalidPeriod() {

        UUID companyId = UUID.randomUUID();

        LocalDate startDate = LocalDate.of(2026, 7, 31);
        LocalDate endDate = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> service.calculate(
                companyId,
                startDate,
                endDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");

        verifyNoInteractions(dreMovementRepository);
    }
}