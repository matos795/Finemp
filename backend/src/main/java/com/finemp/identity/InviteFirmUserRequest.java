package com.finemp.identity;

import com.finemp.access.AccountingFirmPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record InviteFirmUserRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Set<@NotNull AccountingFirmPermission> permissions) {
    @Override
    public String toString() { return "InviteFirmUserRequest[identity=redacted]"; }
}
