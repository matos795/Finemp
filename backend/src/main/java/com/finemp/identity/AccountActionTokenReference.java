package com.finemp.identity;

import java.util.UUID;

public record AccountActionTokenReference(UUID id, UUID userAccountId) {
}
