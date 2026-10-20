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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingAccountClassificationServiceTest {

        @Mock
        private AccountingAccountRepository accountingAccountRepository;

        @Mock
        private AccountingAccountClassificationRepository classificationRepository;

        @Mock
        private DreClassificationRepository dreClassificationRepository;

        @Test
        void shouldLinkExistingAccountingAccountToGlobalClassification() {

                UUID accountId = UUID.randomUUID();
                UUID classificationId = UUID.randomUUID();

                UUID companyId = UUID.randomUUID();

                Company company = mock(Company.class);

                when(company.getId()).thenReturn(companyId);

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

                DreClassification globalClassification = new DreClassification(
                                "EBITDA",
                                "RECEITA BRUTA",
                                "LOCAÇÃO");

                when(accountingAccountRepository.findById(accountId))
                                .thenReturn(Optional.of(account));

                when(dreClassificationRepository.findById(classificationId))
                                .thenReturn(Optional.of(globalClassification));

                AccountingAccountClassificationService service = new AccountingAccountClassificationService(
                                accountingAccountRepository,
                                classificationRepository,
                                dreClassificationRepository);

                service.classifyAccount(
                                companyId,
                                accountId,
                                classificationId);

                ArgumentCaptor<AccountingAccountClassification> captor = ArgumentCaptor.forClass(
                                AccountingAccountClassification.class);

                verify(classificationRepository)
                                .save(captor.capture());

                AccountingAccountClassification saved = captor.getValue();

                assertThat(saved.getAccountingAccount())
                                .isSameAs(account);

                assertThat(saved.getClassification())
                                .isSameAs(globalClassification);
        }

        @Test
        void shouldRejectClassificationWhenAccountingAccountDoesNotExist() {

                UUID accountId = UUID.randomUUID();
                UUID classificationId = UUID.randomUUID();
                UUID companyId = UUID.randomUUID();

                when(accountingAccountRepository.findById(accountId))
                                .thenReturn(Optional.empty());

                AccountingAccountClassificationService service = new AccountingAccountClassificationService(
                                accountingAccountRepository,
                                classificationRepository,
                                dreClassificationRepository);

                assertThatThrownBy(() -> service.classifyAccount(
                                companyId,
                                accountId,
                                classificationId))
                                .isInstanceOf(
                                                AccountingAccountNotFoundException.class)
                                .hasMessageContaining(accountId.toString());

                verify(dreClassificationRepository, never())
                                .findById(any());

                verify(classificationRepository, never())
                                .save(any());
        }
}