package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.bank.*;
import com.finemp.security.UserAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementPreviewControllerTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);
    final BankAccountRepository banks=mock(BankAccountRepository.class);
    final UserAccountPrincipal actor=mock(UserAccountPrincipal.class);
    final StatementPreviewController controller=new StatementPreviewController(permissions,banks,new StatementRequestCapacity());
    final UUID user=UUID.randomUUID(),company=UUID.randomUUID(),bank=UUID.randomUUID();
    @Test void unauthorizedAndForeignBankAreRejectedBeforeReadingFile()throws Exception {
        when(actor.getUserAccountId()).thenReturn(user);var request=mock(HttpServletRequest.class);
        assertThatThrownBy(()->controller.preview(actor,company,bank,"OFX",null,request)).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(request,never()).getInputStream();verifyNoInteractions(banks);
        when(permissions.hasFreshPermission(user,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);
        assertThatThrownBy(()->controller.preview(actor,company,bank,"OFX",null,request)).isInstanceOf(SaasResourceNotFoundException.class);
        verify(request,never()).getInputStream();
    }
    @Test void validPreviewIsUncachedAndDoesNotPersist() {
        when(actor.getUserAccountId()).thenReturn(user);when(permissions.hasFreshPermission(user,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);
        when(banks.findByIdAndCompanyId(bank,company)).thenReturn(Optional.of(mock(BankAccount.class)));
        var request=new MockHttpServletRequest();request.setContent("Data;Valor\n2026-07-01;-20,00\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result=controller.preview(actor,company,bank,"CSV","SEMICOLON",request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        var preview=(CsvStatementPreviewParser.Preview)result.getBody();assertThat(preview.rows().getFirst().values().signedAmount()).isEqualByComparingTo("-20.00");
        verify(banks).findByIdAndCompanyId(bank,company);verifyNoMoreInteractions(banks);
    }
}
