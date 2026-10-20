package com.finemp.accounting.classification;

import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountNotFoundException;
import com.finemp.accounting.account.AccountingAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountingAccountClassificationService {

    private final AccountingAccountRepository accountingAccountRepository;
    private final AccountingAccountClassificationRepository classificationRepository;
    private final DreClassificationRepository dreClassificationRepository;

    public AccountingAccountClassificationService(
            AccountingAccountRepository accountingAccountRepository,
            AccountingAccountClassificationRepository classificationRepository,
            DreClassificationRepository dreClassificationRepository) {

        this.accountingAccountRepository = accountingAccountRepository;
        this.classificationRepository = classificationRepository;
        this.dreClassificationRepository = dreClassificationRepository;
    }

    @Transactional
    public void classifyAccount(
            UUID companyId,
            UUID accountingAccountId,
            UUID dreClassificationId) {

        AccountingAccount accountingAccount = accountingAccountRepository
                .findById(accountingAccountId)
                .orElseThrow(() -> new AccountingAccountNotFoundException(
                        accountingAccountId));

        UUID accountCompanyId = accountingAccount
                .getAccountingSource()
                .getCompany()
                .getId();

        if (!companyId.equals(accountCompanyId)) {
            throw new AccountingAccountDoesNotBelongToCompanyException(
                    accountingAccountId,
                    companyId);
        }

        DreClassification dreClassification = dreClassificationRepository
                .findById(dreClassificationId)
                .orElseThrow(() -> new DreClassificationNotFoundException(
                        dreClassificationId));

        classificationRepository
                .findByAccountingAccountId(accountingAccountId)
                .ifPresentOrElse(
                        classification -> classification.reclassify(
                                dreClassification),
                        () -> classificationRepository.save(
                                new AccountingAccountClassification(
                                        accountingAccount,
                                        dreClassification)));
    }
}