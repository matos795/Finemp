package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.OperationalDreLine;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Flat, paginated F06 effects. SQL must remain in parity with OperationalDreEntryEffects. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.operational-dre",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
public class OperationalDreEffectReadService {
    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;
    public OperationalDreEffectReadService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public Result list(UUID actor,UUID company,String month,OperationalDreLine line,int page,int size){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        YearMonth period;
        try{if(month==null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])") || page<0 || size<1 || size>100)throw invalid();period=YearMonth.parse(month);if(period.getYear()<1)throw invalid();}catch(DateTimeException ex){throw invalid();}
        var args=new MapSqlParameterSource().addValue("company",company).addValue("month",period.toString()).addValue("from",period.atDay(1)).addValue("to",period.atEndOfMonth());
        String cte="""
            WITH base AS (
                SELECT e.id,e.description,e.category_id,e.competence,e.payment_date,
                    COALESCE(c.dre_line,p.dre_line,'NAO') AS effective_line,
                    CASE WHEN e.direction='INCOME' THEN e.nominal_amount ELSE -e.nominal_amount END AS nominal,
                    (COALESCE(e.actual_paid_amount,e.nominal_amount)-e.nominal_amount)
                        * CASE WHEN e.direction='INCOME' THEN 1 ELSE -1 END AS difference
                FROM financial_entries e
                LEFT JOIN financial_categories c ON c.company_id=e.company_id AND c.id=e.category_id
                LEFT JOIN financial_categories p ON p.company_id=e.company_id AND p.id=c.parent_id
                WHERE e.company_id=:company AND (e.competence=:month OR e.payment_date BETWEEN :from AND :to)
            ), effects AS (
                SELECT id,description,category_id,competence,payment_date,effective_line AS line,
                    'COMPETENCE' AS source,nominal AS amount
                FROM base WHERE effective_line<>'NAO' AND competence=:month
                UNION ALL
                SELECT id,description,category_id,competence,payment_date,
                    CASE WHEN difference>0 THEN 'RFIN' ELSE 'DFIN' END AS line,
                    'SETTLEMENT_DIFFERENCE' AS source,difference AS amount
                FROM base WHERE effective_line<>'NAO' AND payment_date BETWEEN :from AND :to AND difference<>0
            )
            """;
        String from=" FROM effects";
        if(line!=null){from+=" WHERE line=:line";args.addValue("line",line.name());}
        long total=Objects.requireNonNull(jdbc.queryForObject(cte+"SELECT count(*)"+from,args,Long.class));
        args.addValue("limit",size).addValue("offset",(long)page*size);
        var rows=jdbc.query(cte+"SELECT *"+from+" ORDER BY id,source LIMIT :limit OFFSET :offset",args,(rs,n)->new View(rs.getObject("id",UUID.class),rs.getString("description"),rs.getObject("category_id",UUID.class),rs.getString("competence"),rs.getObject("payment_date",LocalDate.class),OperationalDreLine.valueOf(rs.getString("line")),Source.valueOf(rs.getString("source")),rs.getBigDecimal("amount")));
        return new Result(company,period.toString(),true,List.of("FINANCIAL_ENTRIES"),rows,page,size,total,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size));
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Consulta de efeitos DRE inválida.");}
    public enum Source {COMPETENCE,SETTLEMENT_DIFFERENCE}
    public record View(UUID entryId,String description,UUID categoryId,String competence,LocalDate paymentDate,OperationalDreLine line,Source source,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount){}
    public record Result(UUID companyId,String month,boolean partial,List<String> coverage,List<View> items,int page,int size,long totalItems,int totalPages){
        public Result{coverage=List.copyOf(coverage);items=List.copyOf(items);}
    }
}
