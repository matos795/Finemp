package com.finemp.finance.transfer;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.transfers.enabled=true","finemp.finance.transfers.commands.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BankTransferCommandHttpIntegrationTest {
    @Autowired BankTransferRepository transfers;@Autowired BankAccountRepository banks;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
    Company owner,other;UserAccount manager,viewer,restricted,admin;
    UUID origin,destination,foreignBank,foreignTransfer;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void prepare() {
        owner=company("Owner","85858585000185");other=company("Other","86868686000186");
        manager=createUser("Manager");viewer=createUser("Viewer");restricted=createUser("Restricted");admin=createUser("Admin");
        grant(manager,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_VIEW));
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        origin=bank(owner,"Origin");destination=bank(owner,"Destination");foreignBank=bank(other,"Foreign");var foreignSecond=bank(other,"Foreign second");
        foreignTransfer=transfers.saveAndFlush(new BankTransfer(other,new BankTransferValues(foreignBank,foreignSecond,LocalDate.of(2026,7,1),BigDecimal.ONE,"Synthetic foreign transfer"),manager.getId())).getId();
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_bank_transfer_events","financial_bank_transfer_command_receipts","financial_bank_transfers","financial_bank_accounts"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void createAndDeleteMustExposeReceiptsLocationAndExactBackendStateWithReplayAfterDeletion()throws Exception {
        var key=UUID.randomUUID();var created=create(key,body()).andExpect(status().isCreated()).andExpect(jsonPath("$.action").value("CREATED")).andReturn().getResponse();
        var id=UUID.fromString(json.readTree(created.getContentAsString()).get("transferId").asString());assertThat(created.getHeader("Location")).isEqualTo(route()+"/"+id);
        mvc.perform(get(route()+"/"+id).with(user(new UserAccountPrincipal(manager)))).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("1.23"));
        var deleteKey=UUID.randomUUID();var deleted=remove(deleteKey,id,0).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED")).andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE company_id=?",owner.getId());
        assertThat(create(key,body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).isEqualTo(created.getContentAsString());
        assertThat(remove(deleteKey,id,0).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(deleted);
        assertThat(count("financial_bank_transfers")).isZero();assertThat(count("financial_bank_transfer_events")).isEqualTo(2);assertThat(count("financial_bank_transfer_command_receipts")).isEqualTo(2);
        assertThat(banks.findById(origin).orElseThrow().getOpeningBalance()).isEqualByComparingTo("5.00");assertThat(count("financial_entries")).isZero();
    }
    @Test void missingAuthenticationCsrfOrManageMustFailBeforeOversizedBodyBuffering()throws Exception {
        var large=" ".repeat(65537);
        mvc.perform(post(route()).with(csrf()).contentType("application/json").content(large)).andExpect(status().isUnauthorized());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(manager))).contentType("application/json").content(large)).andExpect(status().isForbidden());
        for(var actor:List.of(viewer,admin,restricted))mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(large)).andExpect(status().isForbidden());
        mvc.perform(delete(route()+"/"+foreignTransfer).with(user(new UserAccountPrincipal(manager))).header("Idempotency-Key",UUID.randomUUID()).param("expectedVersion","0")).andExpect(status().isForbidden());empty();
    }
    @Test void revocationMustDenyReplayAndDeleteFromAnExistingPrincipal()throws Exception {
        var key=UUID.randomUUID();var response=create(key,body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();var id=UUID.fromString(json.readTree(response).get("transferId").asString());
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        create(key,body()).andExpect(status().isForbidden());remove(UUID.randomUUID(),id,0).andExpect(status().isForbidden());assertThat(transfers.findById(id)).isPresent();assertThat(count("financial_bank_transfer_events")).isEqualTo(1);
    }
    @Test void crossCompanyReferencesAndTargetsMustNeverBeAcceptedOrDisclosed()throws Exception {
        create(UUID.randomUUID(),body().replace(origin.toString(),foreignBank.toString())).andExpect(status().isBadRequest());
        create(UUID.randomUUID(),body().replace(destination.toString(),foreignBank.toString())).andExpect(status().isBadRequest());
        remove(UUID.randomUUID(),foreignTransfer,0).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BANK_TRANSFER_NOT_FOUND"));
        mvc.perform(post("/api/companies/"+other.getId()+"/bank-transfers").with(user(new UserAccountPrincipal(manager))).with(csrf()).contentType("application/json").content(body())).andExpect(status().isForbidden());
        assertThat(transfers.findById(foreignTransfer)).isPresent();empty();
    }
    @Test void idempotencyMismatchAndStaleDeleteMustConflictWithoutExtraAudit()throws Exception {
        var key=UUID.randomUUID();var response=create(key,body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();var id=UUID.fromString(json.readTree(response).get("transferId").asString());
        create(key,body().replace("1.23","1.24")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        remove(UUID.randomUUID(),id,1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(count("financial_bank_transfer_events")).isEqualTo(1);assertThat(transfers.findById(id)).isPresent();
    }
    @Test void invalidBodiesMissingHeadersAndDeleteVersionsMustReturnSafeFixedErrors()throws Exception {
        for(var payload:List.of(body().replace("\"amount\":\"1.23\"","\"amount\":1.23"),body().replace("\"values\":","\"actorId\":\"private\",\"values\":"),body()+"{}"))
            create(UUID.randomUUID(),payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BANK_TRANSFER_COMMAND"));
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(manager))).with(csrf()).contentType("application/json").content(body())).andExpect(status().isBadRequest());
        mvc.perform(delete(route()+"/"+UUID.randomUUID()).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isBadRequest());
        remove(UUID.randomUUID(),UUID.randomUUID(),-1).andExpect(status().isBadRequest());empty();
    }
    @Test void encodedRoutesAndBindingAliasesMustReceiveTheSameBodyBudgetAndOnlyPostReadsBodies()throws Exception {
        var encoded=java.net.URI.create(route().replace("bank-transfers","%62ank-transfers"));
        mvc.perform(post(encoded).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(" ".repeat(65537)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("BANK_TRANSFER_PAYLOAD_TOO_LARGE"));
        mvc.perform(post(encoded).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body())).andExpect(status().isCreated());
        var upperCompanyRoute=route().replace(owner.getId().toString(),owner.getId().toString().toUpperCase(Locale.ROOT));
        mvc.perform(post(upperCompanyRoute).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(" ".repeat(65537))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).header("Content-Encoding","gzip").contentType("application/json").content(body())).andExpect(status().isUnsupportedMediaType());
        remove(UUID.randomUUID(),UUID.fromString("00000001-0001-0001-0001-000000000001"),0).andExpect(status().isNotFound());
        assertThat(count("financial_bank_transfers")).isEqualTo(1);
    }
    ResultActions create(UUID key,String body)throws Exception {return mvc.perform(post(route()).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body));}
    ResultActions remove(UUID key,UUID id,long version)throws Exception {return mvc.perform(delete(route()+"/"+id).param("expectedVersion",Long.toString(version)).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",key));}
    String body() {return "{\"values\":{\"originBankAccountId\":\""+origin+"\",\"destinationBankAccountId\":\""+destination+"\",\"transferDate\":\"2026-07-01\",\"amount\":\"1.23\",\"description\":\"Synthetic HTTP transfer\"}}";}
    String route() {return "/api/companies/"+owner.getId()+"/bank-transfers";}
    void empty() {for(var table:List.of("financial_bank_transfers","financial_bank_transfer_events","financial_bank_transfer_command_receipts"))assertThat(count(table)).isZero();}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company,String label) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Transfer HTTP "+label,BankAccountType.CASH,new BigDecimal("5.00"),LocalDate.of(2026,1,1),true)).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Transfer HTTP Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Transfer HTTP Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Transfer HTTP Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Transfer HTTP "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,role,scope,permission));}
}
