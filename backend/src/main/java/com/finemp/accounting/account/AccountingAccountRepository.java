package com.finemp.accounting.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountingAccountRepository
        extends JpaRepository<AccountingAccount, UUID> {
}