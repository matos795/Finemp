package com.finemp.accounting.classification;

import com.finemp.accounting.account.AccountingAccount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AccountingAccountClassificationQueryRepository
        extends Repository<AccountingAccount, UUID> {

    @Query("""
            select new com.finemp.accounting.classification.AccountingAccountClassificationRow(
                account.id,
                source.id,
                account.accountKey,
                account.description,
                dreClassification.id,
                dreClassification.dreClassification,
                dreClassification.dreOpenClassification,
                dreClassification.ebitdaClassification
            )
            from AccountingAccount account
            join account.accountingSource source
            left join AccountingAccountClassification accountClassification
                on accountClassification.accountingAccount = account
            left join accountClassification.classification dreClassification
            where source.company.id = :companyId
            order by account.accountKey
            """)
    List<AccountingAccountClassificationRow> findAllByCompanyId(
            @Param("companyId") UUID companyId);
}