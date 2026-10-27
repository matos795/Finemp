package com.finemp.administration;

import com.finemp.client.Client;

import java.util.UUID;

public record ClientResponse(
        UUID id,
        UUID accountingFirmId,
        String name,
        boolean active) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getAccountingFirm().getId(),
                client.getName(),
                client.isActive());
    }
}
