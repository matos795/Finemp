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

    /**
     * Página, contagem e totais vêm de consultas distintas: REPEATABLE_READ garante o mesmo
     * snapshot mesmo se uma publicação for confirmada entre elas.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AccountingMovementSearchResponse search(
            UUID companyId,
            LocalDate startDate,
            LocalDate endDate,
            UUID accountingSourceId,
            UUID accountId,
            int page,
            int size) {
        validatePeriod(startDate, endDate);
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Invalid movements page.");
        }
        UUID none = new UUID(0, 0);
        boolean allSources = accountingSourceId == null;
        boolean allAccounts = accountId == null;
        UUID sourceId = allSources ? none : accountingSourceId;
        UUID account = allAccounts ? none : accountId;

        var result = accountingMovementRepository.searchByCompanyAndPeriod(companyId, startDate, endDate,
                allSources, sourceId, allAccounts, account, org.springframework.data.domain.PageRequest.of(page, size));
        var totals = accountingMovementRepository.totalsByCompanyAndPeriod(companyId, startDate, endDate,
                allSources, sourceId, allAccounts, account);

        return new AccountingMovementSearchResponse(
                result.getContent().stream().map(AccountingMovementSearchResponse.Item::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                AccountingMovementSearchResponse.Totals.from(totals),
                AccountingMovementSearchResponse.NOT_SYNCHRONIZED);
    }
}
