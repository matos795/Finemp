package com.finemp.accounting.ingestion;

import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.movement.AccountingMovement;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountingMovementIngestionService {

    private final AccountingSourceRepository accountingSourceRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final AccountingMovementRepository accountingMovementRepository;

    public AccountingMovementIngestionService(
            AccountingSourceRepository accountingSourceRepository,
            AccountingAccountRepository accountingAccountRepository,
            AccountingMovementRepository accountingMovementRepository) {
        this.accountingSourceRepository = accountingSourceRepository;
        this.accountingAccountRepository = accountingAccountRepository;
        this.accountingMovementRepository = accountingMovementRepository;
    }

    @Transactional
    public void ingest(
            UUID accountingSourceId,
            List<ImportedAccountingMovement> movements) {
        AccountingSource accountingSource = accountingSourceRepository
                .findById(accountingSourceId)
                .orElseThrow();

        for (ImportedAccountingMovement movement : movements) {

            if (movementAlreadyExists(
                    accountingSourceId,
                    movement)) {
                continue;
            }

            AccountingAccount account = findOrCreateAccount(
                    accountingSource,
                    movement);

            accountingMovementRepository.save(
                    new AccountingMovement(
                            account,
                            movement.sourceEntryKey(),
                            movement.side(),
                            movement.accountingDate(),
                            movement.amount(),
                            movement.history()));
        }
    }

    private boolean movementAlreadyExists(
            UUID accountingSourceId,
            ImportedAccountingMovement movement) {
        return accountingMovementRepository
                .existsBySourceEntryKeyAndSide(
                        accountingSourceId,
                        movement.sourceEntryKey(),
                        movement.side());
    }

    private AccountingAccount findOrCreateAccount(
            AccountingSource accountingSource,
            ImportedAccountingMovement movement) {
        return accountingAccountRepository
                .findByAccountingSourceIdAndExternalAccountId(
                        accountingSource.getId(),
                        movement.externalAccountId())
                .orElseGet(() -> accountingAccountRepository.save(
                        new AccountingAccount(
                                accountingSource,
                                movement.externalAccountId(),
                                movement.accountKey(),
                                movement.accountDescription())));
    }
}