package com.finemp.administration;

public class CompanyAssignmentConflictException extends RuntimeException {

    public CompanyAssignmentConflictException() {
        super("Company is already assigned to another client.");
    }
}
