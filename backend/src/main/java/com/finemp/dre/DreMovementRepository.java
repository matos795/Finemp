package com.finemp.dre;

import com.finemp.accounting.movement.AccountingMovement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DreMovementRepository
        extends Repository<AccountingMovement, UUID> {

    @Query("""
            select new com.finemp.dre.DreMovementRow(
                account.id,
                account.accountKey,
                movement.side,
                movement.amount,
                classification.dreClassification,
                classification.dreOpenClassification,
                classification.ebitdaClassification
            )
            from AccountingMovement movement
            join movement.account account
            join account.accountingSource source
            left join AccountingAccountClassification classification
                on classification.accountingAccount = account
            where source.company.id = :companyId
              and movement.accountingDate between :startDate and :endDate
            """)
    List<DreMovementRow> findAllByCompanyAndPeriod(
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}