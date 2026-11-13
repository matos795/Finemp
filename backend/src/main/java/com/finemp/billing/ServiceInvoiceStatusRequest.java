package com.finemp.billing;

public record ServiceInvoiceStatusRequest(
        Long expectedVersion,
        String invoiceNumber) {
}
