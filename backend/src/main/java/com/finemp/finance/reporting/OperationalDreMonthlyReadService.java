package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.FinancialEntryValues;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Current entry effects only; other operational DRE sources are not yet implemented. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.operational-dre",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
public class OperationalDreMonthlyReadService {
    private final CompanyPermissionAuthorizationService permissions;
    private final JdbcTemplate jdbc;
    public OperationalDreMonthlyReadService(CompanyPermissionAuthorizationService permissions,JdbcTemplate jdbc){this.permissions=permissions;this.jdbc=jdbc;}
    public Report report(UUID actor,UUID company,String month){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        YearMonth period;
        try{if(month==null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid();period=YearMonth.parse(month);if(period.getYear()<1)throw invalid();}catch(DateTimeException ex){throw invalid();}
        var competence=new EnumMap<OperationalDreLine,BigDecimal>(OperationalDreLine.class);
        var settlement=new EnumMap<OperationalDreLine,BigDecimal>(OperationalDreLine.class);
        String sql="""
            SELECT e.*,COALESCE(c.dre_line,p.dre_line,'NAO') AS effective_dre
            FROM financial_entries e
            LEFT JOIN financial_categories c ON c.company_id=e.company_id AND c.id=e.category_id
            LEFT JOIN financial_categories p ON p.company_id=e.company_id AND p.id=c.parent_id
            WHERE e.company_id=? AND (e.competence=? OR e.payment_date BETWEEN ? AND ?)
            """;
        jdbc.query(sql,ps->{ps.setObject(1,company);ps.setString(2,period.toString());ps.setObject(3,period.atDay(1));ps.setObject(4,period.atEndOfMonth());ps.setFetchSize(500);},rs->{
            var values=values(rs);
            for(var effect:OperationalDreEntryEffects.calculate(values,OperationalDreLine.valueOf(rs.getString("effective_dre")))){
                if(!effect.period().equals(period))continue;
                var target=effect.source()==OperationalDreEntryEffects.Source.COMPETENCE?competence:settlement;
                target.merge(effect.line(),effect.amount(),BigDecimal::add);
            }
        });
        var lines=new ArrayList<Line>();
        for(var line:OperationalDreLine.values())if(line!=OperationalDreLine.NAO){
            var base=competence.getOrDefault(line,zero());var difference=settlement.getOrDefault(line,zero());
            lines.add(new Line(line,base,difference,base.add(difference)));
        }
        return new Report(company,period.toString(),true,List.of("FINANCIAL_ENTRIES"),lines);
    }
    private static FinancialEntryValues values(ResultSet rs)throws SQLException{
        var cost=rs.getString("cost_behavior_override");
        return new FinancialEntryValues(FinancialDirection.valueOf(rs.getString("direction")),rs.getString("description"),rs.getBigDecimal("nominal_amount"),
                rs.getObject("document_date",LocalDate.class),rs.getObject("entry_date",LocalDate.class),YearMonth.parse(rs.getString("competence")),rs.getObject("due_date",LocalDate.class),
                rs.getObject("payment_date",LocalDate.class),rs.getBigDecimal("actual_paid_amount"),rs.getObject("bank_account_id",UUID.class),rs.getObject("category_id",UUID.class),
                rs.getObject("partner_id",UUID.class),rs.getObject("result_center_id",UUID.class),cost==null?null:CostBehavior.valueOf(cost),rs.getString("payment_method"),rs.getString("document_number"),rs.getString("notes"));
    }
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Mês da DRE operacional inválido.");}
    public record Line(OperationalDreLine line,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal competenceAmount,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal settlementDifference,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount){}
    public record Report(UUID companyId,String month,boolean partial,List<String> coverage,List<Line> lines){
        public Report{coverage=List.copyOf(coverage);lines=List.copyOf(lines);}
    }
}
