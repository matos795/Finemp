package com.finemp.company;

import com.finemp.access.CompanyAccessAuthorizationService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyProfileQueryService {

    private final CompanyRepository companies;
    private final CompanyAccessAuthorizationService authorization;

    public CompanyProfileQueryService(
            CompanyRepository companies,
            CompanyAccessAuthorizationService authorization) {
        this.companies = companies;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public CompanyProfileResponse get(UUID actorUserAccountId, UUID companyId) {
        authorization.requireAccess(actorUserAccountId, companyId);

        var company = companies.findById(companyId)
                .orElseThrow(CompanyProfileNotFoundException::new);

        return CompanyProfileResponse.from(company);
    }
}
