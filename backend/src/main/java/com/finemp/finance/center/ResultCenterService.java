package com.finemp.finance.center;

import com.finemp.access.*;
import com.finemp.company.*;
import com.finemp.administration.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
@Transactional(readOnly=true)
public class ResultCenterService {
    private final ResultCenterRepository centers;
    private final FinancialCompanySettingsRepository settings;
    private final CompanyRepository companies;
    private final CompanyPermissionAuthorizationService permissions;
    private final ObjectProvider<ResultCenterUsageGuard> usage;
    private final CenterWriteTransactionGuard transactions;
    public ResultCenterService(ResultCenterRepository centers,FinancialCompanySettingsRepository settings,CompanyRepository companies,
            CompanyPermissionAuthorizationService permissions,ObjectProvider<ResultCenterUsageGuard> usage,CenterWriteTransactionGuard transactions) {
        this.centers=centers;this.settings=settings;this.companies=companies;this.permissions=permissions;this.usage=usage;this.transactions=transactions;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ResultCenterResponse create(UUID actor,UUID company,ResultCenterCreateRequest request) {
        var owner=writeOwner(actor,company);
        if(request==null || request.type()==null)throw invalid();
        return ResultCenterResponse.from(createRow(owner,request),false);
    }
    private ResultCenter createRow(Company owner,ResultCenterCreateRequest request) {
        UUID company=owner.getId();var code=new ResultCenterCode(request.code());
        if(centers.existsByCompanyIdAndCode(company,code.value()))throw conflict(ResultCenterConflictException.Reason.DUPLICATE_CODE);
        UUID parentId=null;
        if(code.parentCode()!=null) {
            var parent=centers.findByCompanyIdAndCode(company,code.parentCode()).orElseThrow(()->conflict(ResultCenterConflictException.Reason.PARENT_NOT_FOUND));
            parentId=parent.getId();
        }
        return centers.saveAndFlush(new ResultCenter(owner,code.value(),parentId,request.name(),true,request.type()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ResultCenterResponse update(UUID actor,UUID company,UUID id,ResultCenterUpdateRequest request) {
        writeOwner(actor,company);
        if(request==null || request.active()==null)throw invalid();
        var row=owned(company,id);version(row,request.expectedVersion());
        if(request.type()!=null && request.type()!=row.getType()) {
            if(request.type()==ResultCenterType.GROUP && usage.orderedStream().anyMatch(guard->guard.hasEntries(company,id)))
                throw conflict(ResultCenterConflictException.Reason.HAS_ENTRIES);
            row.changeType(request.type());
        }
        row.replace(request.name(),request.active());centers.flush();
        return response(row);
    }
    public ResultCenterResponse find(UUID actor,UUID company,UUID id) {requireRead(actor,company);return response(owned(company,id));}
    private ResultCenterResponse response(ResultCenter row) {
        return ResultCenterResponse.from(row,centers.existsByCompanyIdAndParentId(row.getCompany().getId(),row.getId()));
    }
    public PagedResponse<ResultCenterResponse> list(UUID actor,UUID company,String search,Boolean active,UUID rootId,int page,int size) {
        requireRead(actor,company);
        if(page<0 || size<1 || size>100 || search!=null && search.length()>100)throw invalid();
        String root=rootId==null?"":owned(company,rootId).getCode();
        var result=centers.search(company,search==null?"":ResultCenter.searchKey(search),active,root,
                PageRequest.of(page,size,Sort.by("sortKey").ascending().and(Sort.by("id"))));
        var ids=result.getContent().stream().map(ResultCenter::getId).collect(Collectors.toSet());
        var parents=ids.isEmpty()?Set.<UUID>of():centers.parentsWithChildren(company,ids);
        return PagedResponse.from(result,row->ResultCenterResponse.from(row,parents.contains(row.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DeleteResult delete(UUID actor,UUID company,UUID id,long expectedVersion) {
        writeOwner(actor,company);var row=owned(company,id);version(row,expectedVersion);
        if(usage.orderedStream().anyMatch(guard->guard.hasReferences(company,id))) {
            row.deactivate();centers.flush();return new DeleteResult(id,DeleteAction.DEACTIVATED);
        }
        if(centers.existsByCompanyIdAndParentId(company,id))throw conflict(ResultCenterConflictException.Reason.HAS_CHILDREN);
        centers.delete(row);centers.flush();return new DeleteResult(id,DeleteAction.DELETED);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public List<ResultCenterResponse> createSuggestedPlan(UUID actor,UUID company) {
        var owner=writeOwner(actor,company);
        if(centers.countByCompanyId(company)>0)throw conflict(ResultCenterConflictException.Reason.PLAN_NOT_EMPTY);
        var rows=new ArrayList<ResultCenter>();
        String[][] plan={{"1","Comercial"},{"1.1","Loja física"},{"1.2","Loja virtual"},{"2","Serviços"},
                {"2.1","Contratos de manutenção"},{"3","Administrativo"},{"4","Financeiro"}};
        for(var item:plan)rows.add(createRow(owner,new ResultCenterCreateRequest(item[0],item[1],
                Set.of("1","2").contains(item[0])?ResultCenterType.GROUP:ResultCenterType.ANALYTICAL)));
        var parents=centers.parentsWithChildren(company,rows.stream().map(ResultCenter::getId).collect(Collectors.toSet()));
        return rows.stream().map(row->ResultCenterResponse.from(row,parents.contains(row.getId()))).toList();
    }
    public SettingsResponse settings(UUID actor,UUID company) {
        requireRead(actor,company);
        return settings.findById(company).map(SettingsResponse::from).orElseGet(()->new SettingsResponse(company,false,null));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SettingsResponse updateSettings(UUID actor,UUID company,ResultCenterSettingsRequest request) {
        writeOwner(actor,company);
        if(request==null || request.requireResultCenter()==null || request.expectedVersion()!=null && request.expectedVersion()<0)throw invalid();
        var current=settings.findById(company);
        if(current.isEmpty()) {
            if(request.expectedVersion()!=null)throw conflict(ResultCenterConflictException.Reason.STALE_VERSION);
            return SettingsResponse.from(settings.saveAndFlush(new FinancialCompanySettings(company,request.requireResultCenter())));
        }
        var row=current.orElseThrow();
        if(!Objects.equals(row.getVersion(),request.expectedVersion()))throw conflict(ResultCenterConflictException.Reason.STALE_VERSION);
        row.requireResultCenter(request.requireResultCenter());settings.flush();return SettingsResponse.from(row);
    }
    private Company writeOwner(UUID actor,UUID company) {
        requireManage(actor,company);transactions.requireReadCommitted();
        var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor,company);return owner;
    }
    private ResultCenter owned(UUID company,UUID id) {
        return centers.findByIdAndCompanyId(id,company).orElseThrow(()->new SaasResourceNotFoundException("Centro de resultado"));
    }
    private void requireRead(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.RESULT_CENTER_VIEW)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.RESULT_CENTER_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private void requireManage(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.RESULT_CENTER_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private void version(ResultCenter row,Long expected) {
        if(expected==null || expected<0)throw invalid();
        if(row.getVersion()!=expected)throw conflict(ResultCenterConflictException.Reason.STALE_VERSION);
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Parâmetros de centro de resultado inválidos.");}
    private static ResultCenterConflictException conflict(ResultCenterConflictException.Reason reason) {return new ResultCenterConflictException(reason);}
    public record SettingsResponse(UUID companyId,boolean requireResultCenter,Long version) {
        static SettingsResponse from(FinancialCompanySettings row) {return new SettingsResponse(row.getCompanyId(),row.isRequireResultCenter(),row.getVersion());}
    }
    public enum DeleteAction { DELETED, DEACTIVATED }
    public record DeleteResult(UUID id,DeleteAction action) { }
}
