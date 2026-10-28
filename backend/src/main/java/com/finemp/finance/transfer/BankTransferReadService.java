package com.finemp.finance.transfer;

import com.finemp.access.*;
import com.finemp.administration.*;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix="finemp.finance.transfers",name="enabled",havingValue="true")
@Transactional(readOnly=true)
public class BankTransferReadService {
    private final BankTransferReadRepository queries;
    private final CompanyPermissionAuthorizationService permissions;
    public BankTransferReadService(BankTransferReadRepository queries,CompanyPermissionAuthorizationService permissions) {this.queries=queries;this.permissions=permissions;}
    public BankTransferResponse find(UUID actor,UUID company,UUID transfer) {
        requireRead(actor,company);return queries.find(company,transfer).orElseThrow(()->new SaasResourceNotFoundException("Transferência financeira"));
    }
    public PagedResponse<BankTransferResponse> list(UUID actor,UUID company,int page,int size) {
        requireRead(actor,company);if(page<0 || size<1 || size>100)throw new IllegalArgumentException("Paginação de transferências inválida.");
        return PagedResponse.from(queries.list(company,PageRequest.of(page,size)),row->row);
    }
    private void requireRead(UUID actor,UUID company) {
        if(actor==null || company==null || !permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_VIEW)
                && !permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE))throw new OrganizationAccessDeniedException();
    }
}
