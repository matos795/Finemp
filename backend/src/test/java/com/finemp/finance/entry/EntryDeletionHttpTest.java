package com.finemp.finance.entry;

import com.finemp.access.*;
import com.finemp.security.*;
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

@WebMvcTest(controllers=EntryDeletionController.class,properties={"finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true"})
@Import({SecurityConfiguration.class,CompanyPermissionAuthorizationManagerFactory.class})
class EntryDeletionHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean EntryDeletionCommandService service;
    @MockitoBean CompanyPermissionAuthorizationService permissions;
    @MockitoBean UserDetailsService users;
    @MockitoBean SessionAccountStateValidator sessions;
    @MockitoBean QuestorImportAuthorizationManager imports;
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID(),key=UUID.randomUUID();
    @Test void bodylessDeletionMustReturnTheOriginalReceiptWithoutRequiringJson()throws Exception {
        allow();when(service.delete(actor,company,key,id,0L)).thenReturn(new FinancialEntryCommandReceipt(company,key,id,EntryCommandAction.DELETED,0));
        mvc.perform(delete(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key).param("expectedVersion","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED"));verify(service).delete(actor,company,key,id,0L);
    }
    @Test void missingCsrfAndCurrentPermissionMustFailBeforeServiceInvocation()throws Exception {
        allow();mvc.perform(delete(route()).with(user(principal())).header("Idempotency-Key",key).param("expectedVersion","0")).andExpect(status().isForbidden());
        when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(false);
        mvc.perform(delete(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key).param("expectedVersion","0")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void missingOrInvalidVersionAndHeaderMustReturnFixedBadRequest()throws Exception {
        allow();mvc.perform(delete(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FINANCIAL_ENTRY_COMMAND"));
        mvc.perform(delete(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key).param("expectedVersion","private-invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Comando financeiro inválido."));
        mvc.perform(delete(route()).with(user(principal())).with(csrf()).param("expectedVersion","0")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    void allow() {when(sessions.isCurrent(any())).thenReturn(true);when(permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);}
    String route() {return "/api/companies/"+company+"/financial-entries/"+id;}
    UserAccountPrincipal principal() {var user=new UserAccount("Synthetic Entry Delete HTTP","delete@example.invalid","hash");ReflectionTestUtils.setField(user,"id",actor);return new UserAccountPrincipal(user);}
}
