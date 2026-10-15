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

    public AccountingAccountClassificationService(
            AccountingAccountRepository accountingAccountRepository,
            AccountingAccountClassificationRepository classificationRepository) {
        this.accountingAccountRepository = accountingAccountRepository;
        this.classificationRepository = classificationRepository;
    }

    @Transactional
    public void classifyAccount(
            UUID accountingAccountId,
            String dreClassification,
            String dreOpenClassification,
            String ebitdaClassification) {

        AccountingAccount accountingAccount = accountingAccountRepository
                .findById(accountingAccountId)
                .orElseThrow(() -> new AccountingAccountNotFoundException(
                        accountingAccountId));

        AccountingAccountClassification classification = new AccountingAccountClassification(
                accountingAccount,
                dreClassification,
                dreOpenClassification,
                ebitdaClassification);

        classificationRepository.save(classification);
    }
}