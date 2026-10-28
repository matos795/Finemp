package com.finemp.finance.category;

import com.finemp.access.*;
import com.finemp.company.*;
import com.finemp.administration.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
@Transactional(readOnly=true)
public class FinancialCategoryService {
    private final FinancialCategoryRepository categories;
    private final CompanyRepository companies;
    private final CompanyPermissionAuthorizationService permissions;
    private final ObjectProvider<FinancialCategoryUsageGuard> usage;
    private final CategoryWriteTransactionGuard transactions;
    public FinancialCategoryService(FinancialCategoryRepository categories,CompanyRepository companies,
            CompanyPermissionAuthorizationService permissions,ObjectProvider<FinancialCategoryUsageGuard> usage,CategoryWriteTransactionGuard transactions) {
        this.categories=categories;this.companies=companies;this.permissions=permissions;this.usage=usage;this.transactions=transactions;
    }
    public FinancialCategoryResponse find(UUID actor,UUID company,UUID id) {
        requireRead(actor,company);
        return categories.findSnapshot(company,id).orElseThrow(()->new SaasResourceNotFoundException("Categoria financeira")).response();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public FinancialCategoryResponse createGroup(UUID actor,UUID company,FinancialCategoryGroupRequest request) {
        var owner=writeOwner(actor,company);
        if(request==null || request.direction()==null)throw invalid();
        var group=categories.saveAndFlush(FinancialCategory.group(owner,request.direction(),next(categories.maxGroupSequence(company,request.direction())),request.name()));
        categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Nova subcategoria"));
        return FinancialCategoryResponse.from(group,null);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public FinancialCategoryResponse createSubcategory(UUID actor,UUID company,UUID groupId,FinancialSubcategoryRequest request) {
        writeOwner(actor,company);
        if(request==null)throw invalid();
        var parent=group(company,groupId);
        var child=categories.saveAndFlush(FinancialCategory.subcategory(parent,next(categories.maxSubcategorySequence(company,groupId)),request.name()));
        return FinancialCategoryResponse.from(child,parent);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public FinancialCategoryResponse update(UUID actor,UUID company,UUID id,FinancialCategoryUpdateRequest request) {
        writeOwner(actor,company);
        if(request==null)throw invalid();
        var row=owned(company,id);version(row,request.expectedVersion());
        FinancialCategory parent=null;
        if(row.getKind()==FinancialCategory.Kind.GROUP)row.updateGroup(request.name(),request.cashActivity(),request.dreLine(),request.costBehavior());
        else {
            parent=group(company,row.getParentId());
            row.updateSubcategory(parent,request.name(),request.cashActivity(),request.dreLine(),request.costBehavior());
        }
        categories.flush();return FinancialCategoryResponse.from(row,parent);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(UUID actor,UUID company,UUID id,long expectedVersion) {
        writeOwner(actor,company);var row=owned(company,id);version(row,expectedVersion);
        if(usage.orderedStream().anyMatch(guard->guard.hasEntriesInCategoryOrChildren(company,id)))throw conflict(FinancialCategoryConflictException.Reason.CATEGORY_IN_USE);
        if(row.getKind()==FinancialCategory.Kind.GROUP)categories.deleteChildren(company,id);
        categories.delete(row);categories.flush();
    }
    public PagedResponse<FinancialCategoryResponse> list(UUID actor,UUID company,String search,FinancialDirection direction,
            FinancialCategory.Kind kind,UUID parentId,int page,int size) {
        requireRead(actor,company);
        if(page<0 || size<1 || size>100 || search!=null && search.length()>100)throw invalid();
        if(parentId!=null)group(company,parentId);
        var result=categories.search(company,search==null?"":FinancialCategory.searchKey(search),direction,kind,parentId,
                PageRequest.of(page,size,Sort.by("sortKey").ascending().and(Sort.by("id"))));
        return PagedResponse.from(result,FinancialCategorySnapshot::response);
    }
    private Company writeOwner(UUID actor,UUID company) {
        requireManage(actor,company);transactions.requireReadCommitted();
        var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor,company);return owner;
    }
    private FinancialCategory owned(UUID company,UUID id) {
        return categories.findByIdAndCompanyId(id,company).orElseThrow(()->new SaasResourceNotFoundException("Categoria financeira"));
    }
    private FinancialCategory group(UUID company,UUID id) {
        var row=owned(company,id);
        if(row.getKind()!=FinancialCategory.Kind.GROUP)throw conflict(FinancialCategoryConflictException.Reason.NOT_A_GROUP);
        return row;
    }
    private void requireRead(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_VIEW)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private void requireManage(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_CATEGORY_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private void version(FinancialCategory row,Long expected) {
        if(expected==null || expected<0)throw invalid();
        if(row.getVersion()!=expected)throw conflict(FinancialCategoryConflictException.Reason.STALE_VERSION);
    }
    private long next(long previous) {
        if(previous<0 || previous==Long.MAX_VALUE)throw conflict(FinancialCategoryConflictException.Reason.SEQUENCE_EXHAUSTED);
        return previous+1;
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Parâmetros de categoria financeira inválidos.");}
    private static FinancialCategoryConflictException conflict(FinancialCategoryConflictException.Reason reason) {return new FinancialCategoryConflictException(reason);}
}
