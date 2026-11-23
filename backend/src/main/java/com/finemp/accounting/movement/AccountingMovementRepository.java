package com.finemp.accounting.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AccountingMovementRepository
        extends JpaRepository<AccountingMovement, UUID> {

    @Query("""
            select movement from AccountingMovement movement join fetch movement.account account
            where movement.accountingSourceId = :sourceId and movement.sourceEntryKey in :entryKeys
            """)
    List<AccountingMovement> findAllForPublication(
            @Param("sourceId") UUID sourceId, @Param("entryKeys") java.util.Collection<String> entryKeys);

    @Query("""
            select movement
            from AccountingMovement movement
            join fetch movement.account account
            join account.accountingSource source
            where source.company.id = :companyId
              and movement.accountingDate between :startDate and :endDate
            order by movement.accountingDate asc
            """)
    List<AccountingMovement> findAllByCompanyAndPeriod(
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select case when count(movement) > 0 then true else false end
            from AccountingMovement movement
            join movement.account account
            where account.accountingSource.id = :accountingSourceId
            and movement.sourceEntryKey = :sourceEntryKey
            and movement.side = :side
            """)
    boolean existsBySourceEntryKeyAndSide(
            @Param("accountingSourceId") UUID accountingSourceId,
            @Param("sourceEntryKey") String sourceEntryKey,
            @Param("side") AccountingMovementSide side
    );

    /** Página determinística por Company/período, com filtros opcionais de fonte e conta (#444). */
    @Query(value = """
            select movement
            from AccountingMovement movement
            join fetch movement.account account
            join account.accountingSource source
            where source.company.id = :companyId
              and movement.accountingDate between :startDate and :endDate
              and (:allSources = true or source.id = :sourceId)
              and (:allAccounts = true or account.id = :accountId)
            order by movement.accountingDate asc, source.id asc, movement.sourceEntryKey asc,
                     movement.side asc, movement.id asc
            """,
            countQuery = """
            select count(movement)
            from AccountingMovement movement
            join movement.account account
            join account.accountingSource source
            where source.company.id = :companyId
              and movement.accountingDate between :startDate and :endDate
              and (:allSources = true or source.id = :sourceId)
              and (:allAccounts = true or account.id = :accountId)
            """)
    org.springframework.data.domain.Page<AccountingMovement> searchByCompanyAndPeriod(
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("allSources") boolean allSources,
            @Param("sourceId") UUID sourceId,
            @Param("allAccounts") boolean allAccounts,
            @Param("accountId") UUID accountId,
            org.springframework.data.domain.Pageable pageable);

    /** Somas exatas de débito e crédito sobre o mesmo filtro, sem página. */
    @Query("""
            select new com.finemp.accounting.movement.AccountingMovementTotals(
                coalesce(sum(case when movement.side = com.finemp.accounting.movement.AccountingMovementSide.DEBIT
                                  then movement.amount else 0 end), 0),
                coalesce(sum(case when movement.side = com.finemp.accounting.movement.AccountingMovementSide.CREDIT
                                  then movement.amount else 0 end), 0))
            from AccountingMovement movement
            join movement.account account
            join account.accountingSource source
            where source.company.id = :companyId
              and movement.accountingDate between :startDate and :endDate
              and (:allSources = true or source.id = :sourceId)
              and (:allAccounts = true or account.id = :accountId)
            """)
    AccountingMovementTotals totalsByCompanyAndPeriod(
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("allSources") boolean allSources,
            @Param("sourceId") UUID sourceId,
            @Param("allAccounts") boolean allAccounts,
            @Param("accountId") UUID accountId);
}
