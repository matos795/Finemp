package com.finemp.finance.entry;

import com.finemp.finance.category.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface FinancialEntryReadRepository extends Repository<FinancialEntry,UUID> {
    String COST="""
            coalesce(entry.values.costBehaviorOverride,category.costBehavior,parentCategory.costBehavior,
              case when category.direction=com.finemp.finance.category.FinancialDirection.INCOME
              then com.finemp.finance.category.CostBehavior.VARIABLE else com.finemp.finance.category.CostBehavior.FIXED end)
            """;
    String SELECT="""
            select new com.finemp.finance.entry.FinancialEntryReadRow(
              entry.id,entry.company.id,entry.values,bank.name,category.code,category.name,
              case when partner.profile.tradeName<>'' then partner.profile.tradeName else coalesce(partner.profile.name,'') end,
              coalesce(center.code,''),coalesce(center.name,''),
            """+COST+",entry.createdAt,entry.updatedAt,entry.version) ";
    String FROM="""
            from FinancialEntry entry
            join BankAccount bank on bank.id=entry.values.bankAccountId and bank.company.id=entry.company.id
            join FinancialCategory category on category.id=entry.values.categoryId and category.company.id=entry.company.id
            left join FinancialCategory parentCategory on parentCategory.id=category.parentId and parentCategory.company.id=entry.company.id
            left join BusinessPartner partner on partner.id=entry.values.partnerId and partner.company.id=entry.company.id
            left join ResultCenter center on center.id=entry.values.resultCenterId and center.company.id=entry.company.id
            """;
    @Query(SELECT+FROM+"where entry.company.id=:company and entry.id=:id")
    @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    Optional<FinancialEntryReadRow> find(@Param("company") UUID company,@Param("id") UUID id);
    @Query(SELECT+FROM+"""
            where entry.company.id=:company
              and (:from is null or coalesce(entry.values.paymentDate,entry.values.dueDate)>=:from)
              and (:to is null or coalesce(entry.values.paymentDate,entry.values.dueDate)<=:to)
              and (:direction is null or entry.values.direction=:direction)
              and (:status is null
                or (:status='PAID' and entry.values.paymentDate is not null)
                or (:status='OPEN' and entry.values.paymentDate is null)
                or (:status='OVERDUE' and entry.values.paymentDate is null and entry.values.dueDate<:asOf))
              and (:bankId is null or entry.values.bankAccountId=:bankId)
              and (:categoryId is null or category.id=:categoryId or category.parentId=:categoryId)
              and (:partnerId is null or entry.values.partnerId=:partnerId)
              and (:withoutCenter=false or entry.values.resultCenterId is null)
              and (:centerCode='' or center.code=:centerCode or center.code like concat(:centerCode,'.%'))
              and (:search='' or locate(lower(:search),lower(concat(entry.values.description,' ',
                case when partner.profile.tradeName<>'' then partner.profile.tradeName else coalesce(partner.profile.name,'') end,' ',
                coalesce(partner.profile.document,''),' ',entry.values.documentNumber,' ',entry.values.notes,' ',category.name)))>0)
              and (:cost is null or
            """+COST+"""
              =:cost)
            order by coalesce(entry.values.paymentDate,entry.values.dueDate),entry.id
            """)
    @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    Page<FinancialEntryReadRow> search(@Param("company") UUID company,@Param("from") LocalDate from,@Param("to") LocalDate to,
            @Param("direction") FinancialDirection direction,@Param("status") String status,
            @Param("bankId") UUID bankId,@Param("categoryId") UUID categoryId,@Param("partnerId") UUID partnerId,
            @Param("centerCode") String centerCode,@Param("withoutCenter") boolean withoutCenter,@Param("cost") CostBehavior cost,
            @Param("search") String search,@Param("asOf") LocalDate asOf,Pageable pageable);
}
