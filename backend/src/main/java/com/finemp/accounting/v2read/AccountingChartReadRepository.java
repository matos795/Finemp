package com.finemp.accounting.v2read;

import com.finemp.accounting.canonical.persistence.AccountingAccountSourceAttributes;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Plano completo = contas com atributos de plano v2 (vindas do dataset CHART).
 * Contas só observadas nos movimentos não têm atributos e nunca entram aqui.
 */
interface AccountingChartReadRepository extends Repository<AccountingAccountSourceAttributes, UUID> {

    @Query(value = """
            select new com.finemp.accounting.v2read.AccountingChartRow(
                account.id, attributes.sourceAccountId, attributes.classificationCode, account.description,
                attributes.accountType, attributes.calculationType, attributes.balanceNature,
                attributes.balanceControl, attributes.showInBalanceSheet, attributes.nativeDreCode,
                attributes.nativeDreAccount)
            from AccountingAccountSourceAttributes attributes
            join AccountingAccount account
              on account.accountingSource.id = attributes.accountingSourceId
             and account.externalAccountId = attributes.sourceAccountId
            where attributes.accountingSourceId = :sourceId
              and (:search = ''
                   or locate(:search, lower(attributes.sourceAccountId)) > 0
                   or locate(:search, lower(attributes.classificationCode)) > 0
                   or locate(:search, lower(account.description)) > 0)
            order by attributes.classificationCode asc, attributes.sourceAccountId asc
            """,
            countQuery = """
            select count(attributes)
            from AccountingAccountSourceAttributes attributes
            join AccountingAccount account
              on account.accountingSource.id = attributes.accountingSourceId
             and account.externalAccountId = attributes.sourceAccountId
            where attributes.accountingSourceId = :sourceId
              and (:search = ''
                   or locate(:search, lower(attributes.sourceAccountId)) > 0
                   or locate(:search, lower(attributes.classificationCode)) > 0
                   or locate(:search, lower(account.description)) > 0)
            """)
    Page<AccountingChartRow> search(
            @Param("sourceId") UUID sourceId,
            @Param("search") String search,
            Pageable pageable);
}
