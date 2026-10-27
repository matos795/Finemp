package com.finemp.administration;

public class OrganizationAccessDeniedException extends RuntimeException {

    public OrganizationAccessDeniedException() {
        super("User account is not authorized to perform the requested administration operation.");
    }
}
