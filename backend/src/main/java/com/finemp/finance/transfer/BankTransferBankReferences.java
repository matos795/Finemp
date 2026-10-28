package com.finemp.finance.transfer;

import com.finemp.finance.bank.BankAccountReferenceGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reference integrity remains relevant even when operational producers are disabled. */
@Component
public class BankTransferBankReferences implements BankAccountReferenceGuard {
    private final BankTransferRepository transfers;
    public BankTransferBankReferences(BankTransferRepository transfers) {this.transfers=transfers;}
    @Override public boolean hasReferences(UUID company,UUID bank) {return transfers.hasBankReferences(company,bank);}
}
