package com.finemp.finance.transfer;

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

@WebMvcTest(controllers=BankTransferCommandController.class,properties={"finemp.finance.transfers.enabled=true","finemp.finance.transfers.commands.enabled=true"})
@Import({SecurityConfiguration.class,CompanyPermissionAuthorizationManagerFactory.class,BankTransferCommandJsonDecoder.class})
class BankTransferCommandHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean BankTransferCommandService service;
    @MockitoBean CompanyPermissionAuthorizationService permissions;
    @MockitoBean UserDetailsService users;
    @MockitoBean SessionAccountStateValidator sessions;
    @MockitoBean QuestorImportAuthorizationManager imports;
    final UUID actor=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID(),key=UUID.randomUUID();
    @Test void createAndBodylessDeleteMustReturnTheServicesImmutableReceipts()throws Exception {
        allow();var created=new BankTransferCommandReceipt(company,key,id,BankTransferCommandAction.CREATED,0);when(service.create(eq(actor),eq(company),eq(key),any())).thenReturn(created);
        mvc.perform(post(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(header().string("Location",route()+"/"+id)).andExpect(jsonPath("$.transferId").value(id.toString()));
        when(service.delete(actor,company,key,id,0L)).thenReturn(new BankTransferCommandReceipt(company,key,id,BankTransferCommandAction.DELETED,0));
        mvc.perform(delete(route()+"/"+id).queryParam("expectedVersion","0").with(user(principal())).with(csrf()).header("Idempotency-Key",key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED"));
    }
    @Test void currentDenialOrMissingCsrfMustPrecedeOversizedBodyHandling()throws Exception {
        allow();when(permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(false);
        mvc.perform(post(route()).with(user(principal())).with(csrf()).contentType("application/json").content(" ".repeat(65537))).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(user(principal())).contentType("application/json").content(body())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void authorizedBodiesAndDeleteParametersMustBeStrictlyBoundedBeforeService()throws Exception {
        allow();mvc.perform(post(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(" ".repeat(65537))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(route()).with(user(principal())).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body().replace("\"amount\":\"1.23\"","\"amount\":1.23")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BANK_TRANSFER_COMMAND"));
        mvc.perform(delete(route()+"/"+id).with(user(principal())).with(csrf()).header("Idempotency-Key",key)).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void formBodyMustNotSupplyTheVersionMissingFromTheQuery()throws Exception {
        allow();mvc.perform(delete(route()+"/"+id).with(user(principal())).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/x-www-form-urlencoded").content("expectedVersion=0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BANK_TRANSFER_COMMAND"));verifyNoInteractions(service);
    }
    void allow() {when(sessions.isCurrent(any())).thenReturn(true);when(permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE)).thenReturn(true);}
    String route() {return "/api/companies/"+company+"/bank-transfers";}
    UserAccountPrincipal principal() {var account=new UserAccount("Synthetic Transfer Command HTTP","command@example.invalid","hash");ReflectionTestUtils.setField(account,"id",actor);return new UserAccountPrincipal(account);}
    String body() {return "{\"values\":{\"originBankAccountId\":\"00000000-0000-0000-0000-000000000001\",\"destinationBankAccountId\":\"00000000-0000-0000-0000-000000000002\",\"transferDate\":\"2026-07-01\",\"amount\":\"1.23\"}}";}
}
