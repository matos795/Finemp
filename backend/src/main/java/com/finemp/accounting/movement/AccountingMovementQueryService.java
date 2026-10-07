package com.finemp.accounting.movement;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AccountingMovementQueryService {

    private final AccountingMovementRepository accountingMovementRepository;

    public AccountingMovementQueryService(
            AccountingMovementRepository accountingMovementRepository) {
        this.accountingMovementRepository = accountingMovementRepository;
    }

    public List<AccountingMovementResponse> findByCompanyAndPeriod(
            UUID companyId,
            LocalDate startDate,
            LocalDate endDate) {
        validatePeriod(startDate, endDate);

        return accountingMovementRepository
                .findAllByCompanyAndPeriod(
                        companyId,
                        startDate,
                        endDate)
                .stream()
                .map(AccountingMovementResponse::from)
                .toList();
    }

    private void validatePeriod(
            LocalDate startDate,
            LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidAccountingPeriodException();
        }
    }
}