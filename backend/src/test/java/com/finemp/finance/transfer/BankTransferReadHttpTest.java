package com.finemp.finance.transfer;

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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=BankTransferReadController.class,properties="finemp.finance.transfers.enabled=true")
@Import({SecurityConfiguration.class,CompanyPermissionAuthorizationManagerFactory.class})
class BankTransferReadHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean BankTransferReadService service;
    @MockitoBean CompanyPermissionAuthorizationService permissions;
    @MockitoBean UserDetailsService users;
    @MockitoBean SessionAccountStateValidator sessions;
    @MockitoBean QuestorImportAuthorizationManager imports;
    @Test void authorizedRouteMustPreserveExactStringAmountAndDelegateTheCompanyScope()throws Exception {
        var actor=UUID.randomUUID();var company=UUID.randomUUID();var id=UUID.randomUUID();when(sessions.isCurrent(any())).thenReturn(true);
        when(service.find(actor,company,id)).thenReturn(new BankTransferResponse(id,company,UUID.randomUUID(),"Origin",UUID.randomUUID(),"Destination",LocalDate.of(2026,7,1),new BigDecimal("99999999999999999.99"),"Synthetic",0));
        mvc.perform(get(route(company)+"/"+id).with(user(principal(actor)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isString()).andExpect(jsonPath("$.amount").value("99999999999999999.99"))
                .andExpect(jsonPath("$.createdBy").doesNotExist());verify(service).find(actor,company,id);
    }
    @Test void serviceDenialAndInvalidPaginationBindingMustProduceFixedSafeErrors()throws Exception {
        var actor=UUID.randomUUID();var company=UUID.randomUUID();when(sessions.isCurrent(any())).thenReturn(true);
        when(service.list(actor,company,0,50)).thenThrow(new com.finemp.administration.OrganizationAccessDeniedException());
        mvc.perform(get(route(company)).with(user(principal(actor)))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BANK_TRANSFER_ACCESS_DENIED"));
        mvc.perform(get(route(company)).param("page","not-a-number").with(user(principal(actor)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BANK_TRANSFER_QUERY"));
    }
    @Test void anonymousAndUnimplementedWritersMustNotReachTheReadService()throws Exception {
        var company=UUID.randomUUID();when(sessions.isCurrent(any())).thenReturn(true);var actor=principal(UUID.randomUUID());
        mvc.perform(get(route(company))).andExpect(status().isUnauthorized());
        mvc.perform(post(route(company)).with(user(actor)).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete(route(company)+"/"+UUID.randomUUID()).with(user(actor)).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    String route(UUID company) {return "/api/companies/"+company+"/bank-transfers";}
    UserAccountPrincipal principal(UUID id) {var account=new UserAccount("Synthetic Transfer HTTP","transfer@example.invalid","hash");ReflectionTestUtils.setField(account,"id",id);return new UserAccountPrincipal(account);}
}
