package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.category.FinancialDirection;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Read-only snapshot: SQL applies the previously verified matcher and bounded pagination. */
@Service
@ConditionalOnProperty(prefix="finemp.finance.statement-candidates",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class StatementCandidateReadService {
    private static final LocalDate MIN=LocalDate.of(1,1,1),MAX=LocalDate.of(9999,12,31);
    private final NamedParameterJdbcTemplate jdbc;private final CompanyPermissionAuthorizationService permissions;
    public StatementCandidateReadService(NamedParameterJdbcTemplate jdbc,CompanyPermissionAuthorizationService permissions){this.jdbc=jdbc;this.permissions=permissions;}
    public PagedResponse<View> list(UUID actor,UUID company,UUID statement,Status status,int page,int size){
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW) && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        if(statement==null || page<0 || size<1 || size>100)throw new IllegalArgumentException("Página de candidatos inválida.");
        var params=new MapSqlParameterSource().addValue("company",company).addValue("statement",statement);
        var source=jdbc.query("SELECT bank_account_id,posted_date,signed_amount FROM financial_bank_statement_items WHERE company_id=:company AND id=:statement",params,(rs,n)->new Source(rs.getObject("bank_account_id",UUID.class),rs.getObject("posted_date",LocalDate.class),rs.getBigDecimal("signed_amount"))).stream().findFirst().orElseThrow(()->new SaasResourceNotFoundException("Movimento de extrato"));
        params.addValue("bank",source.bank()).addValue("date",source.date()).addValue("amount",source.amount().abs()).addValue("direction",source.amount().signum()>0?"INCOME":"EXPENSE")
            .addValue("paidFrom",shift(source.date(),-3)).addValue("paidTo",shift(source.date(),3)).addValue("openFrom",shift(source.date(),-15)).addValue("openTo",shift(source.date(),15));
        String where="""
              FROM financial_entries e WHERE e.company_id=:company AND e.direction=:direction
              AND NOT EXISTS (SELECT 1 FROM financial_bank_statement_items s WHERE s.company_id=e.company_id AND s.linked_entry_id=e.id)
              AND ((e.payment_date IS NOT NULL AND e.bank_account_id=:bank AND e.payment_date BETWEEN :paidFrom AND :paidTo)
                OR (e.payment_date IS NULL AND e.due_date BETWEEN :openFrom AND :openTo))
              AND abs((CASE WHEN e.payment_date IS NOT NULL THEN COALESCE(e.actual_paid_amount,e.nominal_amount) ELSE e.nominal_amount END)-:amount)<0.011
              """+(status==null?"":status==Status.PAID?" AND e.payment_date IS NOT NULL":" AND e.payment_date IS NULL");
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) "+where,params,Long.class));
        params.addValue("limit",size).addValue("offset",(long)page*size);
        var result=jdbc.query("SELECT e.*,abs((CASE WHEN e.payment_date IS NULL THEN e.due_date ELSE e.payment_date END)-CAST(:date AS date)) AS distance "+where+" ORDER BY (e.payment_date IS NOT NULL) DESC,distance ASC,e.id ASC LIMIT :limit OFFSET :offset",params,(rs,n)->view(rs));
        return new PagedResponse<>(result,page,size,total,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size));
    }
    private View view(ResultSet r)throws SQLException{return new View(r.getObject("id",UUID.class),r.getString("description"),FinancialDirection.valueOf(r.getString("direction")),r.getObject("bank_account_id",UUID.class),r.getObject("due_date",LocalDate.class),r.getObject("payment_date",LocalDate.class),r.getBigDecimal("nominal_amount"),r.getBigDecimal("actual_paid_amount"),r.getLong("version"),r.getInt("distance"));}
    private static LocalDate shift(LocalDate date,int days){if(days<0 && date.toEpochDay()-MIN.toEpochDay()<-days)return MIN;if(days>0 && MAX.toEpochDay()-date.toEpochDay()<days)return MAX;return date.plusDays(days);}
    public enum Status { PAID,OPEN }
    private record Source(UUID bank,LocalDate date,BigDecimal amount){}
    public record View(UUID entryId,String description,FinancialDirection direction,UUID bankAccountId,LocalDate dueDate,LocalDate paymentDate,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal nominalAmount,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal actualPaidAmount,long version,int distanceDays){}
}
