package com.finemp.finance.partner;

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
public class BusinessPartnerService {
    private final BusinessPartnerRepository partners;
    private final CompanyRepository companies;
    private final CompanyPermissionAuthorizationService permissions;
    private final ObjectProvider<BusinessPartnerReferenceGuard> references;
    private final PartnerWriteTransactionGuard transactions;
    public BusinessPartnerService(BusinessPartnerRepository partners,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,
            ObjectProvider<BusinessPartnerReferenceGuard> references,PartnerWriteTransactionGuard transactions) {
        this.partners=partners;this.companies=companies;this.permissions=permissions;this.references=references;this.transactions=transactions;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BusinessPartnerResponse create(UUID actor,UUID company,BusinessPartnerRequest request) {
        var owner=writeOwner(actor,company);validate(request);
        if(request.expectedVersion()!=null)throw invalid();
        unique(company,request.profile().document(),null);
        return BusinessPartnerResponse.from(partners.saveAndFlush(new BusinessPartner(owner,request.profile())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BusinessPartnerResponse update(UUID actor,UUID company,UUID id,BusinessPartnerRequest request) {
        writeOwner(actor,company);validate(request);
        var row=owned(company,id);version(row,request.expectedVersion());unique(company,request.profile().document(),id);row.replace(request.profile());
        partners.flush();return BusinessPartnerResponse.from(row);
    }
    public BusinessPartnerResponse find(UUID actor,UUID company,UUID id) {
        requireRead(actor,company);return BusinessPartnerResponse.from(owned(company,id));
    }
    public PagedResponse<BusinessPartnerResponse> list(UUID actor,UUID company,String search,PartnerRole role,Boolean active,int page,int size) {
        requireRead(actor,company);
        if(page<0 || size<1 || size>100 || search!=null && search.length()>100)throw invalid();
        String text=search==null?"":search.trim();
        // Search accepts longer text than the document parser; only a bounded candidate is useful.
        String doc=text.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z]","");
        if(doc.length()<3 || doc.length()>14)doc="";
        Set<PartnerRole> roles=role==null?EnumSet.allOf(PartnerRole.class):EnumSet.of(role,PartnerRole.BOTH);
        var pageable=PageRequest.of(page,size,Sort.by("displayNameKey").ascending().and(Sort.by("id")));
        return PagedResponse.from(partners.search(company,BusinessPartner.searchKey(text),doc,roles,active,pageable),BusinessPartnerResponse::from);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DeleteResult delete(UUID actor,UUID company,UUID id,long expectedVersion) {
        writeOwner(actor,company);var row=owned(company,id);version(row,expectedVersion);
        if(references.orderedStream().anyMatch(guard->guard.hasReferences(company,id))) {
            row.deactivate();partners.flush();return new DeleteResult(id,DeleteAction.DEACTIVATED);
        }
        partners.delete(row);partners.flush();return new DeleteResult(id,DeleteAction.DELETED);
    }
    private Company writeOwner(UUID actor,UUID company) {
        requireManage(actor,company);transactions.requireReadCommitted();
        var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor,company);return owner;
    }
    private BusinessPartner owned(UUID company,UUID id) {
        return partners.findByIdAndCompanyId(id,company).orElseThrow(()->new SaasResourceNotFoundException("Parceiro comercial"));
    }
    private void requireRead(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_VIEW)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private void requireManage(UUID actor,UUID company) {
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.BUSINESS_PARTNER_MANAGE))throw new OrganizationAccessDeniedException();
    }
    private void version(BusinessPartner row,Long expected) {
        if(expected==null || expected<0)throw invalid();
        if(row.getVersion()!=expected)throw new BusinessPartnerConflictException(BusinessPartnerConflictException.Reason.STALE_VERSION);
    }
    private void validate(BusinessPartnerRequest request) {if(request==null || request.profile()==null)throw invalid();}
    private void unique(UUID company,String document,UUID excluding) {
        boolean used=excluding==null?partners.existsByCompanyIdAndProfileDocument(company,document)
                :partners.existsByCompanyIdAndProfileDocumentAndIdNot(company,document,excluding);
        if(used)throw new BusinessPartnerConflictException(BusinessPartnerConflictException.Reason.DUPLICATE_DOCUMENT);
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Parâmetros de parceiro inválidos.");}
    public enum DeleteAction { DELETED, DEACTIVATED }
    public record DeleteResult(UUID id,DeleteAction action) { }
}
