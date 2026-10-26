package com.finemp.security;

import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.dre.DreCalculationService;
import com.finemp.dre.DreController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DreController.class)
@Import({
                SecurityConfiguration.class,
                CompanyPermissionAuthorizationManagerFactory.class
})
class SecurityHttpTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private DreCalculationService dreCalculationService;

        @MockitoBean
        private CompanyRequestAuthorizationManager companyRequestAuthorizationManager;

        @MockitoBean
        private CompanyPermissionAuthorizationService companyPermissionAuthorizationService;

        @MockitoBean
        private UserDetailsService userDetailsService;

        @MockitoBean
        private SessionAccountStateValidator sessionAccountStateValidator;

        @Test
        void shouldReturnUnauthorizedWhenRequestIsNotAuthenticated()
                        throws Exception {

                when(companyRequestAuthorizationManager.authorize(
                                any(),
                                any()))
                                .thenReturn(new AuthorizationDecision(false));

                UUID companyId = UUID.randomUUID();

                mockMvc.perform(
                                get("/api/companies/{companyId}/dre", companyId)
                                                .param("startDate", "2026-07-01")
                                                .param("endDate", "2026-07-31"))
                                .andExpect(status().isUnauthorized());
        }
}
