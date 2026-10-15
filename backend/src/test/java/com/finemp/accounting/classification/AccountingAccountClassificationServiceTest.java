package com.finemp.accounting.classification;

import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountNotFoundException;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingAccountClassificationServiceTest {

    @Mock
    private AccountingAccountRepository accountingAccountRepository;

    @Mock
    private AccountingAccountClassificationRepository classificationRepository;

    @Test
    void shouldClassifyExistingAccountingAccount() {

        UUID accountId = UUID.randomUUID();

        Company company = new Company("Empresa Exemplo Ltda", "11111111000111");

        AccountingSource source = new AccountingSource(
                company,
                AccountingSourceType.QUESTOR,
                "company-example",
                true);

        AccountingAccount account = new AccountingAccount(
                source,
                "100",
                "1.1.01.100",
                "Conta exemplo");

        when(accountingAccountRepository.findById(accountId))
                .thenReturn(Optional.of(account));

        AccountingAccountClassificationService service = new AccountingAccountClassificationService(
                accountingAccountRepository,
                classificationRepository);

        service.classifyAccount(
                accountId,
                "EBITDA",
                "RECEITA BRUTA",
                "LOCAÇÃO");

        ArgumentCaptor<AccountingAccountClassification> captor = ArgumentCaptor.forClass(
                AccountingAccountClassification.class);

        verify(classificationRepository)
                .save(captor.capture());

        AccountingAccountClassification saved = captor.getValue();

        assertThat(saved.getAccountingAccount())
                .isSameAs(account);

        assertThat(saved.getDreClassification())
                .isEqualTo("EBITDA");

        assertThat(saved.getDreOpenClassification())
                .isEqualTo("RECEITA BRUTA");

        assertThat(saved.getEbitdaClassification())
                .isEqualTo("LOCAÇÃO");
    }

    @Test
    void shouldRejectClassificationWhenAccountingAccountDoesNotExist() {

        UUID accountId = UUID.randomUUID();

        when(accountingAccountRepository.findById(accountId))
                .thenReturn(Optional.empty());

        AccountingAccountClassificationService service = new AccountingAccountClassificationService(
                accountingAccountRepository,
                classificationRepository);

        assertThatThrownBy(() -> service.classifyAccount(
                accountId,
                "EBITDA",
                "RECEITA BRUTA",
                "LOCAÇÃO"))
                .isInstanceOf(AccountingAccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());

        verify(classificationRepository, never())
                .save(any());
    }
}