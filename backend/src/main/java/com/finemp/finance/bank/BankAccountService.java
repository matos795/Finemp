package com.finemp.finance.bank;

import com.finemp.access.*;
import com.finemp.company.*;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import com.finemp.administration.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
@Transactional(readOnly=true)
public class BankAccountService{
    private final BankAccountRepository banks;
    private final CompanyRepository companies;
    private final CompanyPermissionAuthorizationService permissions;
    private final ObjectProvider<BankAccountReferenceGuard> references;
    private final BankWriteTransactionGuard transactions;
    public BankAccountService(BankAccountRepository banks,CompanyRepository companies,CompanyPermissionAuthorizationService permissions,
            ObjectProvider<BankAccountReferenceGuard> references,BankWriteTransactionGuard transactions){
        this.banks=banks;this.companies=companies;this.permissions=permissions;this.references=references;this.transactions=transactions;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BankAccountResponse create(UUID actor,UUID company,BankAccountRequest request){
        var owner=writeOwner(actor,company);validate(request);
        if(request.expectedVersion()!=null)throw invalid();
        var row=new BankAccount(owner,request.name(),request.type(),request.openingBalance(),request.openingDate(),request.active());
        return BankAccountResponse.from(banks.saveAndFlush(row));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BankAccountResponse update(UUID actor,UUID company,UUID bank,BankAccountRequest request){
        writeOwner(actor,company);validate(request);var row=owned(company,bank);version(row,request.expectedVersion());
        row.replace(request.name(),request.type(),request.openingBalance(),request.openingDate(),request.active());
        banks.flush();return BankAccountResponse.from(row);
    }
    public BankAccountResponse find(UUID actor,UUID company,UUID bank){requireRead(actor,company);return BankAccountResponse.from(owned(company,bank));}
    public PagedResponse<BankAccountResponse> list(UUID actor,UUID company,String search,BankAccountType type,Boolean active,int page,int size){
        requireRead(actor,company);
        if(page<0||size<1||size>100||search!=null&&search.length()>100)throw invalid();
        var paging=PageRequest.of(page,size,Sort.by("name").ascending().and(Sort.by("id")));
        return PagedResponse.from(banks.search(company,search==null?"":search.trim(),type,active,paging),BankAccountResponse::from);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DeleteResult delete(UUID actor,UUID company,UUID bank,long expectedVersion){
        writeOwner(actor,company);var row=owned(company,bank);version(row,expectedVersion);
        if(references.orderedStream().anyMatch(guard->guard.hasReferences(company,bank))){
            row.deactivate();banks.flush();return new DeleteResult(bank,DeleteAction.DEACTIVATED);
        }
        if(banks.countByCompanyId(company)<=1)throw new BankAccountConflictException(BankAccountConflictException.Reason.LAST_ACCOUNT);
        banks.delete(row);banks.flush();return new DeleteResult(bank,DeleteAction.DELETED);
    }
    private Company writeOwner(UUID actor,UUID company){
        requireManage(actor,company);transactions.requireReadCommitted();
        var owner=companies.findLockedById(company).orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor,company);return owner;
    }
    private BankAccount owned(UUID company,UUID bank){return banks.findByIdAndCompanyId(bank,company).orElseThrow(()->new SaasResourceNotFoundException("Conta financeira"));}
    private void requireRead(UUID actor,UUID company){
        if(!permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_VIEW)&&!permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE))
            throw new OrganizationAccessDeniedException();
    }
    private void requireManage(UUID actor,UUID company){if(!permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE))throw new OrganizationAccessDeniedException();}
    private void version(BankAccount row,Long expected){
        if(expected==null||expected<0)throw invalid();
        if(row.getVersion()!=expected)throw new BankAccountConflictException(BankAccountConflictException.Reason.STALE_VERSION);
    }
    private void validate(BankAccountRequest request){if(request==null||request.active()==null)throw invalid();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Parâmetros de conta financeira inválidos.");}
    public enum DeleteAction{DELETED,DEACTIVATED}
    public record DeleteResult(UUID id,DeleteAction action){ }
}
