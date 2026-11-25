package com.finemp.people;

import com.finemp.finance.center.ResultCenter;
import java.time.LocalDate;

record EmployeeValues(
        String fullName,
        EmploymentType employmentType,
        JobPosition jobPosition,
        ResultCenter resultCenter,
        LocalDate hiredOn,
        LocalDate terminatedOn) { }
