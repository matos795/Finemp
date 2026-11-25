package com.finemp.people;

import com.finemp.finance.center.ResultCenterUsageGuard;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Funcionários mantêm o centro de resultado referenciado mesmo quando ele é desativado. */
@Configuration(proxyBeanMethods = false)
public class PeopleCatalogReferenceGuards {

    @Bean
    ResultCenterUsageGuard employeeResultCenterReferences(EmployeeRepository employees) {
        return new ResultCenterUsageGuard() {
            @Override
            public boolean hasEntries(UUID companyId, UUID centerId) {
                return false;
            }

            @Override
            public boolean hasReferences(UUID companyId, UUID centerId) {
                return employees.existsByCompanyIdAndResultCenterId(companyId, centerId);
            }
        };
    }
}
