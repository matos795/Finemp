package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.FinancialDirection;
import java.sql.Connection;
import java.time.*;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Composes existing partial reports in one database snapshot; never infers missing business blocks. */
@Service
@ConditionalOnProperty(name={"finemp.finance.presentation.enabled","finemp.finance.aging.enabled","finemp.finance.daily-cash.enabled","finemp.finance.monthly-cash.enabled"},havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=15)
public class FinancialPresentationSnapshotService {
    private final CompanyPermissionAuthorizationService permissions;
    private final JdbcTemplate jdbc;
    private final DailyCashTotalsService daily;
    private final MonthlyCashTotalsService monthly;
    private final OpenTitlesAgingService aging;
    public FinancialPresentationSnapshotService(CompanyPermissionAuthorizationService permissions,JdbcTemplate jdbc,DailyCashTotalsService daily,MonthlyCashTotalsService monthly,OpenTitlesAgingService aging){
        this.permissions=permissions;this.jdbc=jdbc;this.daily=daily;this.monthly=monthly;this.aging=aging;
    }
    public Snapshot snapshot(UUID actor,UUID company,String month,LocalDate asOf,OpenTitlesAgingService.Scope scope){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        YearMonth period;
        try{if(month==null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid();period=YearMonth.parse(month);if(period.getYear()<1)throw invalid();}catch(DateTimeException ex){throw invalid();}
        if(asOf==null || asOf.getYear()<1 || asOf.getYear()>9999 || scope==null)throw invalid();
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)connection->connection.getTransactionIsolation()==Connection.TRANSACTION_REPEATABLE_READ || connection.getTransactionIsolation()==Connection.TRANSACTION_SERIALIZABLE)))
            throw new IllegalStateException("Snapshot financeiro exige isolamento consistente.");
        var cashMonth=daily.report(actor,company,month);
        var cashYear=monthly.report(actor,company,period.getYear());
        var payables=aging.report(actor,company,FinancialDirection.EXPENSE,asOf,scope);
        var receivables=aging.report(actor,company,FinancialDirection.INCOME,asOf,scope);
        return new Snapshot(1,true,company,month,asOf,scope,cashMonth,cashYear,payables,receivables);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Mês, data-base e escopo explícitos são obrigatórios.");}
    public record Snapshot(int schemaVersion,boolean partial,UUID companyId,String month,LocalDate asOf,OpenTitlesAgingService.Scope scope,
            DailyCashTotalsService.Report cashMonth,MonthlyCashTotalsService.Report cashYear,OpenTitlesAgingService.Report payables,OpenTitlesAgingService.Report receivables){}
}
