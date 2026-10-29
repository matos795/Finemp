package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.bank.*;
import com.finemp.security.UserAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StatementImportControllerTest {
    final CompanyPermissionAuthorizationService permissions=mock(CompanyPermissionAuthorizationService.class);final BankAccountRepository banks=mock(BankAccountRepository.class);
    final UserAccountPrincipal actor=mock(UserAccountPrincipal.class);final StatementImportCommandService commands=mock(StatementImportCommandService.class);
    final StatementImportController controller=new StatementImportController(permissions,banks,new StatementRequestCapacity(),new StatementImportRequestDecoder(),commands);
    final UUID user=UUID.randomUUID(),company=UUID.randomUUID(),bank=UUID.randomUUID(),key=UUID.randomUUID();
    final String csv="Data;Valor;Histórico\n2026-07-01;-20,00;Compra sintética\n";
    void authorize(){when(actor.getUserAccountId()).thenReturn(user);when(permissions.hasFreshPermission(user,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE)).thenReturn(true);when(banks.findByIdAndCompanyId(bank,company)).thenReturn(Optional.of(mock(BankAccount.class)));}
    @Test void deniesBeforeReadingFile()throws Exception{when(actor.getUserAccountId()).thenReturn(user);var request=mock(HttpServletRequest.class);assertThatThrownBy(()->controller.confirm(actor,company,bank,key,request)).isInstanceOf(OrganizationAccessDeniedException.class);verify(request,never()).getInputStream();verifyNoInteractions(commands);}
    @Test void reparsesAndOnlyForwardsExplicitValidSelection(){
        authorize();var receipt=new StatementImportCommandService.Receipt(company,bank,key,List.of(UUID.randomUUID()),UUID.randomUUID());when(commands.confirmWithProvenance(eq(user),eq(company),eq(key),eq(bank),anyList(),anyString())).thenReturn(receipt);
        var result=controller.confirm(actor,company,bank,key,request(body(csv,StatementFileHash.sha256(csv.getBytes(StandardCharsets.UTF_8)),"[2]")));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        @SuppressWarnings("unchecked") var rows=(List<StatementValues>)mockingDetails(commands).getInvocations().iterator().next().getArgument(4);
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().signedAmount()).isEqualByComparingTo("-20.00");
    }
    @Test void rejectsWrongFileHashOrNonexistentSelectionWithoutWriting(){
        authorize();for(String payload:List.of(body(csv,"a".repeat(64),"[2]"),body(csv,StatementFileHash.sha256(csv.getBytes(StandardCharsets.UTF_8)),"[99]")))assertThatThrownBy(()->controller.confirm(actor,company,bank,key,request(payload))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(commands);
    }
    @Test void refusesMultipleAccountsOrExplicitNonBrlCurrencyWithoutWriting(){
        authorize();
        var base="OFXHEADER:100\nDATA:OFXSGML\n<OFX><CURDEF>BRL\n<BANKID>1\n<ACCTID>100\n<BANKTRANLIST><STMTTRN><DTPOSTED>20260701\n<TRNAMT>-20\n</STMTTRN></BANKTRANLIST></OFX>";
        for(String file:List.of(base.replace("<ACCTID>100\n","<ACCTID>100\n<ACCTID>200\n"),base.replace("<CURDEF>BRL","<CURDEF>USD"))){
            var payload="{\"format\":\"OFX\",\"fileBase64\":\""+Base64.getEncoder().encodeToString(file.getBytes(StandardCharsets.UTF_8))+"\",\"fileSha256\":\""+StatementFileHash.sha256(file.getBytes(StandardCharsets.UTF_8))+"\",\"selectedRows\":[1],\"accountConfirmed\":true,\"currencyConfirmed\":true,\"warningsConfirmed\":true}";
            assertThatThrownBy(()->controller.confirm(actor,company,bank,key,request(payload))).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(commands);
    }
    String body(String file,String hash,String rows){return "{\"format\":\"CSV\",\"separator\":\"SEMICOLON\",\"fileBase64\":\""+Base64.getEncoder().encodeToString(file.getBytes(StandardCharsets.UTF_8))+"\",\"fileSha256\":\""+hash+"\",\"selectedRows\":"+rows+",\"accountConfirmed\":true,\"currencyConfirmed\":true,\"warningsConfirmed\":true}";}
    MockHttpServletRequest request(String payload){var request=new MockHttpServletRequest();request.setContent(payload.getBytes(StandardCharsets.UTF_8));return request;}
}
