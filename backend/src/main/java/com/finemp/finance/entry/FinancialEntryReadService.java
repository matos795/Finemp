package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.finance.center.ResultCenterRepository;
import com.finemp.administration.*;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@Transactional(readOnly=true)
public class FinancialEntryReadService {
    private final FinancialEntryReadRepository queries;
    private final CompanyPermissionAuthorizationService permissions;
    private final ResultCenterRepository centers;
    private final Clock calendar;
    public FinancialEntryReadService(FinancialEntryReadRepository queries,CompanyPermissionAuthorizationService permissions,ResultCenterRepository centers,
            @Value("${finemp.finance.entries.calendar-zone:America/Sao_Paulo}") String calendarZone) {
        this.queries=queries;this.permissions=permissions;this.centers=centers;this.calendar=Clock.system(ZoneId.of(calendarZone));
    }
    public FinancialEntryResponse find(UUID actor,UUID company,UUID id,LocalDate asOf) {
        requireRead(actor,company);LocalDate reference=reference(asOf);
        return queries.find(company,id).orElseThrow(()->new SaasResourceNotFoundException("Lançamento financeiro")).response(reference);
    }
    public PagedResponse<FinancialEntryResponse> list(UUID actor,UUID company,FinancialEntryFilter filter,int page,int size) {
        requireRead(actor,company);
        if(filter==null)filter=FinancialEntryFilter.empty();
        if(page<0 || size<1 || size>100 || filter.search()!=null && filter.search().length()>100)throw invalid();
        date(filter.from());date(filter.to());
        if(filter.from()!=null && filter.to()!=null && filter.from().isAfter(filter.to()))throw invalid();
        boolean without=Boolean.TRUE.equals(filter.withoutCenter());
        if(without && filter.centerId()!=null)throw invalid();
        String code=filter.centerId()==null?"":centers.findCurrentCode(company,filter.centerId()).orElseThrow(()->new SaasResourceNotFoundException("Centro de resultado"));
        LocalDate asOf=reference(filter.asOfDate());
        // Status is computed, not an entity-mapped enum: bind its validated API name as SQL text.
        var result=queries.search(company,filter.from(),filter.to(),filter.direction(),filter.status()==null?null:filter.status().name(),filter.bankId(),filter.categoryId(),filter.partnerId(),
                code,without,filter.costBehavior(),filter.search()==null?"":filter.search().trim(),asOf,PageRequest.of(page,size));
        return PagedResponse.from(result,row->row.response(asOf));
    }
    private LocalDate reference(LocalDate requested) {LocalDate value=requested==null?LocalDate.now(calendar):requested;date(value);return value;}
    private void date(LocalDate value) {if(value!=null && (value.getYear()<1 || value.getYear()>9999))throw invalid();}
    private void requireRead(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Parâmetros de consulta financeira inválidos.");}
}
