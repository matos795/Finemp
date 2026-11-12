package com.finemp.company;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CompanyProfileNotFoundException extends RuntimeException {

    public CompanyProfileNotFoundException() {
        super("Company not found.");
    }
}
