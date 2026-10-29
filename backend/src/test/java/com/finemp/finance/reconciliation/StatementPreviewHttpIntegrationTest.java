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

@SpringBootTest(properties="finemp.finance.statement-preview.enabled=true")
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
    @AfterEach void cleanup(){for(var company:List.of(owner,other)){jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());companies.deleteById(company.getId());}clients.deleteById(client.getId());firms.deleteById(firm.getId());users.deleteById(actor.getId());users.deleteById(viewer.getId());}
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
    String route(Company company,BankAccount account){return "/api/companies/"+company.getId()+"/bank-accounts/"+account.getId()+"/statement-previews";}
    UserAccountPrincipal principal(UserAccount user){return new UserAccountPrincipal(user);}
}
