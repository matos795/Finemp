package com.finemp.finance.reconciliation;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.statement-preview.enabled=true","finemp.finance.statement-import.enabled=true"})
@AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
class StatementPreviewHttpIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired BankAccountRepository banks;
    AccountingFirm firm;Client client;Company owner,other;UserAccount actor,viewer;BankAccount bank,foreign;
    final String csv="Data;Valor;Histórico\n2026-07-01;-20,00;Compra sintética\n";
    @BeforeEach void prepare(){
        firm=firms.saveAndFlush(new AccountingFirm("Synthetic Preview Firm",true));client=clients.saveAndFlush(new Client(firm,"Synthetic Preview Client",true));
        owner=companies.saveAndFlush(new Company(client,"Synthetic Preview Owner","60606060000160"));other=companies.saveAndFlush(new Company(client,"Synthetic Preview Other","70707070000170"));
        actor=users.saveAndFlush(new UserAccount("Synthetic Preview Manager",UUID.randomUUID()+"@example.invalid","hash"));viewer=users.saveAndFlush(new UserAccount("Synthetic Preview Viewer",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE)));
        grants.saveAndFlush(new CompanyAccessGrant(viewer,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));
        bank=banks.saveAndFlush(new BankAccount(owner,"Synthetic Preview Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));
        foreign=banks.saveAndFlush(new BankAccount(other,"Synthetic Foreign Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));
    }
    @AfterEach void cleanup(){for(var company:List.of(owner,other)){for(var table:List.of("financial_statement_import_receipts","financial_bank_statement_items","financial_periods","access_audit_events","financial_bank_accounts"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());companies.deleteById(company.getId());}clients.deleteById(client.getId());firms.deleteById(firm.getId());users.deleteById(actor.getId());users.deleteById(viewer.getId());}
    @Test void authorizedPreviewReturnsExactMoneyWithoutWriting()throws Exception{
        mvc.perform(post(route(owner,bank)).with(user(principal(actor))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(csv.getBytes(StandardCharsets.UTF_8)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.rows[0].values.signedAmount").value("-20.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_bank_statement_items WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    @Test void companyPermissionBankOwnershipAndCsrfAreRequired()throws Exception{
        mvc.perform(post(route(owner,bank)).with(user(principal(viewer))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(csv)).andExpect(status().isForbidden());
        mvc.perform(post(route(other,foreign)).with(user(principal(actor))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(csv)).andExpect(status().isForbidden());
        mvc.perform(post(route(owner,foreign)).with(user(principal(actor))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(csv)).andExpect(status().isNotFound());
        mvc.perform(post(route(owner,bank)).with(user(principal(actor))).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(csv)).andExpect(status().isForbidden());
    }
    @Test void unsupportedEncodingAndOversizedInputAreRejected()throws Exception{
        mvc.perform(post(route(owner,bank)).with(user(principal(actor))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").header("Content-Encoding","gzip").contentType("application/octet-stream").content(csv)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(route(owner,bank)).with(user(principal(actor))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(new byte[16*1024*1024+1])).andExpect(status().isPayloadTooLarge());
    }
    @Test void ofxPreviewReturnsRowsAndInvalidFilesAreBadRequests()throws Exception{
        var ofx="OFXHEADER:100\nDATA:OFXSGML\n<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20260701\n<TRNAMT>-12.34\n<MEMO>Compra sintetica\n</STMTTRN></BANKTRANLIST></OFX>";
        mvc.perform(post(route(owner,bank)).with(user(principal(actor))).with(csrf()).param("format","OFX").contentType("application/octet-stream").content(ofx)).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].values.signedAmount").value("-12.34"));
        mvc.perform(post(route(owner,bank)).with(user(principal(actor))).with(csrf()).param("format","OFX").contentType("application/octet-stream").content("invalid")).andExpect(status().isBadRequest());
    }
    @Test void confirmationReparsesTheReviewedFileAndReplaysWithoutDuplicates()throws Exception{
        var preview=mvc.perform(post(route(owner,bank)).with(user(principal(actor))).with(csrf()).param("format","CSV").param("separator","SEMICOLON").contentType("application/octet-stream").content(csv)).andExpect(status().isOk()).andReturn();
        String hash=preview.getResponse().getHeader("X-Statement-SHA256");assertThat(hash).isEqualTo(StatementFileHash.sha256(csv.getBytes(StandardCharsets.UTF_8)));
        String body=confirmation(csv,hash,"[2]");UUID key=UUID.randomUUID();
        String first=mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(content().json(first));
        mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body+" ")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_bank_statement_items WHERE company_id=?",Long.class,owner.getId())).isEqualTo(1);
        mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body.replace("\"selectedRows\":[2]","\"selectedRows\":[2],\"extra\":1"))).andExpect(status().isBadRequest());
    }
    @Test void confirmationRequiresMatchingFileExplicitAcknowledgementsCompanyAndCsrf()throws Exception{
        String hash=StatementFileHash.sha256(csv.getBytes(StandardCharsets.UTF_8));String body=confirmation(csv,hash,"[2]");
        for(var invalid:List.of(body.replace(hash,"a".repeat(64)),body.replace("\"accountConfirmed\":true","\"accountConfirmed\":false"),body.replace("[2]","[99]")))mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post(importRoute(owner,bank)).with(user(principal(viewer))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(importRoute(owner,foreign)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(importRoute(owner,bank)).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_bank_statement_items WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    @Test void confirmationCannotBypassClosedMonth()throws Exception{
        jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,'2026-07',true,0,?,now())",UUID.randomUUID(),owner.getId(),actor.getId());
        var body=confirmation(csv,StatementFileHash.sha256(csv.getBytes(StandardCharsets.UTF_8)),"[2]");
        mvc.perform(post(importRoute(owner,bank)).with(user(principal(actor))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINANCIAL_PERIOD_CLOSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_bank_statement_items WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    String confirmation(String file,String hash,String rows){return "{\"format\":\"CSV\",\"separator\":\"SEMICOLON\",\"fileBase64\":\""+Base64.getEncoder().encodeToString(file.getBytes(StandardCharsets.UTF_8))+"\",\"fileSha256\":\""+hash+"\",\"selectedRows\":"+rows+",\"accountConfirmed\":true,\"currencyConfirmed\":true,\"warningsConfirmed\":true}";}
    String importRoute(Company company,BankAccount account){return "/api/companies/"+company.getId()+"/bank-accounts/"+account.getId()+"/statement-imports";}
    String route(Company company,BankAccount account){return "/api/companies/"+company.getId()+"/bank-accounts/"+account.getId()+"/statement-previews";}
    UserAccountPrincipal principal(UserAccount user){return new UserAccountPrincipal(user);}
}
