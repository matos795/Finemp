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

    /** Universo importado da Company, com filtros e ordem determinística (#442). */
    @Query(value = """
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
              and (:allSources = true or source.id = :sourceId)
              and (:search = ''
                   or locate(:search, lower(account.accountKey)) > 0
                   or locate(:search, lower(coalesce(account.description, ''))) > 0)
              and (:status = 'ALL'
                   or (:status = 'PENDING' and dreClassification.id is null)
                   or (:status = 'CLASSIFIED' and dreClassification.id is not null))
            order by account.accountKey, source.id, account.id
            """,
            countQuery = """
            select count(account)
            from AccountingAccount account
            join account.accountingSource source
            left join AccountingAccountClassification accountClassification
                on accountClassification.accountingAccount = account
            left join accountClassification.classification dreClassification
            where source.company.id = :companyId
              and (:allSources = true or source.id = :sourceId)
              and (:search = ''
                   or locate(:search, lower(account.accountKey)) > 0
                   or locate(:search, lower(coalesce(account.description, ''))) > 0)
              and (:status = 'ALL'
                   or (:status = 'PENDING' and dreClassification.id is null)
                   or (:status = 'CLASSIFIED' and dreClassification.id is not null))
            """)
    org.springframework.data.domain.Page<AccountingAccountClassificationRow> searchImported(
            @Param("companyId") UUID companyId,
            @Param("allSources") boolean allSources,
            @Param("sourceId") UUID sourceId,
            @Param("search") String search,
            @Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    /** Total e classificadas sobre fonte/busca (sem filtro de status nem página). */
    @Query("""
            select new com.finemp.accounting.classification.ImportedAccountsSummary(
                count(account), count(dreClassification.id))
            from AccountingAccount account
            join account.accountingSource source
            left join AccountingAccountClassification accountClassification
                on accountClassification.accountingAccount = account
            left join accountClassification.classification dreClassification
            where source.company.id = :companyId
              and (:allSources = true or source.id = :sourceId)
              and (:search = ''
                   or locate(:search, lower(account.accountKey)) > 0
                   or locate(:search, lower(coalesce(account.description, ''))) > 0)
            """)
    ImportedAccountsSummary summarizeImported(
            @Param("companyId") UUID companyId,
            @Param("allSources") boolean allSources,
            @Param("sourceId") UUID sourceId,
            @Param("search") String search);
}
