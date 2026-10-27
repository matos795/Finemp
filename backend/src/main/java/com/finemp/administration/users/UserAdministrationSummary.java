package com.finemp.administration.users;

import com.finemp.access.UserAccount;
import java.util.UUID;

public record UserAdministrationSummary(UUID id, String name, String email, boolean active, boolean invitationPending) {
    public static UserAdministrationSummary from(UserAccount user) {
        return new UserAdministrationSummary(user.getId(), user.getName(), user.getEmail(),
                user.isActive(), user.isInvitationPending());
    }
}
