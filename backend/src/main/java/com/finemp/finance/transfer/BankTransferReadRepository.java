package com.finemp.finance.transfer;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BankTransferReadRepository extends Repository<BankTransfer,UUID> {
    String PROJECTION="""
            select new com.finemp.finance.transfer.BankTransferResponse(t.id,t.company.id,
                t.values.originBankAccountId,origin.name,t.values.destinationBankAccountId,destination.name,
                t.values.transferDate,t.values.amount,t.values.description,t.version)
            from BankTransfer t
            join BankAccount origin on origin.company.id=t.company.id and origin.id=t.values.originBankAccountId
            join BankAccount destination on destination.company.id=t.company.id and destination.id=t.values.destinationBankAccountId
            where t.company.id=:company
            """;
    @Query(PROJECTION+" and t.id=:id")
    @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    Optional<BankTransferResponse> find(@Param("company") UUID company,@Param("id") UUID id);
    @Query(value=PROJECTION+" order by t.values.transferDate desc,t.id",countQuery="select count(t) from BankTransfer t where t.company.id=:company")
    @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    Page<BankTransferResponse> list(@Param("company") UUID company,Pageable page);
}
