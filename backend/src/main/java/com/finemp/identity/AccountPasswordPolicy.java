package com.finemp.identity;

import java.nio.charset.StandardCharsets;

public final class AccountPasswordPolicy {

    private AccountPasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= 8
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.matches("(?s).*[a-zA-Z].*")
                && password.matches("(?s).*\\d.*");
    }
}
