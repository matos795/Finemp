package com.finemp.company;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.finemp.access.CompanyAccessAuthorizationService;
import com.finemp.access.CompanyAccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyProfileQueryServiceTest {

    @Mock
    CompanyRepository companies;

    @Mock
    CompanyAccessAuthorizationService authorization;

    @Test
    void authorizationHappensBeforeCompanyRead() {
        var actorId = UUID.randomUUID();
        var companyId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new CompanyAccessDeniedException())
                .when(authorization)
                .requireAccess(actorId, companyId);

        var service = new CompanyProfileQueryService(companies, authorization);

        assertThrows(
                CompanyAccessDeniedException.class,
                () -> service.get(actorId, companyId));

        verifyNoInteractions(companies);
    }

    @Test
    void missingCompanyReturnsExplicitNotFoundAfterAuthorization() {
        var actorId = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        when(companies.findById(companyId)).thenReturn(Optional.empty());

        var service = new CompanyProfileQueryService(companies, authorization);

        assertThrows(
                CompanyProfileNotFoundException.class,
                () -> service.get(actorId, companyId));

        verify(authorization).requireAccess(actorId, companyId);
    }
}
