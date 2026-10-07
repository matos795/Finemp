package com.finemp.accounting.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class AccountingMovementQueryServiceTest {

    private final AccountingMovementRepository accountingMovementRepository =
            mock(AccountingMovementRepository.class);

    private final AccountingMovementQueryService service =
            new AccountingMovementQueryService(accountingMovementRepository);

    @Test
    void shouldReturnMovementResponsesForCompanyAndPeriod() {
        UUID companyId = UUID.randomUUID();

        Company company = new Company(
                "Empresa Exemplo Ltda",
                "12345678000190"
        );

        AccountingSource source = new AccountingSource(
                company,
                AccountingSourceType.QUESTOR,
                "company-ref",
                true
        );

        AccountingAccount account = new AccountingAccount(
                source,
                "5396",
                "3.1.01.005.001.009.5396",
                "Receita de Exemplo"
        );

        AccountingMovement movement = new AccountingMovement(
                account,
                "entry-123",
                AccountingMovementSide.CREDIT,
                LocalDate.of(2026, 7, 15),
                new BigDecimal("1250.75"),
                "Histórico de exemplo"
        );

        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);

        when(accountingMovementRepository.findAllByCompanyAndPeriod(
                companyId,
                startDate,
                endDate
        )).thenReturn(List.of(movement));

        List<AccountingMovementResponse> responses =
                service.findByCompanyAndPeriod(
                        companyId,
                        startDate,
                        endDate
                );

        assertThat(responses).hasSize(1);

        AccountingMovementResponse response = responses.getFirst();

        assertThat(response.accountingDate())
                .isEqualTo(LocalDate.of(2026, 7, 15));

        assertThat(response.accountDescription())
                .isEqualTo("Receita de Exemplo");

        assertThat(response.accountKey())
                .isEqualTo("3.1.01.005.001.009.5396");

        assertThat(response.side())
                .isEqualTo(AccountingMovementSide.CREDIT);

        assertThat(response.amount())
                .isEqualByComparingTo("1250.75");

        assertThat(response.history())
                .isEqualTo("Histórico de exemplo");

        assertThat(response.sourceEntryKey())
                .isEqualTo("entry-123");

        verify(accountingMovementRepository)
                .findAllByCompanyAndPeriod(
                        companyId,
                        startDate,
                        endDate
                );
    }

    @Test
    void shouldRejectPeriodWhenStartDateIsAfterEndDate() {
        UUID companyId = UUID.randomUUID();

        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);

        assertThatThrownBy(() ->
                service.findByCompanyAndPeriod(
                        companyId,
                        startDate,
                        endDate
                ))
                .isInstanceOf(InvalidAccountingPeriodException.class);
    }
}