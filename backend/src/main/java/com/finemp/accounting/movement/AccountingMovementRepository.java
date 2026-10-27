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
}
