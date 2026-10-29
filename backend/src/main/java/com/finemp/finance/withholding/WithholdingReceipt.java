package com.finemp.finance.withholding;

import java.util.UUID;

public record WithholdingReceipt(UUID companyId,UUID commandId,UUID entryId,UUID withholdingId,WithholdingAction action,long version,UUID auditEventId) {
    public WithholdingReceipt {if(companyId==null || commandId==null || entryId==null || withholdingId==null || action==null || version<0 || auditEventId==null)throw new IllegalArgumentException("Recibo de retenção inválido.");}
}
