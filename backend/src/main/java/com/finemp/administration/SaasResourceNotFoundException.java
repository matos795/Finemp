package com.finemp.administration;

public class SaasResourceNotFoundException extends RuntimeException {

    public SaasResourceNotFoundException(String resource) {
        super(resource + " was not found in the requested hierarchy.");
    }
}
