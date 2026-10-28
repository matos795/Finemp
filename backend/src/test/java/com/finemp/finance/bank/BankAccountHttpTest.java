package com.finemp.finance.bank;

import com.finemp.access.*;
import com.finemp.security.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=BankAccountController.class,properties="finemp.finance.catalog.enabled=true")
@Import({SecurityConfiguration.class,CompanyPermissionAuthorizationManagerFactory.class})
class BankAccountHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean BankAccountService service;
    @MockitoBean CompanyPermissionAuthorizationService permissions;
    @MockitoBean UserDetailsService users;
    @MockitoBean SessionAccountStateValidator sessions;
    @MockitoBean QuestorImportAuthorizationManager imports;

    @Test void authenticatedBankRequestMustReachServiceAndRetainItsPermissionEnforcement()throws Exception{
        UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),bank=UUID.randomUUID();
        var account=new UserAccount("Synthetic HTTP","synthetic@example.invalid","hash");
        ReflectionTestUtils.setField(account,"id",actor);
        var principal=new UserAccountPrincipal(account);
        when(sessions.isCurrent(any())).thenReturn(true);
        when(service.find(actor,company,bank)).thenReturn(new BankAccountResponse(bank,company,"Synthetic",BankAccountType.CASH,new BigDecimal("0.00"),LocalDate.of(2026,7,1),true,0));
        var route="/api/companies/"+company+"/bank-accounts/"+bank;
        mvc.perform(get(route).with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(bank.toString()));
        when(service.find(actor,company,bank)).thenThrow(new com.finemp.administration.OrganizationAccessDeniedException());
        mvc.perform(get(route).with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(get(route+"/unsupported").with(user(principal))).andExpect(status().isForbidden());
        verify(service,times(2)).find(actor,company,bank);
    }
    @Test void anonymousOrMissingCsrfMustNotReachFinancialService()throws Exception{
        UUID company=UUID.randomUUID(),bank=UUID.randomUUID();
        String route="/api/companies/"+company+"/bank-accounts/"+bank;
        mvc.perform(get(route)).andExpect(status().isUnauthorized());
        mvc.perform(delete(route).with(user("synthetic")).param("version","0")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
