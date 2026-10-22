package com.finemp.access;

public class CompanyAccessDeniedException extends RuntimeException {

    public CompanyAccessDeniedException() {
        super("User account is not authorized to access the requested company.");
    }
}